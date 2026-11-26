package tienda;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tienda.articulos.Laptop;
import tienda.excepciones.CorreoDuplicadoException;
import tienda.excepciones.StockInsuficienteException;
import tienda.pagos.PagoTarjeta;
import tienda.persistencia.RepositorioJson;
import tienda.servicio.Tienda;
import tienda.usuarios.Invitado;
import tienda.usuarios.UsuarioRegistrado;

/**
 * Casos simultaneos: varios usuarios (hilos) operando al mismo tiempo sobre la misma tienda y los
 * mismos archivos. Todos los hilos arrancan a la vez con un CountDownLatch para forzar la carrera.
 */
class ConcurrenciaTest {

    @TempDir
    Path carpeta;

    private static <T> List<Future<T>> enParalelo(int hilos, java.util.function.IntFunction<Callable<T>> tarea) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<T>> res = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            Callable<T> c = tarea.apply(i);
            res.add(pool.submit(() -> { salida.await(); return c.call(); }));
        }
        salida.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS), "los hilos no terminaron (posible bloqueo)");
        return res;
    }

    private static void jsonValidos(Path carpeta) throws Exception {
        for (String f : new String[]{"catalogo.json", "usuarios.json", "pedidos.json"})
            assertDoesNotThrow(() -> JsonParser.parseString(Files.readString(carpeta.resolve(f))).getAsJsonObject(), f);
    }

    @Test @DisplayName("20 compradores pagan a la vez la misma laptop con stock 5: se venden exactamente 5")
    void sobreventaImposible() throws Exception {
        Tienda t = new Tienda(carpeta);
        t.getAdministrador().agregarProducto(new Laptop(t.reservarIdArticulo(), "Ultimas 5", 1000, 5, null, 12, 1.3, 9));
        List<UsuarioRegistrado> compradores = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UsuarioRegistrado u = UsuarioRegistrado.desdeHash(100 + i, "c" + i + "@correo.com", "C" + i, "x");
            compradores.add(u);
        }
        // se agregan via repositorio para no gastar 20 hashes bcrypt
        new RepositorioJson(carpeta).guardarUsuarios(compradores);
        Tienda t2 = new Tienda(carpeta);
        List<UsuarioRegistrado> us = t2.getUsuarios();
        for (UsuarioRegistrado u : us) t2.agregarAlCarrito(u, 1); // todos lo tienen en el carrito (stock 5 >= 1)
        AtomicInteger exitos = new AtomicInteger(), rechazos = new AtomicInteger();
        enParalelo(20, i -> () -> {
            try {
                t2.pagar(us.get(i), new PagoTarjeta("4111111111111111", YearMonth.now().plusYears(1)));
                exitos.incrementAndGet();
            } catch (StockInsuficienteException e) {
                rechazos.incrementAndGet();
            }
            return null;
        }).forEach(f -> assertDoesNotThrow(() -> f.get()));
        assertEquals(5, exitos.get());
        assertEquals(15, rechazos.get());
        assertEquals(0, t2.buscarArticulo(1).getStock());
        assertEquals(5, t2.getPedidos().size());
        Tienda reabierta = new Tienda(carpeta);
        assertEquals(0, reabierta.buscarArticulo(1).getStock(), "catalogo.json consistente");
        assertEquals(5, reabierta.getPedidos().size(), "pedidos.json consistente");
        jsonValidos(carpeta);
    }

    @Test @DisplayName("8 hilos registran el MISMO correo a la vez: solo se crea una cuenta")
    void mismoCorreoSimultaneo() throws Exception {
        Tienda t = new Tienda(carpeta);
        AtomicInteger duplicados = new AtomicInteger();
        enParalelo(8, i -> () -> {
            try { t.registrar("mismo@correo.com", "secreta" + i, "U" + i); }
            catch (CorreoDuplicadoException e) { duplicados.incrementAndGet(); }
            return null;
        }).forEach(f -> assertDoesNotThrow(() -> f.get()));
        assertEquals(7, duplicados.get());
        assertEquals(1, t.getUsuarios().size());
        assertEquals(1, new RepositorioJson(carpeta).cargarUsuarios().size());
    }

    @Test @DisplayName("8 registros distintos simultaneos: todos quedan en usuarios.json con ids unicos")
    void registrosDistintos() throws Exception {
        Tienda t = new Tienda(carpeta);
        List<Future<UsuarioRegistrado>> r = enParalelo(8, i -> () -> t.registrar("u" + i + "@correo.com", "secreta", "U" + i));
        List<Integer> ids = new ArrayList<>();
        for (Future<UsuarioRegistrado> f : r) ids.add(f.get().getId());
        assertEquals(8, ids.stream().distinct().count());
        List<UsuarioRegistrado> enDisco = new RepositorioJson(carpeta).cargarUsuarios();
        assertEquals(8, enDisco.size());
        jsonValidos(carpeta);
    }

    @Test @DisplayName("Muchos invitados agregan al carrito la misma pieza mientras el admin la edita y luego la borra")
    void adminYClientesALaVez() throws Exception {
        Tienda t = new Tienda(carpeta);
        for (int i = 0; i < 10; i++)
            t.getAdministrador().agregarProducto(new Laptop(t.reservarIdArticulo(), "L" + i, 100 + i, 50, null, 12, 1.2, 8));
        List<Invitado> invitados = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> inesperados = Collections.synchronizedList(new ArrayList<>());
        enParalelo(16, i -> () -> {
            try {
                if (i == 0) {
                    for (int k = 1; k <= 10; k++) t.getAdministrador().editarProducto(new Laptop(k, "Editada " + k, 200, 3, null, 12, 1.2, 8));
                    for (int k = 1; k <= 5; k++) t.getAdministrador().eliminarProducto(k);
                } else {
                    Invitado inv = t.entrarComoInvitado();
                    invitados.add(inv);
                    for (int k = 0; k < 40; k++) {
                        try { t.agregarAlCarrito(inv, 1 + (k % 10)); }
                        catch (StockInsuficienteException | IllegalArgumentException | IllegalStateException esperado) { }
                    }
                }
            } catch (Throwable e) { inesperados.add(e); }
            return null;
        });
        assertTrue(inesperados.isEmpty(), inesperados.toString());
        assertEquals(5, t.getCatalogo().size());
        for (Invitado inv : invitados) assertTrue(inv.getCarrito().getCantidad() <= 15);
        jsonValidos(carpeta);
    }

    @Test @DisplayName("Un mismo usuario con dos ventanas abiertas agrega a la vez: nunca supera stock ni 15")
    void mismoUsuarioDosVentanas() throws Exception {
        Tienda t = new Tienda(carpeta);
        t.getAdministrador().agregarProducto(new Laptop(t.reservarIdArticulo(), "Stock 7", 100, 7, null, 12, 1.2, 8));
        t.getAdministrador().agregarProducto(new Laptop(t.reservarIdArticulo(), "Stock 50", 100, 50, null, 12, 1.2, 8));
        UsuarioRegistrado u = t.registrar("dos@correo.com", "secreta", "Dos");
        enParalelo(10, i -> () -> {
            for (int k = 0; k < 5; k++) {
                try { t.agregarAlCarrito(u, i % 2 == 0 ? 1 : 2); } catch (Exception esperado) { }
            }
            return null;
        });
        assertEquals(15, u.getCarrito().getCantidad());
        assertTrue(u.getCarrito().contar(1) <= 7);
        assertEquals(15, new RepositorioJson(carpeta).cargarUsuarios().get(0).getCarrito().getCantidad(),
            "lo guardado coincide con la memoria");
    }

    @Test @DisplayName("Dos instancias de la app (dos Tienda) escriben a la vez: los JSON nunca quedan corruptos")
    void dosInstanciasMismaCarpeta() throws Exception {
        Tienda a = new Tienda(carpeta);
        Tienda b = new Tienda(carpeta);
        enParalelo(2, i -> () -> {
            Tienda t = i == 0 ? a : b;
            for (int k = 0; k < 40; k++)
                t.getAdministrador().agregarProducto(new Laptop(1000 * (i + 1) + k, "P" + i + "-" + k, 10, 1, null, 1, 1, 1));
            return null;
        }).forEach(f -> assertDoesNotThrow(() -> f.get()));
        jsonValidos(carpeta);
        int enDisco = new RepositorioJson(carpeta).cargarCatalogo().size();
        assertEquals(40, enDisco, "gana la ultima escritura completa (limitacion documentada), pero nunca hay JSON roto");
    }
}
