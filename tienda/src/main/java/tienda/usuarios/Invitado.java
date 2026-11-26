package tienda.usuarios;

import tienda.articulos.Articulo;

/** Invitado: su carrito vive solo en memoria y se borra al cerrar sesion (RN-05). Nunca se guarda en disco. */
public class Invitado extends Usuario {
    private final Carrito carrito = new Carrito();

    public Invitado() {
        super("invitado@tienda.local", "Invitado", null, true); // sin contrasena: no puede autenticarse
    }

    public void agregarAlCarrito(Articulo articulo) {
        carrito.agregar(articulo);
    }

    public Carrito getCarrito() { return carrito; }

    public void cerrarSesion() {
        carrito.vaciar();
    }
}
