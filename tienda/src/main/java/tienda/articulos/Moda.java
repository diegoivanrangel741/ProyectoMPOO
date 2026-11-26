package tienda.articulos;

/** Moda: sin IVA extra; 15% de descuento cuando el producto esta en temporada. */
public abstract class Moda extends Articulo {
    public static final double DESCUENTO_TEMPORADA = 0.15;

    private Talla talla;
    private String color;
    private boolean enTemporada;

    public Moda(int id, String nombre, double precio, int stock, String imagen, Talla talla, String color,
                boolean enTemporada) {
        super(id, nombre, precio, stock, imagen);
        setTalla(talla);
        setColor(color);
        this.enTemporada = enTemporada;
    }

    public void setTalla(Talla talla) {
        if (talla == null) throw new IllegalArgumentException("Se necesita una talla valida (XS a XL)");
        this.talla = talla;
    }

    public void setColor(String color) {
        if (color == null || color.isBlank()) throw new IllegalArgumentException("Se necesita un color");
        this.color = color.trim();
    }

    public void setEnTemporada(boolean enTemporada) { this.enTemporada = enTemporada; }

    @Override
    public double getPrecioFinal(int unidades) {
        validarUnidades(unidades);
        double subtotal = unidades * getPrecio();
        return redondear(enTemporada ? subtotal * (1 - DESCUENTO_TEMPORADA) : subtotal);
    }

    @Override
    public String getCategoria() { return "Moda"; }

    public Talla getTalla() { return talla; }
    public String getColor() { return color; }
    public boolean isEnTemporada() { return enTemporada; }
}
