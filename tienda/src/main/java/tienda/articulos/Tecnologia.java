package tienda.articulos;

/** Tecnologia: se cobra IVA del 16% sobre el precio. */
public abstract class Tecnologia extends Articulo {
    public static final double IVA = 0.16;
    private int garantiaMeses;

    public Tecnologia(int id, String nombre, double precio, int stock, String imagen, int garantiaMeses) {
        super(id, nombre, precio, stock, imagen);
        setGarantiaMeses(garantiaMeses);
    }

    public void setGarantiaMeses(int garantiaMeses) {
        if (garantiaMeses < 0) throw new IllegalArgumentException("La garantia no puede ser menor a 0 meses");
        this.garantiaMeses = garantiaMeses;
    }

    @Override
    public double getPrecioFinal(int unidades) {
        validarUnidades(unidades);
        return redondear(unidades * getPrecio() * (1 + IVA));
    }

    @Override
    public String getCategoria() { return "Tecnologia"; }

    public int getGarantiaMeses() { return garantiaMeses; }
}
