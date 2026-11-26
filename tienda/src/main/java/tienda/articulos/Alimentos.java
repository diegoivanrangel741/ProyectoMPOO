package tienda.articulos;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Alimentos: 10% de descuento si faltan 3 dias o menos para caducar. Un alimento caducado no se vende. */
public abstract class Alimentos extends Articulo {
    public static final int DIAS_DESCUENTO = 3;
    public static final double DESCUENTO_CADUCIDAD = 0.10;

    private LocalDate fechaCaducidad;
    private final boolean perecedero;

    public Alimentos(int id, String nombre, double precio, int stock, String imagen,
                     LocalDate fechaCaducidad, boolean perecedero) {
        super(id, nombre, precio, stock, imagen);
        setFechaCaducidad(fechaCaducidad);
        this.perecedero = perecedero;
    }

    public void setFechaCaducidad(LocalDate fechaCaducidad) {
        if (fechaCaducidad == null) throw new IllegalArgumentException("Se necesita una fecha de caducidad");
        this.fechaCaducidad = fechaCaducidad;
    }

    public long diasParaCaducar() {
        return ChronoUnit.DAYS.between(LocalDate.now(), fechaCaducidad);
    }

    public boolean estaCaducado() {
        return diasParaCaducar() < 0;
    }

    public boolean tieneDescuento() {
        long dias = diasParaCaducar();
        return dias >= 0 && dias <= DIAS_DESCUENTO;
    }

    @Override
    public double getPrecioFinal(int unidades) {
        validarUnidades(unidades);
        double subtotal = getPrecio() * unidades;
        return redondear(tieneDescuento() ? subtotal * (1 - DESCUENTO_CADUCIDAD) : subtotal);
    }

    @Override
    public boolean estaDisponible() {
        return super.estaDisponible() && !estaCaducado();
    }

    @Override
    public String getCategoria() { return "Alimentos"; }

    public LocalDate getFechaCaducidad() { return fechaCaducidad; }
    public boolean isPerecedero() { return perecedero; }
}
