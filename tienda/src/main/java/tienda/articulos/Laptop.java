package tienda.articulos;

public class Laptop extends Tecnologia {
    private double pesoKg;
    private int bateriaHoras;

    public Laptop(int id, String nombre, double precio, int stock, String imagen, int garantiaMeses,
                  double pesoKg, int bateriaHoras) {
        super(id, nombre, precio, stock, imagen, garantiaMeses);
        setPesoKg(pesoKg);
        setBateriaHoras(bateriaHoras);
    }

    public void setPesoKg(double pesoKg) {
        if (Double.isNaN(pesoKg) || pesoKg <= 0) throw new IllegalArgumentException("El peso debe ser mayor a 0 kg");
        this.pesoKg = pesoKg;
    }

    public void setBateriaHoras(int bateriaHoras) {
        if (bateriaHoras < 0) throw new IllegalArgumentException("La bateria no puede ser menor a 0 horas");
        this.bateriaHoras = bateriaHoras;
    }

    public double getPesoKg() { return pesoKg; }
    public int getBateriaHoras() { return bateriaHoras; }
}
