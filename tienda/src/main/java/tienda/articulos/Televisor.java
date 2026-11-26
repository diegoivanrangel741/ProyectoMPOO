package tienda.articulos;

/** Subcategoria "Television" del enunciado. */
public class Televisor extends Tecnologia {
    private int pulgadas;
    private String resolucion;

    public Televisor(int id, String nombre, double precio, int stock, String imagen, int garantiaMeses,
                     int pulgadas, String resolucion) {
        super(id, nombre, precio, stock, imagen, garantiaMeses);
        setPulgadas(pulgadas);
        setResolucion(resolucion);
    }

    public void setPulgadas(int pulgadas) {
        if (pulgadas <= 0) throw new IllegalArgumentException("Las pulgadas deben ser mayores a 0");
        this.pulgadas = pulgadas;
    }

    public void setResolucion(String resolucion) {
        if (resolucion == null || resolucion.isBlank()) throw new IllegalArgumentException("Debe tener una resolucion");
        this.resolucion = resolucion.trim();
    }

    public int getPulgadas() { return pulgadas; }
    public String getResolucion() { return resolucion; }
}
