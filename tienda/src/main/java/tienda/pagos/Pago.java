package tienda.pagos;

import tienda.excepciones.PagoRechazadoException;
import tienda.excepciones.TarjetaInvalidaException;

/** Metodo de pago simulado. Devuelve true si el cobro se autoriza. */
public interface Pago {
    boolean procesar(double monto) throws TarjetaInvalidaException, PagoRechazadoException;

    /** Nombre corto del metodo para el historial ("Tarjeta VISA", "Transferencia", "Wallet"). */
    String getDescripcion();
}
