package tienda.articulos;

import java.time.LocalDate;

public class Abarrotes extends Alimentos {
    public Abarrotes(int id, String nombre, double precio, int stock, String imagen, LocalDate fechaCaducidad) {
        super(id, nombre, precio, stock, imagen, fechaCaducidad, false);
    }
}
