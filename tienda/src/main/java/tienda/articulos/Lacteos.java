package tienda.articulos;

import java.time.LocalDate;

public class Lacteos extends Alimentos {
    public Lacteos(int id, String nombre, double precio, int stock, String imagen, LocalDate fechaCaducidad) {
        super(id, nombre, precio, stock, imagen, fechaCaducidad, true);
    }
}
