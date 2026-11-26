package tienda.articulos;

import tienda.excepciones.StockInsuficienteException;

/**
 * Producto vendible. Clase abstracta de la que derivan Tecnologia, Alimentos y Moda.
 * El precio final de cada categoria se calcula de forma polimorfica con getPrecioFinal().
 */
public abstract class Articulo {
    /** Imagen generica: archivo fijo incluido en la carpeta imagenes/ del proyecto. */
    public static final String IMAGEN_GENERICA = "imagenes/generica.png";

    private final int id;
    private String nombre;
    private double precio;
    private int stock;
    private String imagen;

    public Articulo(int id, String nombre, double precio, int stock, String imagen) {
        if (id < 1) throw new IllegalArgumentException("El id del articulo debe ser mayor a 0");
        this.id = id;
        setNombre(nombre);
        setPrecio(precio);
        setStock(stock);
        setImagen(imagen);
    }

    public void setNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Se necesita un nombre");
        this.nombre = nombre.trim();
    }

    public void setPrecio(double precio) {
        if (Double.isNaN(precio) || Double.isInfinite(precio) || precio <= 0)
            throw new IllegalArgumentException("El precio debe ser mayor a 0");
        this.precio = precio;
    }

    public synchronized void setStock(int stock) {
        if (stock < 0) throw new IllegalArgumentException("El stock no puede ser menor a 0");
        this.stock = stock;
    }

    /** Si no se da imagen se usa la generica (CA-08). */
    public void setImagen(String imagen) {
        this.imagen = (imagen == null || imagen.isBlank()) ? IMAGEN_GENERICA : imagen.trim().replace('\\', '/');
    }

    /** Precio final de UNA unidad (IVA o descuentos segun la categoria). */
    public double getPrecioFinal() {
        return getPrecioFinal(1);
    }

    /** Precio final de varias unidades. */
    public abstract double getPrecioFinal(int unidades);

    /** Categoria raiz: Tecnologia, Alimentos o Moda. */
    public abstract String getCategoria();

    /** Subcategoria concreta (Laptop, Lacteos, Hombre...). */
    public String getTipo() {
        return getClass().getSimpleName();
    }

    /** Un articulo esta disponible para venta si tiene stock (las subclases agregan reglas). */
    public boolean estaDisponible() {
        return getStock() > 0;
    }

    /** RN-02 / RF-10: descuenta inventario de forma atomica; no permite quedar en negativo. */
    public synchronized void descontarStock(int unidades) throws StockInsuficienteException {
        if (unidades < 1) throw new IllegalArgumentException("No puedes comprar 0 unidades o unidades negativas");
        if (unidades > stock) {
            throw new StockInsuficienteException("No hay suficiente stock de \"" + nombre + "\" (disponibles: "
                    + stock + ", solicitados: " + unidades + ")");
        }
        this.stock -= unidades;
    }

    protected static void validarUnidades(int unidades) {
        if (unidades < 1) throw new IllegalArgumentException("No puedes comprar 0 unidades o unidades negativas");
    }

    protected static double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public double getPrecio() { return precio; }
    public synchronized int getStock() { return stock; }
    public String getImagen() { return imagen; }

    @Override
    public String toString() {
        return nombre + " (" + getTipo() + ")";
    }
}
