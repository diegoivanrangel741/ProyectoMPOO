package tienda.articulos;

public class Celular extends Tecnologia {
    private int almacenamientoGb;
    private String sistemaOperativo;

    public Celular(int id, String nombre, double precio, int stock, String imagen, int garantiaMeses,
                   int almacenamientoGb, String sistemaOperativo) {
        super(id, nombre, precio, stock, imagen, garantiaMeses);
        setAlmacenamientoGb(almacenamientoGb);
        setSistemaOperativo(sistemaOperativo);
    }

    public void setAlmacenamientoGb(int almacenamientoGb) {
        if (almacenamientoGb <= 0) throw new IllegalArgumentException("El almacenamiento debe ser mayor a 0 GB");
        this.almacenamientoGb = almacenamientoGb;
    }

    public void setSistemaOperativo(String sistemaOperativo) {
        if (sistemaOperativo == null || sistemaOperativo.isBlank())
            throw new IllegalArgumentException("Se necesita el sistema operativo");
        this.sistemaOperativo = sistemaOperativo.trim();
    }

    public int getAlmacenamientoGb() { return almacenamientoGb; }
    public String getSistemaOperativo() { return sistemaOperativo; }
}
