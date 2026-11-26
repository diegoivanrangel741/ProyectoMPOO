package tienda.articulos;

public class Mujer extends Moda {
    public Mujer(int id, String nombre, double precio, int stock, String imagen, Talla talla, String color,
              boolean enTemporada) {
        super(id, nombre, precio, stock, imagen, talla, color, enTemporada);
    }

    public Mujer(int id, String nombre, double precio, int stock, String imagen, Talla talla, String color) {
        this(id, nombre, precio, stock, imagen, talla, color, false);
    }

    /** Acepta la talla como texto (XS a XL), como en el diagrama de clases. */
    public Mujer(int id, String nombre, double precio, int stock, String imagen, String talla, String color) {
        this(id, nombre, precio, stock, imagen, Talla.desdeTexto(talla), color, false);
    }
}
