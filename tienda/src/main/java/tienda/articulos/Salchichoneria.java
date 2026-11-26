package tienda.articulos;

import java.time.LocalDate;

public class Salchichoneria extends Alimentos {
    public Salchichoneria(int id, String nombre, double precio, int stock, String imagen, LocalDate fechaCaducidad) {
        super(id, nombre, precio, stock, imagen, fechaCaducidad, true);
    }
}
