package tienda;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tienda.articulos.*;
import tienda.pagos.PagoTarjeta;
import tienda.persistencia.RepositorioJson;
import tienda.servicio.Tienda;
import tienda.usuarios.*;

/** Seccion 12 de la solicitud: archivos externos, inicio limpio, borrado y dano de archivos, imagenes. */
class PersistenciaTest {

    @TempDir
    Path carpeta;

    private JsonObject json(String archivo) throws Exception {
        return JsonParser.parseString(Files.readString(carpeta.resolve(archivo))).getAsJsonObject();
    }

    private Tienda tiendaConVenta() throws Exception {
        Tienda t = new Tienda(carpeta);
        t.getAdministrador().agregarProducto(new Laptop(t.reservarIdArticulo(), "Laptop 14 pulgadas", 15999.0, 5, null, 12, 1.4, 10));
        UsuarioRegistrado u = t.registrar("ana@correo.com", "secreta", "Ana");
        t.agregarAlCarrito(u, 1);
        t.pagar(u, new PagoTarjeta("4111111111111111", YearMonth.now().plusYears(1)));
        t.agregarAlCarrito(u, 1);
        return t;
    }

    @Test @DisplayName("Inicio limpio: se crean los tres JSON vacios y la carpeta imagenes/")
    void inicioLimpio() throws Exception {
        Tienda t = new Tienda(carpeta);
        assertTrue(t.getCatalogo().isEmpty());
        assertTrue(t.getUsuarios().isEmpty());
        assertTrue(t.getPedidos().isEmpty());
        assertEquals(0, json("catalogo.json").getAsJsonArray("articulos").size());
        assertEquals(0, json("usuarios.json").getAsJsonArray("usuarios").size());
        assertEquals(0, json("pedidos.json").getAsJsonArray("pedidos").size());
        assertTrue(Files.isDirectory(carpeta.resolve("imagenes")));
    }

    @Test @DisplayName("Estructura de los JSON igual a las plantillas de referencia")
    void estructuraDeLosJson() throws Exception {
        tiendaConVenta();
        JsonObject art = json("catalogo.json").getAsJsonArray("articulos").get(0).getAsJsonObject();
        for (String k : new String[]{"id", "tipo", "categoria", "nombre", "precio", "stock", "imagen", "garantiaMeses", "pesoKg", "bateriaHoras"})
            assertTrue(art.has(k), k);
        assertEquals("Laptop", art.get("tipo").getAsString());
        assertEquals("Tecnologia", art.get("categoria").getAsString());
        assertEquals(4, art.get("stock").getAsInt());
        JsonObject usr = json("usuarios.json").getAsJsonArray("usuarios").get(0).getAsJsonObject();
        for (String k : new String[]{"id", "tipo", "correo", "contrasenaHash", "carrito"}) assertTrue(usr.has(k), k);
        JsonObject linea = usr.getAsJsonArray("carrito").get(0).getAsJsonObject();
        assertEquals(1, linea.get("articuloId").getAsInt());
        assertEquals(1, linea.get("cantidad").getAsInt());
        JsonObject ped = json("pedidos.json").getAsJsonArray("pedidos").get(0).getAsJsonObject();
        for (String k : new String[]{"id", "usuarioId", "fecha", "total", "lineas"}) assertTrue(ped.has(k), k);
        JsonObject lp = ped.getAsJsonArray("lineas").get(0).getAsJsonObject();
        assertEquals("Laptop 14 pulgadas", lp.get("nombre").getAsString());
        assertEquals(18558.84, lp.get("precioUnitario").getAsDouble(), 0.001);
    }

    @Test @DisplayName("Todos los tipos de articulo sobreviven ida y vuelta al JSON")
    void idaYVueltaTodosLosTipos() {
        LocalDate f = LocalDate.of(2030, 1, 15);
        RepositorioJson repo = new RepositorioJson(carpeta);
        repo.guardarCatalogo(java.util.List.of(
            new Computadora(1, "PC", 100, 1, null, 12, "Ryzen 5", 16), new Laptop(2, "L", 100, 1, null, 12, 1.2, 8),
            new Celular(3, "C", 100, 1, null, 6, 256, "iOS"), new Televisor(4, "T", 100, 1, null, 12, 55, "4K"),
            new Abarrotes(5, "A", 100, 1, null, f), new Lacteos(6, "L", 100, 1, null, f),
            new Salchichoneria(7, "S", 100, 1, null, f), new Frutas(8, "F", 100, 1, null, f),
            new Verduras(9, "V", 100, 1, null, f), new Hombre(10, "H", 100, 1, null, Talla.XL, "Rojo", true),
            new Mujer(11, "M", 100, 1, null, Talla.XS, "Negro", false)));
        var cargado = new RepositorioJson(carpeta).cargarCatalogo();
        assertEquals(11, cargado.size());
        assertEquals("Ryzen 5", ((Computadora) cargado.get(0)).getProcesador());
        assertEquals(256, ((Celular) cargado.get(2)).getAlmacenamientoGb());
        assertEquals("4K", ((Televisor) cargado.get(3)).getResolucion());
        assertEquals(f, ((Verduras) cargado.get(8)).getFechaCaducidad());
        assertEquals(Talla.XL, ((Hombre) cargado.get(9)).getTalla());
        assertTrue(((Hombre) cargado.get(9)).isEnTemporada());
    }

    @Test @DisplayName("RF-06 tras un fallo (sin cerrar sesion) el carrito del registrado se restaura")
    void carritoTrasUnFallo() throws Exception {
        tiendaConVenta(); // se "cae" la app: no se llama cerrarSesion ni guardarTodo
        Tienda reabierta = new Tienda(carpeta);
        UsuarioRegistrado ana = (UsuarioRegistrado) reabierta.iniciarSesion("ana@correo.com", "secreta");
        assertEquals(1, ana.getCarrito().getCantidad());
        assertEquals(1, reabierta.getHistorial(ana).size(), "el historial se reconstruye desde pedidos.json");
        assertEquals(4, reabierta.buscarArticulo(1).getStock());
    }

    @Test @DisplayName("RN-05 el carrito del invitado nunca se guarda en disco")
    void invitadoNoSeGuarda() throws Exception {
        Tienda t = new Tienda(carpeta);
        t.getAdministrador().agregarProducto(new Laptop(t.reservarIdArticulo(), "L", 100, 5, null, 12, 1.2, 8));
        String antes = Files.readString(carpeta.resolve("usuarios.json"));
        Invitado inv = t.entrarComoInvitado();
        t.agregarAlCarrito(inv, 1);
        t.cerrarSesion(inv);
        assertEquals(antes, Files.readString(carpeta.resolve("usuarios.json")));
        assertFalse(Files.readString(carpeta.resolve("usuarios.json")).contains("invitado"));
        assertTrue(inv.getCarrito().estaVacio());
    }

    @Test @DisplayName("Borrar cada JSON no provoca error; borrar catalogo.json no afecta pedidos.json")
    void borrarArchivos() throws Exception {
        tiendaConVenta();
        String pedidosAntes = Files.readString(carpeta.resolve("pedidos.json"));
        Files.delete(carpeta.resolve("catalogo.json"));
        Tienda t1 = assertDoesNotThrow(() -> new Tienda(carpeta));
        assertTrue(t1.getCatalogo().isEmpty());
        assertEquals(1, t1.getPedidos().size());
        assertEquals(pedidosAntes, Files.readString(carpeta.resolve("pedidos.json")));
        UsuarioRegistrado ana = (UsuarioRegistrado) t1.iniciarSesion("ana@correo.com", "secreta");
        assertTrue(ana.getCarrito().estaVacio(), "el articulo ya no existe: se omite del carrito");

        Files.delete(carpeta.resolve("usuarios.json"));
        Files.delete(carpeta.resolve("pedidos.json"));
        Tienda t2 = assertDoesNotThrow(() -> new Tienda(carpeta));
        assertTrue(t2.getUsuarios().isEmpty());
        assertTrue(Files.exists(carpeta.resolve("usuarios.json")));
        assertTrue(Files.exists(carpeta.resolve("pedidos.json")));
    }

    @Test @DisplayName("Un JSON danado se respalda y la app continua vacia, sin cerrarse")
    void jsonDanado() throws Exception {
        tiendaConVenta();
        Files.writeString(carpeta.resolve("usuarios.json"), "{ \"usuarios\": [ { \"id\": 1, ");
        Files.writeString(carpeta.resolve("catalogo.json"), "");
        Tienda t = assertDoesNotThrow(() -> new Tienda(carpeta));
        assertTrue(t.getUsuarios().isEmpty());
        assertTrue(t.getCatalogo().isEmpty());
        assertEquals(1, t.getPedidos().size());
        try (Stream<Path> s = Files.list(carpeta)) {
            assertEquals(2, s.filter(p -> p.getFileName().toString().contains(".danado-")).count());
        }
        assertFalse(t.consumirAvisos().isEmpty());
        UsuarioRegistrado nuevo = t.registrar("otra@correo.com", "secreta", "Otra");
        assertTrue(nuevo.getId() > 1, "no reutiliza el id del usuario que aparece en pedidos.json");
    }

    @Test @DisplayName("Un registro invalido dentro del JSON se omite sin perder los demas")
    void registroInvalido() throws Exception {
        Files.writeString(carpeta.resolve("catalogo.json"),
            "{\"articulos\":[{\"id\":1,\"tipo\":\"Laptop\",\"nombre\":\"Buena\",\"precio\":10,\"stock\":1,\"garantiaMeses\":1,\"pesoKg\":1.1,\"bateriaHoras\":3},"
          + "{\"id\":2,\"tipo\":\"Nave\",\"nombre\":\"Rara\",\"precio\":10,\"stock\":1},"
          + "{\"id\":3,\"tipo\":\"Laptop\",\"nombre\":\"Precio negativo\",\"precio\":-1,\"stock\":1,\"garantiaMeses\":1,\"pesoKg\":1,\"bateriaHoras\":3}]}");
        Tienda t = new Tienda(carpeta);
        assertEquals(1, t.getCatalogo().size());
        assertEquals(2, t.consumirAvisos().size());
    }

    @Test @DisplayName("Imagen faltante: se usa la generica; si tampoco existe, null (la UI muestra texto)")
    void imagenFaltante() throws Exception {
        Tienda t = new Tienda(carpeta);
        Laptop l = new Laptop(1, "Laptop", 100, 1, "imagenes/no-existe.png", 12, 1.2, 8);
        assertNull(t.resolverImagen(l), "sin generica -> texto plano");
        Files.write(carpeta.resolve(Articulo.IMAGEN_GENERICA), new byte[]{1});
        assertEquals(carpeta.resolve(Articulo.IMAGEN_GENERICA), t.resolverImagen(l));
    }

    @Test @DisplayName("No quedan archivos temporales despues de guardar")
    void sinTemporales() throws Exception {
        tiendaConVenta();
        try (Stream<Path> s = Files.list(carpeta)) {
            assertEquals(0, s.filter(p -> p.toString().endsWith(".tmp")).count());
        }
    }
}
