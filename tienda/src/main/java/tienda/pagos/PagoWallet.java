package tienda.pagos;

import tienda.excepciones.PagoRechazadoException;

/**
 * RF-09: simula un monedero electronico con saldo; el resultado se informa con un pop-up.
 * El pop-up lo dibuja el Notificador que entrega la interfaz grafica, asi esta clase no depende de Swing.
 */
public class PagoWallet implements Pago {
    private double saldo;
    private final Notificador notificador;

    public PagoWallet(double saldo) {
        this(saldo, Notificador.CONSOLA);
    }

    public PagoWallet(double saldo, Notificador notificador) {
        if (Double.isNaN(saldo) || saldo < 0) throw new IllegalArgumentException("El saldo no puede ser negativo");
        this.saldo = saldo;
        this.notificador = notificador == null ? Notificador.CONSOLA : notificador;
    }

    public synchronized boolean cobrar(double monto) {
        if (monto <= 0 || monto > saldo + 1e-9) {
            notificador.notificar("Wallet", "Saldo insuficiente\nSaldo: $" + String.format("%,.2f", saldo)
                    + "\nTotal: $" + String.format("%,.2f", monto), false);
            return false;
        }
        saldo = Math.round((saldo - monto) * 100.0) / 100.0;
        notificador.notificar("Wallet", "Pago realizado\nSe cobraron: $" + String.format("%,.2f", monto)
                + "\nSaldo restante: $" + String.format("%,.2f", saldo), true);
        return true;
    }

    @Override
    public boolean procesar(double monto) throws PagoRechazadoException {
        if (!cobrar(monto)) throw new PagoRechazadoException("Saldo insuficiente en la wallet");
        return true;
    }

    @Override
    public String getDescripcion() { return "Wallet"; }

    public synchronized double getSaldo() { return saldo; }
}
