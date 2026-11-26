package tienda.usuarios;

/** Copia del producto al momento de la compra (nombre, precio final unitario y cantidad). */
public class LineaPedido {
    private final String nombre;
    private final double precioUnitario;
    private final int cantidad;

    public LineaPedido(String nombre, double precioUnitario, int cantidad) {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("El nombre no puede estar vacio");
        if (precioUnitario <= 0) throw new IllegalArgumentException("El precio debe ser mayor a 0");
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        this.nombre = nombre;
        this.precioUnitario = precioUnitario;
        this.cantidad = cantidad;
    }

    public String getNombre() { return nombre; }
    public double getPrecioUnitario() { return precioUnitario; }
    public int getCantidad() { return cantidad; }
    public double getSubtotal() { return Math.round(precioUnitario * cantidad * 100.0) / 100.0; }
}
