package tienda.excepciones;

/** El metodo de pago no autorizo el cobro (saldo insuficiente, datos incompletos). */
public class PagoRechazadoException extends Exception {
    public PagoRechazadoException(String mensaje) {
        super(mensaje);
    }
}
