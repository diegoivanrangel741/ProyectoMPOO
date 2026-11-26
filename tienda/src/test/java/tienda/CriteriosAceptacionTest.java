package tienda;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tienda.articulos.*;
import tienda.excepciones.*;
import tienda.pagos.PagoTarjeta;
import tienda.persistencia.RepositorioJson;
import tienda.servicio.Tienda;
import tienda.usuarios.*;

/** Pruebas de los criterios de aceptacion CA-01 a CA-08 de la especificacion. */
class CriteriosAceptacionTest {

    @TempDir
    Path carpeta;

    private Laptop laptop(int id, int stock) {
        return new Laptop(id, "Laptop 14 pulgadas", 15999.0, stock, "imagenes/laptop-14.png", 12, 1.4, 10);
    }

    private UsuarioRegistrado ana() {
        return new UsuarioRegistrado(7, "ana@correo.com", "secreta", "Ana");
    }

    @Test
    @DisplayName("CA-01 (RF-01) consultar una categoria muestra sus subcategorias y articulos")
    void ca01_consultarCategoriaMuestraSusArticulos() throws Exception {
        RepositorioJson repo = new RepositorioJson(carpeta);
        List<Articulo> catalogo = List.of(
            laptop(1, 5),
            new Lacteos(2, "Leche", 25.0, 10, "imagenes/leche.png", LocalDate.now().plusDays(10)),
            new Hombre(3, "Playera", 199.0, 4, "imagenes/playera.png", "M", "Azul"));
        repo.guardarCatalogo(catalogo);

        List<Articulo> cargado = new RepositorioJson(carpeta).cargarCatalogo();
        List<Articulo> tecnologia = cargado.stream().filter(a -> a instanceof Tecnologia).collect(Collectors.toList());
        List<Articulo> alimentos = cargado.stream().filter(a -> a instanceof Alimentos).collect(Collectors.toList());
        List<Articulo> moda = cargado.stream().filter(a -> a instanceof Moda).collect(Collectors.toList());
        assertEquals(3, cargado.size());
        assertTrue(tecnologia.get(0) instanceof Laptop);
        assertTrue(alimentos.get(0) instanceof Lacteos);
        assertTrue(moda.get(0) instanceof Hombre);

        Tienda tienda = new Tienda(carpeta);
        Map<String, List<Articulo>> tec = tienda.consultarCategoria("Tecnologia");
        assertEquals(List.of("Computadora", "Laptop", "Celular", "Televisor"), List.copyOf(tec.keySet()));
        assertEquals(1, tec.get("Laptop").size());
        assertEquals(5, tienda.consultarCategoria("Alimentos").size());
        assertEquals(1, tienda.consultarCategoria("Moda").get("Hombre").size());
    }

    @Test
    @DisplayName("CA-02 (RF-06) el carrito del registrado aparece igual al reabrir la app")
    void ca02_carritoDelRegistradoSeConservaAlReabrirLaApp() {
        RepositorioJson repo = new RepositorioJson(carpeta);
        Laptop l = laptop(1, 5);
        repo.guardarCatalogo(List.of(l));
        UsuarioRegistrado ana = ana();
        ana.agregarAlCarrito(l);
        ana.agregarAlCarrito(l);
        repo.guardarUsuarios(List.of(ana));

        RepositorioJson reabierto = new RepositorioJson(carpeta);
        List<UsuarioRegistrado> usuarios = reabierto.cargarUsuarios();
        assertEquals(1, usuarios.size());
        List<Articulo> carrito = usuarios.get(0).getCarrito().getArticulos();
        assertEquals(2, carrito.size());
        assertEquals(1, carrito.get(0).getId());
        assertTrue(usuarios.get(0).autenticar("secreta"));
    }

    @Test
    @DisplayName("CA-03 (RF-06) el carrito del invitado queda vacio al cerrar sesion")
    void ca03_carritoDelInvitadoQuedaVacioAlCerrarSesion() {
        Invitado invitado = new Invitado();
        invitado.getCarrito().agregar(laptop(1, 5));
        assertFalse(invitado.getCarrito().getArticulos().isEmpty());
        invitado.cerrarSesion();
        assertTrue(invitado.getCarrito().getArticulos().isEmpty());
    }

    @Test
    @DisplayName("CA-04 (RF-07) una tarjeta de 14 digitos se rechaza")
    void ca04_tarjetaDe14DigitosSeRechaza() {
        YearMonth vigente = YearMonth.now().plusYears(2);
        TarjetaInvalidaException e = assertThrows(TarjetaInvalidaException.class,
            () -> new PagoTarjeta("12345678901234", vigente).procesar(100.0));
        assertTrue(e.getMessage().contains("15 o 16"));
    }

    @Test
    @DisplayName("CA-05 (RF-10) stock 3, se compran 2, queda 1")
    void ca05_stock3ComprarDosQuedaEnUno() throws StockInsuficienteException {
        Laptop l = laptop(1, 3);
        l.descontarStock(2);
        assertEquals(1, l.getStock());
    }

    @Test
    @DisplayName("CA-05 extra (RN-02) no se puede descontar mas del stock")
    void ca05_extra_noSePuedeDescontarMasDelStock() {
        Laptop l = laptop(1, 3);
        assertThrows(StockInsuficienteException.class, () -> l.descontarStock(4));
        assertEquals(3, l.getStock());
    }

    @Test
    @DisplayName("CA-05 completo: pagar 2 unidades por la tienda deja stock 1 en catalogo.json")
    void ca05_completoPorLaTienda() throws Exception {
        Tienda t = new Tienda(carpeta);
        t.getAdministrador().agregarProducto(laptop(t.reservarIdArticulo(), 3));
        UsuarioRegistrado u = t.registrar("compra@correo.com", "secreta1", "Compra");
        int id = t.getCatalogo().get(0).getId();
        t.agregarAlCarrito(u, id);
        t.agregarAlCarrito(u, id);
        t.pagar(u, new PagoTarjeta("4111111111111111", YearMonth.now().plusYears(1)));
        assertEquals(1, new RepositorioJson(carpeta).cargarCatalogo().get(0).getStock());
    }

    @Test
    @DisplayName("CA-06 (RF-04) baja con contrasena incorrecta no elimina la cuenta")
    void ca06_bajaConContrasenaIncorrectaNoEliminaLaCuenta() throws Exception {
        Tienda t = new Tienda(carpeta);
        UsuarioRegistrado ana = t.registrar("ana@correo.com", "secreta", "Ana");
        assertThrows(ContrasenaIncorrectaException.class, () -> t.eliminarCuenta(ana, "incorrecta"));
        assertTrue(ana.autenticar("secreta"));
        assertEquals(1, new RepositorioJson(carpeta).cargarUsuarios().size());
    }

    @Test
    @DisplayName("CA-07 (RF-13) al eliminar un usuario sus pedidos siguen en el historial")
    void ca07_eliminarUsuarioConservaSusPedidos() {
        RepositorioJson repo = new RepositorioJson(carpeta);
        repo.guardarUsuarios(List.of(ana()));
        Pedido pedido = new Pedido(1, 7, LocalDateTime.of(2026, 11, 5, 16, 20), 15999.0,
            List.of(new LineaPedido("Laptop 14 pulgadas", 15999.0, 1)));
        repo.guardarPedidos(List.of(pedido));

        Tienda tienda = new Tienda(repo);
        Administrador admin = tienda.getAdministrador();
        admin.eliminarUsuario(7);

        RepositorioJson reabierto = new RepositorioJson(carpeta);
        assertTrue(reabierto.cargarUsuarios().isEmpty());
        List<Pedido> pedidos = reabierto.cargarPedidos();
        assertEquals(1, pedidos.size());
        assertEquals(7, pedidos.get(0).getUsuarioId());
        assertEquals("Laptop 14 pulgadas", pedidos.get(0).getLineas().get(0).getNombre());
        assertEquals(1, admin.verPedidos().size());
    }

    @Test
    @DisplayName("CA-08 (RF-12) un producto sin imagen usa la imagen generica")
    void ca08_productoSinImagenUsaLaGenerica() throws Exception {
        Laptop sinImagen = new Laptop(1, "Laptop sin foto", 9999.0, 2, null, 12, 1.5, 8);
        assertEquals(Articulo.IMAGEN_GENERICA, sinImagen.getImagen());
        Tienda t = new Tienda(carpeta);
        t.getAdministrador().agregarProducto(sinImagen);
        assertEquals(Articulo.IMAGEN_GENERICA, new RepositorioJson(carpeta).cargarCatalogo().get(0).getImagen());
    }
}
