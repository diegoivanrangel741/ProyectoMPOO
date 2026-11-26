package tienda.usuarios;

import java.util.List;
import tienda.articulos.Articulo;
import tienda.excepciones.CorreoDuplicadoException;
import tienda.servicio.Tienda;

/**
 * Administrador: cuenta fija admin@todopoderso.com / admin123.
 * Sus operaciones se delegan a la Tienda, que aplica las reglas y guarda los JSON.
 */
public class Administrador extends Usuario {
    public static final String CORREO = "admin@todopoderso.com";
    public static final String CONTRASENA_INICIAL = "admin123";

    private final Tienda tienda;

    public Administrador(String correo, String contrasena, String nombre, Tienda tienda) {
        super(correo, contrasena, nombre);
        if (tienda == null) throw new IllegalArgumentException("El administrador necesita una tienda");
        this.tienda = tienda;
    }

    /** RF-12 / CU-05. Lanza CorreoDuplicadoException si el producto ya existe (mismo nombre y tipo). */
    public void agregarProducto(Articulo articulo) throws CorreoDuplicadoException {
        tienda.agregarProducto(articulo);
    }

    /** Nota 14: el administrador tambien puede editar productos existentes. */
    public void editarProducto(Articulo articuloEditado) throws CorreoDuplicadoException {
        tienda.editarProducto(articuloEditado);
    }

    /** Decision 11: al borrar un producto se quita de los carritos de todos los usuarios. */
    public void eliminarProducto(int id) {
        tienda.eliminarProducto(id);
    }

    /** RF-13 / RN-04: borra datos y carrito del usuario; conserva sus pedidos. */
    public void eliminarUsuario(int id) {
        tienda.eliminarUsuario(id);
    }

    public List<UsuarioRegistrado> verUsuarios() { return tienda.getUsuarios(); }
    public List<Pedido> verPedidos() { return tienda.getPedidos(); }
}
