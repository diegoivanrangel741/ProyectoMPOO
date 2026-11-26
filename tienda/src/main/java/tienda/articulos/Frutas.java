package tienda.articulos;

import java.time.LocalDate;

public class Frutas extends Alimentos {
    public Frutas(int id, String nombre, double precio, int stock, String imagen, LocalDate fechaCaducidad) {
        super(id, nombre, precio, stock, imagen, fechaCaducidad, true);
    }
}
