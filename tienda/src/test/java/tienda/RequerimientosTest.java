package tienda;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tienda.articulos.*;
import tienda.excepciones.*;
import tienda.pagos.*;
import tienda.servicio.Tienda;
import tienda.usuarios.*;

/** Verifica cada requerimiento funcional, regla de negocio y nota adicional de la solicitud. */
class RequerimientosTest {

    @TempDir
    Path carpeta;
    Tienda tienda;
    Administrador admin;

    @BeforeEach
    void iniciar() {
        tienda = new Tienda(carpeta);
        admin = tienda.getAdministrador();
    }

    private int publicar(Articulo a) throws Exception {
        admin.agregarProducto(a);
        return a.getId();
    }

    private Laptop laptop(String nombre, double precio, int stock) {
        return new Laptop(tienda.reservarIdArticulo(), nombre, precio, stock, null, 12, 1.4, 10);
    }

    private YearMonth vigente() { return YearMonth.now().plusYears(2); }

    // ---------------------------------------------------------------- RF-01 / polimorfismo de precios
    @Test @DisplayName("RF-01 las 11 subcategorias existen y heredan de su categoria raiz")
    void rf01_jerarquia() {
        LocalDate f = LocalDate.now().plusDays(30);
        Articulo[] todos = {
            new Computadora(1, "PC", 100, 1, null, 12, "i5", 8), new Laptop(2, "L", 100, 1, null, 12, 1.2, 8),
            new Celular(3, "C", 100, 1, null, 12, 128, "Android"), new Televisor(4, "T", 100, 1, null, 12, 50, "4K"),
            new Abarrotes(5, "A", 100, 1, null, f), new Lacteos(6, "L", 100, 1, null, f),
            new Salchichoneria(7, "S", 100, 1, null, f), new Frutas(8, "F", 100, 1, null, f),
            new Verduras(9, "V", 100, 1, null, f), new Hombre(10, "H", 100, 1, null, Talla.M, "Rojo"),
            new Mujer(11, "M", 100, 1, null, Talla.S, "Negro")};
        int tec = 0, ali = 0, mod = 0;
        for (Articulo a : todos) {
            if (a instanceof Tecnologia) tec++;
            if (a instanceof Alimentos) ali++;
            if (a instanceof Moda) mod++;
        }
        assertEquals(4, tec); assertEquals(5, ali); assertEquals(2, mod);
        assertFalse(((Alimentos) todos[4]).isPerecedero(), "Abarrotes no es perecedero");
        for (int i = 5; i <= 8; i++) assertTrue(((Alimentos) todos[i]).isPerecedero());
    }

    @Test @DisplayName("Precio final polimorfico: IVA 16% tecnologia, -10% alimento por caducar, -15% moda en temporada")
    void preciosPolimorficos() {
        assertEquals(1160.0, new Laptop(1, "L", 1000, 1, null, 12, 1.2, 8).getPrecioFinal(), 0.001);
        assertEquals(90.0, new Lacteos(2, "Leche", 100, 1, null, LocalDate.now().plusDays(3)).getPrecioFinal(), 0.001);
        assertEquals(100.0, new Lacteos(3, "Leche2", 100, 1, null, LocalDate.now().plusDays(4)).getPrecioFinal(), 0.001);
        assertEquals(85.0, new Hombre(4, "Playera", 100, 1, null, Talla.M, "Azul", true).getPrecioFinal(), 0.001);
        assertEquals(100.0, new Mujer(5, "Blusa", 100, 1, null, Talla.M, "Azul", false).getPrecioFinal(), 0.001);
        Carrito c = new Carrito();
        c.agregar(new Laptop(6, "L", 1000, 1, null, 12, 1.2, 8));
        c.agregar(new Hombre(7, "P", 100, 1, null, Talla.M, "Azul", true));
        assertEquals(1245.0, c.calcularTotal(), 0.001);
    }

    @Test @DisplayName("Validaciones de articulo: precio > 0, stock >= 0, talla XS..XL, nombre obligatorio")
    void validacionesArticulo() {
        assertThrows(IllegalArgumentException.class, () -> new Laptop(1, "L", 0, 1, null, 12, 1.2, 8));
        assertThrows(IllegalArgumentException.class, () -> new Laptop(1, "L", -5, 1, null, 12, 1.2, 8));
        assertThrows(IllegalArgumentException.class, () -> new Laptop(1, "L", 10, -1, null, 12, 1.2, 8));
        assertThrows(IllegalArgumentException.class, () -> new Laptop(1, " ", 10, 1, null, 12, 1.2, 8));
        assertThrows(IllegalArgumentException.class, () -> new Hombre(1, "P", 10, 1, null, "XXL", "Rojo"));
        assertThrows(IllegalArgumentException.class, () -> new Frutas(1, "F", 10, 1, null, null));
        assertDoesNotThrow(() -> new Laptop(1, "L", 10, 0, null, 12, 1.2, 8), "stock 0 es valido");
    }

    // ---------------------------------------------------------------- RF-02 / RF-03 / CU-01
    @Test @DisplayName("RF-02 tres tipos de usuario con permisos distintos")
    void rf02_tiposDeUsuario() throws Exception {
        int id = publicar(laptop("Laptop A", 1000, 5));
        Invitado inv = tienda.entrarComoInvitado();
        tienda.agregarAlCarrito(inv, id);
        assertEquals(1, inv.getCarrito().getCantidad());
        assertThrows(IllegalStateException.class, () -> tienda.agregarAlCarrito(admin, id), "admin no compra");
        assertFalse(inv.autenticar("invitado"), "el invitado no tiene contrasena");
    }

    @Test @DisplayName("RF-03 registro con correo unico (sin importar mayusculas) y formato valido")
    void rf03_registro() throws Exception {
        UsuarioRegistrado u = tienda.registrar("Ana@Correo.com", "secreta", "Ana");
        assertEquals("ana@correo.com", u.getCorreo());
        assertThrows(CorreoDuplicadoException.class, () -> tienda.registrar("ANA@correo.com", "otra123", "Ana2"));
        assertThrows(CorreoDuplicadoException.class, () -> tienda.registrar(Administrador.CORREO, "otra123", "X"));
        for (String malo : new String[]{"sinarroba.com", "a@b", "a b@c.com", "", "@correo.com"})
            assertThrows(IllegalArgumentException.class, () -> tienda.registrar(malo, "secreta", "X"), malo);
        assertThrows(IllegalArgumentException.class, () -> tienda.registrar("x@correo.com", "123", "X"), "contrasena corta");
        assertEquals(Usuario.class, u.getClass().getSuperclass());
    }

    @Test @DisplayName("Inicio de sesion: registrado, admin fijo y contrasena incorrecta")
    void inicioDeSesion() throws Exception {
        tienda.registrar("luis@correo.com", "secreta", "Luis");
        assertTrue(tienda.iniciarSesion("luis@correo.com", "secreta") instanceof UsuarioRegistrado);
        assertTrue(tienda.iniciarSesion("admin@todopoderso.com", "admin123") instanceof Administrador);
        assertThrows(ContrasenaIncorrectaException.class, () -> tienda.iniciarSesion("luis@correo.com", "mala"));
        assertThrows(ContrasenaIncorrectaException.class, () -> tienda.iniciarSesion("nadie@correo.com", "secreta"));
    }

    // ---------------------------------------------------------------- RF-04 / RN-03
    @Test @DisplayName("RF-04 baja con contrasena correcta elimina la cuenta pero conserva sus pedidos")
    void rf04_bajaCorrecta() throws Exception {
        int id = publicar(laptop("Laptop B", 1000, 5));
        UsuarioRegistrado u = tienda.registrar("baja@correo.com", "secreta", "Baja");
        tienda.agregarAlCarrito(u, id);
        tienda.pagar(u, new PagoTarjeta("4111111111111111", vigente()));
        tienda.eliminarCuenta(u, "secreta");
        assertTrue(tienda.getUsuarios().isEmpty());
        assertEquals(1, tienda.getPedidos().size());
        assertThrows(ContrasenaIncorrectaException.class, () -> tienda.iniciarSesion("baja@correo.com", "secreta"));
        assertThrows(IllegalStateException.class, () -> tienda.agregarAlCarrito(u, id), "sesion invalidada");
    }

    // ---------------------------------------------------------------- RF-05 / CU-02 / notas
    @Test @DisplayName("RF-05 agregar y quitar; CU-02 sin stock se informa; limite de 15 articulos")
    void rf05_carrito() throws Exception {
        int agotado = publicar(laptop("Agotada", 1000, 0));
        int dos = publicar(laptop("Dos piezas", 1000, 2));
        int muchos = publicar(laptop("Muchas", 10, 100));
        UsuarioRegistrado u = tienda.registrar("carrito@correo.com", "secreta", "C");
        StockInsuficienteException e = assertThrows(StockInsuficienteException.class, () -> tienda.agregarAlCarrito(u, agotado));
        assertTrue(e.getMessage().contains("agotado"));
        tienda.agregarAlCarrito(u, dos);
        tienda.agregarAlCarrito(u, dos);
        assertThrows(StockInsuficienteException.class, () -> tienda.agregarAlCarrito(u, dos), "no mas que el stock");
        tienda.quitarDelCarrito(u, dos, false);
        assertEquals(1, u.getCarrito().getCantidad());
        for (int i = 0; i < 14; i++) tienda.agregarAlCarrito(u, muchos);
        assertEquals(15, u.getCarrito().getCantidad());
        assertThrows(IllegalStateException.class, () -> tienda.agregarAlCarrito(u, muchos), "maximo 15");
        tienda.quitarDelCarrito(u, muchos, true);
        assertEquals(1, u.getCarrito().getCantidad());
    }

    @Test @DisplayName("Alimento caducado no se puede agregar")
    void alimentoCaducado() throws Exception {
        int id = publicar(new Lacteos(tienda.reservarIdArticulo(), "Yogurt viejo", 20, 5, null, LocalDate.now().minusDays(1)));
        Invitado inv = tienda.entrarComoInvitado();
        assertThrows(StockInsuficienteException.class, () -> tienda.agregarAlCarrito(inv, id));
    }

    @Test @DisplayName("Nota 14: al abrir el carrito se detecta un articulo agotado y no se deja pagar")
    void validarDisponibilidadAntesDePagar() throws Exception {
        int id = publicar(laptop("Ultima", 1000, 1));
        UsuarioRegistrado a = tienda.registrar("a@correo.com", "secreta", "A");
        UsuarioRegistrado b = tienda.registrar("b@correo.com", "secreta", "B");
        tienda.agregarAlCarrito(a, id);
        tienda.agregarAlCarrito(b, id);
        tienda.pagar(a, new PagoTarjeta("4111111111111111", vigente()));
        Map<Integer, String> problemas = tienda.validarCarrito(b);
        assertEquals("Agotado", problemas.get(id));
        assertThrows(StockInsuficienteException.class, () -> tienda.pagar(b, new PagoTarjeta("4111111111111111", vigente())));
        assertEquals(0, tienda.buscarArticulo(id).getStock());
        tienda.quitarDelCarrito(b, id, true);
        assertTrue(tienda.validarCarrito(b).isEmpty());
    }

    // ---------------------------------------------------------------- RF-07 / RN-01 / BIN
    @Test @DisplayName("RF-07 tarjeta: 15/16 digitos, fecha valida, marca por BIN o Desconocida")
    void rf07_tarjeta() throws Exception {
        assertEquals(Marca.VISA, new PagoTarjeta("4111 1111 1111 1111", vigente()).getMarca());
        assertEquals(Marca.MASTERCARD, new PagoTarjeta("5555555555554444", vigente()).getMarca());
        assertEquals(Marca.MASTERCARD, new PagoTarjeta("2221000000000009", vigente()).getMarca());
        assertEquals(Marca.AMEX, new PagoTarjeta("378282246310005", vigente()).getMarca());
        assertEquals(Marca.DESCONOCIDA, new PagoTarjeta("9999999999999999", vigente()).getMarca());
        assertThrows(TarjetaInvalidaException.class, () -> new PagoTarjeta("41111111111111111", vigente()), "17 digitos");
        assertThrows(TarjetaInvalidaException.class, () -> new PagoTarjeta("4111abcd11111111", vigente()), "letras");
        assertThrows(TarjetaInvalidaException.class, () -> new PagoTarjeta("", vigente()));
        assertThrows(TarjetaInvalidaException.class, () -> new PagoTarjeta("4111111111111111", YearMonth.now().minusMonths(1)), "vencida");
        assertThrows(TarjetaInvalidaException.class, () -> new PagoTarjeta("4111111111111111", null));
        assertThrows(TarjetaInvalidaException.class, () -> new PagoTarjeta("4111111111111111", YearMonth.now().plusYears(40)));
        assertDoesNotThrow(() -> new PagoTarjeta("4111111111111111", YearMonth.now()), "vence este mes: aun valida");
        assertThrows(TarjetaInvalidaException.class, () -> PagoTarjeta.leerVencimiento("13/30"));
        assertThrows(TarjetaInvalidaException.class, () -> PagoTarjeta.leerVencimiento("1230"));
        assertEquals(YearMonth.of(2030, 12), PagoTarjeta.leerVencimiento("12/30"));
    }

    @Test @DisplayName("RN-01 tarjeta invalida en el flujo de pago: no se descuenta stock ni se crea pedido")
    void rn01_pagoRechazadoNoCambiaNada() throws Exception {
        int id = publicar(laptop("Laptop C", 1000, 3));
        UsuarioRegistrado u = tienda.registrar("rn01@correo.com", "secreta", "R");
        tienda.agregarAlCarrito(u, id);
        assertThrows(TarjetaInvalidaException.class, () -> tienda.pagar(u, new PagoTarjeta("12345678901234", vigente())));
        assertEquals(3, tienda.buscarArticulo(id).getStock());
        assertEquals(1, u.getCarrito().getCantidad());
        assertTrue(tienda.getPedidos().isEmpty());
    }

    // ---------------------------------------------------------------- RF-08 / RF-09
    @Test @DisplayName("RF-08 transferencia muestra datos bancarios; CLABE de 18 digitos")
    void rf08_transferencia() throws Exception {
        PagoTransferencia t = new PagoTransferencia();
        assertTrue(t.mostrarDatos().contains("CLABE: " + PagoTransferencia.CLABE_TIENDA));
        assertTrue(t.mostrarDatos().contains("Banco"));
        assertThrows(IllegalArgumentException.class, () -> new PagoTransferencia("123", "Banco"));
        int id = publicar(laptop("Laptop D", 1000, 3));
        UsuarioRegistrado u = tienda.registrar("trans@correo.com", "secreta", "T");
        tienda.agregarAlCarrito(u, id);
        Pedido p = tienda.pagar(u, t);
        assertTrue(p.getMetodoPago().startsWith("Transferencia"));
    }

    @Test @DisplayName("RF-09 wallet: cobra, descuenta saldo, notifica con pop-up; saldo insuficiente se rechaza")
    void rf09_wallet() throws Exception {
        StringBuilder popups = new StringBuilder();
        Notificador n = (t, m, ok) -> popups.append(ok ? "OK|" : "NO|");
        int id = publicar(laptop("Laptop E", 1000, 3));
        UsuarioRegistrado u = tienda.registrar("wallet@correo.com", "secreta", "W");
        tienda.agregarAlCarrito(u, id);
        tienda.pagar(u, new PagoWallet(u.getSaldoWallet(), n));
        assertEquals(UsuarioRegistrado.SALDO_WALLET_INICIAL - 1160.0, u.getSaldoWallet(), 0.001);
        tienda.agregarAlCarrito(u, id);
        assertThrows(PagoRechazadoException.class, () -> tienda.pagar(u, new PagoWallet(10, n)));
        assertEquals(2, tienda.buscarArticulo(id).getStock(), "rechazado: no se descuenta stock");
        assertEquals("OK|NO|", popups.toString());
    }

    // ---------------------------------------------------------------- RF-10 / RF-11
    @Test @DisplayName("RF-10/RF-11 el pago descuenta stock y registra pedido con copia de nombre, precio final y cantidad")
    void rf10_rf11_pedido() throws Exception {
        int id = publicar(laptop("Laptop F", 1000, 5));
        int ropa = publicar(new Mujer(tienda.reservarIdArticulo(), "Vestido", 500, 4, null, Talla.S, "Rojo", true));
        UsuarioRegistrado u = tienda.registrar("pedido@correo.com", "secreta", "P");
        tienda.agregarAlCarrito(u, id);
        tienda.agregarAlCarrito(u, id);
        tienda.agregarAlCarrito(u, ropa);
        Pedido p = tienda.pagar(u, new PagoTarjeta("5555555555554444", vigente()));
        assertEquals(3, tienda.buscarArticulo(id).getStock());
        assertEquals(3, tienda.buscarArticulo(ropa).getStock());
        assertEquals(2320.0 + 425.0, p.getTotal(), 0.001);
        assertEquals(1160.0, p.getLineas().get(0).getPrecioUnitario(), 0.001);
        assertEquals(2, p.getLineas().get(0).getCantidad());
        assertTrue(u.getCarrito().estaVacio());
        assertEquals(1, tienda.getHistorial(u).size());
        assertThrows(IllegalStateException.class, () -> tienda.pagar(u, new PagoTarjeta("4111111111111111", vigente())), "carrito vacio");
    }

    @Test @DisplayName("Un invitado no puede pagar (CU-03 actor: usuario registrado)")
    void invitadoNoPaga() {
        // pagar() solo acepta UsuarioRegistrado: el compilador lo impide; la UI pide registrarse.
        assertThrows(NoSuchMethodException.class, () -> Tienda.class.getMethod("pagar", Invitado.class, Pago.class));
    }

    // ---------------------------------------------------------------- RF-12 / CU-05 / edicion
    @Test @DisplayName("RF-12 agregar, editar y eliminar productos; duplicado se informa; se quita de carritos")
    void rf12_administracionProductos() throws Exception {
        int id = publicar(laptop("Laptop G", 1000, 5));
        assertThrows(CorreoDuplicadoException.class, () -> admin.agregarProducto(laptop("laptop g", 2000, 1)), "mismo nombre y tipo");
        UsuarioRegistrado u = tienda.registrar("adm@correo.com", "secreta", "A");
        tienda.agregarAlCarrito(u, id);
        admin.editarProducto(new Laptop(id, "Laptop G Pro", 1500, 9, null, 24, 1.3, 12));
        assertEquals("Laptop G Pro", tienda.buscarArticulo(id).getNombre());
        assertEquals("Laptop G Pro", u.getCarrito().getArticulos().get(0).getNombre(), "el carrito ve el cambio");
        admin.eliminarProducto(id);
        assertNull(tienda.buscarArticulo(id));
        assertTrue(u.getCarrito().estaVacio(), "decision: se borra de los carritos");
        assertThrows(IllegalArgumentException.class, () -> admin.eliminarProducto(999));
    }

    @Test @DisplayName("RF-12 la imagen se copia a imagenes/ del proyecto")
    void rf12_copiaImagen() throws Exception {
        Path origen = Files.createTempFile("foto", ".png");
        Files.write(origen, new byte[]{1, 2, 3});
        int id = tienda.reservarIdArticulo();
        String ruta = tienda.importarImagen(origen, id);
        assertTrue(ruta.startsWith("imagenes/"));
        assertTrue(Files.exists(carpeta.resolve(ruta)));
        Files.delete(origen);
    }

    // ---------------------------------------------------------------- RF-13 / RN-04
    @Test @DisplayName("RF-13 el admin elimina usuario: se va su carrito y cuenta; su historial queda")
    void rf13_eliminarUsuario() throws Exception {
        int id = publicar(laptop("Laptop H", 1000, 5));
        UsuarioRegistrado u = tienda.registrar("borrar@correo.com", "secreta", "B");
        tienda.agregarAlCarrito(u, id);
        tienda.pagar(u, new PagoTarjeta("4111111111111111", vigente()));
        tienda.agregarAlCarrito(u, id);
        admin.eliminarUsuario(u.getId());
        assertTrue(admin.verUsuarios().isEmpty());
        assertEquals(1, admin.verPedidos().size());
        assertEquals("borrar@correo.com", admin.verPedidos().get(0).getCorreoUsuario());
        UsuarioRegistrado nuevo = tienda.registrar("nuevo@correo.com", "secreta", "N");
        assertNotEquals(u.getId(), nuevo.getId(), "el id no se reutiliza (no hereda historial ajeno)");
        assertTrue(tienda.getHistorial(nuevo).isEmpty());
    }

    // ---------------------------------------------------------------- RNF-04 / nota admin
    @Test @DisplayName("RNF-04 las contrasenas no se guardan en texto plano")
    void rnf04_hash() throws Exception {
        tienda.registrar("hash@correo.com", "MiClaveSecreta", "H");
        String json = Files.readString(carpeta.resolve("usuarios.json"));
        assertFalse(json.contains("MiClaveSecreta"));
        assertTrue(json.contains("$2a$12$"), "bcrypt con costo 12");
    }

    @Test @DisplayName("Nota 14: administrador inicial fijo")
    void adminInicial() throws Exception {
        assertEquals("admin@todopoderso.com", admin.getCorreo());
        assertTrue(admin.autenticar("admin123"));
    }
}
