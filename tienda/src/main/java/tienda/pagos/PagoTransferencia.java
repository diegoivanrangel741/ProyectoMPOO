package tienda.pagos;

/** RF-08: el sistema muestra los datos bancarios de la tienda para transferir. */
public class PagoTransferencia implements Pago {
    public static final String CLABE_TIENDA = "012180001234567897";
    public static final String BANCO_TIENDA = "BBVA Mexico";

    private final String clabe;
    private final String banco;
    private String referencia = "";

    public PagoTransferencia() {
        this(CLABE_TIENDA, BANCO_TIENDA);
    }

    public PagoTransferencia(String clabe, String banco) {
        if (clabe == null || !clabe.matches("\\d{18}")) throw new IllegalArgumentException("La CLABE debe tener 18 digitos");
        if (banco == null || banco.isBlank()) throw new IllegalArgumentException("Se necesita el nombre del banco");
        this.clabe = clabe;
        this.banco = banco.trim();
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia == null ? "" : referencia;
    }

    public String mostrarDatos() {
        return "Banco: " + banco + "\nCLABE: " + clabe + "\nBeneficiario: Tienda en Linea MPOO"
                + (referencia.isBlank() ? "" : "\nReferencia: " + referencia);
    }

    @Override
    public boolean procesar(double monto) {
        return monto > 0;
    }

    @Override
    public String getDescripcion() { return "Transferencia " + banco; }

    public String getClabe() { return clabe; }
    public String getBanco() { return banco; }
}
