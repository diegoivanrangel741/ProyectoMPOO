package tienda.excepciones;

/** Contrasena incorrecta (baja de cuenta o inicio de sesion). */
public class ContrasenaIncorrectaException extends Exception {
    public ContrasenaIncorrectaException(String mensaje) {
        super(mensaje);
    }
}
