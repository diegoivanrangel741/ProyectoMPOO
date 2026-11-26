package tienda.articulos;

public class Computadora extends Tecnologia {
    private String procesador;
    private int ramGb;

    public Computadora(int id, String nombre, double precio, int stock, String imagen, int garantiaMeses,
                       String procesador, int ramGb) {
        super(id, nombre, precio, stock, imagen, garantiaMeses);
        setProcesador(procesador);
        setRamGb(ramGb);
    }

    public void setProcesador(String procesador) {
        if (procesador == null || procesador.isBlank()) throw new IllegalArgumentException("Se necesita un procesador");
        this.procesador = procesador.trim();
    }

    public void setRamGb(int ramGb) {
        if (ramGb <= 0) throw new IllegalArgumentException("La memoria RAM debe ser mayor a 0 GB");
        this.ramGb = ramGb;
    }

    public String getProcesador() { return procesador; }
    public int getRamGb() { return ramGb; }
}
