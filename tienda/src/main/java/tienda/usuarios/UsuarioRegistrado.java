package tienda.usuarios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import tienda.articulos.Articulo;
import tienda.excepciones.ContrasenaIncorrectaException;

/** Usuario con cuenta: su carrito y su saldo de wallet se guardan en usuarios.json. */
public class UsuarioRegistrado extends Usuario {
    public static final double SALDO_WALLET_INICIAL = 20000.0;

    private final int id;
    private final Carrito carrito = new Carrito();
    private final List<Pedido> historial = new ArrayList<>();
    private double saldoWallet = SALDO_WALLET_INICIAL;

    public UsuarioRegistrado(int id, String correo, String contrasena, String nombre) {
        super(correo, contrasena, nombre);
        if (id < 1) throw new IllegalArgumentException("El id del usuario debe ser mayor a 0");
        this.id = id;
    }

    /** Usado por la persistencia: la contrasena ya viene cifrada. */
    public static UsuarioRegistrado desdeHash(int id, String correo, String nombre, String hash) {
        return new UsuarioRegistrado(id, correo, nombre, hash, true);
    }

    private UsuarioRegistrado(int id, String correo, String nombre, String hash, boolean desdeHash) {
        super(correo, nombre, hash, true);
        if (id < 1) throw new IllegalArgumentException("El id del usuario debe ser mayor a 0");
        this.id = id;
    }

    public void agregarAlCarrito(Articulo articulo) {
        carrito.agregar(articulo);
    }

    /**
     * RF-04 / RN-03: confirma con la contrasena. Si es incorrecta lanza la excepcion y no cambia nada.
     * Si es correcta vacia el carrito; el historial NO se borra porque pedidos.json lo conserva (RN-04).
     * La baja definitiva (quitarlo de usuarios.json) la hace la Tienda.
     */
    public boolean eliminarCuenta(String contrasena) throws ContrasenaIncorrectaException {
        if (!autenticar(contrasena))
            throw new ContrasenaIncorrectaException("La contrasena no coincide; no se elimino la cuenta");
        carrito.vaciar();
        return true;
    }

    public void agregarPedido(Pedido pedido) {
        if (pedido == null) throw new IllegalArgumentException("El pedido no puede ser nulo");
        historial.add(pedido);
    }

    public int getId() { return id; }
    public Carrito getCarrito() { return carrito; }
    public List<Pedido> getHistorial() { return Collections.unmodifiableList(historial); }
    public List<Pedido> getPedidos() { return getHistorial(); }
    public double getSaldoWallet() { return saldoWallet; }

    public void setSaldoWallet(double saldoWallet) {
        if (Double.isNaN(saldoWallet) || saldoWallet < 0) throw new IllegalArgumentException("Saldo invalido");
        this.saldoWallet = saldoWallet;
    }
}
