package tienda.pagos;

import java.time.YearMonth;
import tienda.excepciones.TarjetaInvalidaException;

/** RF-07 / RN-01: numero de 15 o 16 digitos, vencimiento valido y marca detectada por BIN. */
public class PagoTarjeta implements Pago {
    public static final int ANIOS_MAXIMOS_VIGENCIA = 20;

    private final String numero;
    private final YearMonth vencimiento;
    private final Marca marca;

    public PagoTarjeta(String numero, YearMonth vencimiento) throws TarjetaInvalidaException {
        if (numero == null || numero.isBlank()) throw new TarjetaInvalidaException("Debes ingresar el numero de la tarjeta");
        String limpio = numero.replace(" ", "").replace("-", "");
        for (int i = 0; i < limpio.length(); i++) {
            if (!Character.isDigit(limpio.charAt(i)))
                throw new TarjetaInvalidaException("La tarjeta solo puede tener numeros");
        }
        if (limpio.length() != 15 && limpio.length() != 16)
            throw new TarjetaInvalidaException("La tarjeta debe tener 15 o 16 digitos (tiene " + limpio.length() + ")");
        if (vencimiento == null) throw new TarjetaInvalidaException("Debes ingresar la fecha de vencimiento");
        YearMonth hoy = YearMonth.now();
        if (vencimiento.isBefore(hoy)) throw new TarjetaInvalidaException("La tarjeta esta vencida (" + vencimiento + ")");
        if (vencimiento.isAfter(hoy.plusYears(ANIOS_MAXIMOS_VIGENCIA)))
            throw new TarjetaInvalidaException("La fecha de vencimiento no es valida (" + vencimiento + ")");
        this.numero = limpio;
        this.vencimiento = vencimiento;
        this.marca = detectarMarca();
    }

    /** Acepta la fecha como texto MM/AA o MM/AAAA. */
    public static YearMonth leerVencimiento(String texto) throws TarjetaInvalidaException {
        if (texto == null || !texto.trim().matches("\\d{1,2}/(\\d{2}|\\d{4})"))
            throw new TarjetaInvalidaException("La fecha debe tener el formato MM/AA");
        String[] p = texto.trim().split("/");
        int mes = Integer.parseInt(p[0]);
        int anio = Integer.parseInt(p[1]);
        if (p[1].length() == 2) anio += 2000;
        if (mes < 1 || mes > 12) throw new TarjetaInvalidaException("El mes debe estar entre 01 y 12");
        return YearMonth.of(anio, mes);
    }

    /** Identificacion por BIN: Visa 4; Mastercard 51-55 y 2221-2720; Amex 34 y 37. */
    public Marca detectarMarca() {
        int dos = Integer.parseInt(numero.substring(0, 2));
        int cuatro = Integer.parseInt(numero.substring(0, 4));
        if (numero.charAt(0) == '4') return Marca.VISA;
        if ((dos >= 51 && dos <= 55) || (cuatro >= 2221 && cuatro <= 2720)) return Marca.MASTERCARD;
        if (dos == 34 || dos == 37) return Marca.AMEX;
        return Marca.DESCONOCIDA;
    }

    @Override
    public boolean procesar(double monto) {
        return monto > 0;
    }

    @Override
    public String getDescripcion() {
        return "Tarjeta " + marca + " terminacion " + numero.substring(numero.length() - 4);
    }

    /** Numero enmascarado: nunca se guarda ni se muestra completo. */
    public String getNumeroEnmascarado() {
        return "**** **** **** " + numero.substring(numero.length() - 4);
    }

    public YearMonth getVencimiento() { return vencimiento; }
    public Marca getMarca() { return marca; }
}
