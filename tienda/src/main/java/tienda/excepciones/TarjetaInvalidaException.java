package tienda.excepciones;

/** Numero de tarjeta o fecha de vencimiento no validos (RN-01). */
public class TarjetaInvalidaException extends Exception {
    public TarjetaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
