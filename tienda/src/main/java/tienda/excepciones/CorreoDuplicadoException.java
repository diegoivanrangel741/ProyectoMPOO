package tienda.excepciones;

/** Correo ya registrado o producto duplicado en el catalogo. */
public class CorreoDuplicadoException extends Exception {
    public CorreoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
