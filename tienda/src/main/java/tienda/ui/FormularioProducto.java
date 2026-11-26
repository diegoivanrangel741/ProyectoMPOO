package tienda.ui;

import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import tienda.articulos.*;
import tienda.excepciones.CorreoDuplicadoException;
import tienda.servicio.Tienda;
import tienda.usuarios.Administrador;

/**
 * Formulario de producto (RF-12 / CU-05). Sirve para agregar y editar.
 * Los campos especificos cambian segun la subcategoria (CardLayout).
 */
class FormularioProducto extends JDialog {
    private static final String[] TIPOS = {"Computadora", "Laptop", "Celular", "Televisor", "Abarrotes", "Lacteos",
            "Salchichoneria", "Frutas", "Verduras", "Hombre", "Mujer"};

    private final Tienda tienda;
    private final Administrador admin;
    private final Articulo original;
    private final JComboBox<String> tipo = new JComboBox<>(TIPOS);
    private final JTextField nombre = new JTextField(22);
    private final JTextField precio = new JTextField(10);
    private final JTextField stock = new JTextField(6);
    private final JTextField garantia = new JTextField("12", 6);
    private final JTextField procesador = new JTextField(14);
    private final JTextField ram = new JTextField(6);
    private final JTextField peso = new JTextField(6);
    private final JTextField bateria = new JTextField(6);
    private final JTextField almacenamiento = new JTextField(6);
    private final JTextField so = new JTextField(12);
    private final JTextField pulgadas = new JTextField(6);
    private final JTextField resolucion = new JTextField(10);
    private final JTextField caducidad = new JTextField(LocalDate.now().plusDays(15).toString(), 10);
    private final JComboBox<Talla> talla = new JComboBox<>(Talla.values());
    private final JTextField color = new JTextField(10);
    private final JCheckBox temporada = new JCheckBox("En temporada (-15%)");
    private final CardLayout tarjetas = new CardLayout();
    private final JPanel especificos = new JPanel(tarjetas);
    private final JPanel vistaImagen = new JPanel(new BorderLayout());
    private final JLabel garantiaEtiqueta = new JLabel("Garantia (meses)");
    private Path imagenElegida = null;
    private boolean quitarImagen = false;

    FormularioProducto(Frame padre, Tienda tienda, Administrador admin, Articulo original) {
        super(padre, original == null ? "Agregar producto" : "Editar producto #" + original.getId(), true);
        this.tienda = tienda;
        this.admin = admin;
        this.original = original;
        setLayout(new BorderLayout());
        add(Estilo.encabezado(original == null ? "Nuevo producto" : "Editar producto",
                original == null ? "Si no cargas imagen se usara la generica" : original.getNombre()), BorderLayout.NORTH);

        JPanel comun = new JPanel(new GridLayout(0, 2, 6, 6));
        comun.add(new JLabel("Subcategoria")); comun.add(tipo);
        comun.add(new JLabel("Nombre")); comun.add(nombre);
        comun.add(new JLabel("Precio base ($)")); comun.add(precio);
        comun.add(new JLabel("Stock")); comun.add(stock);
        comun.add(garantiaEtiqueta); comun.add(garantia);

        especificos.add(fila("Procesador", procesador, "RAM (GB)", ram), "Computadora");
        especificos.add(fila("Peso (kg)", peso, "Bateria (horas)", bateria), "Laptop");
        especificos.add(fila("Almacenamiento (GB)", almacenamiento, "Sistema operativo", so), "Celular");
        especificos.add(fila("Pulgadas", pulgadas, "Resolucion", resolucion), "Televisor");
        especificos.add(fila("Fecha de caducidad (AAAA-MM-DD)", caducidad, null, null), "Alimentos");
        JPanel moda = fila("Talla", talla, "Color", color);
        moda.add(new JLabel()); moda.add(temporada);
        especificos.add(moda, "Moda");

        JButton elegir = Estilo.boton("Elegir imagen...", false);
        JButton sinImagen = Estilo.boton("Usar generica", false);
        elegir.addActionListener(e -> elegirImagen());
        sinImagen.addActionListener(e -> { imagenElegida = null; quitarImagen = true; mostrarImagen(); });
        JPanel img = new JPanel(new BorderLayout(6, 6));
        img.setBorder(BorderFactory.createTitledBorder("Imagen"));
        img.add(vistaImagen, BorderLayout.CENTER);
        JPanel bi = new JPanel(new GridLayout(0, 1, 4, 4));
        bi.add(elegir); bi.add(sinImagen);
        img.add(bi, BorderLayout.SOUTH);

        JPanel centro = new JPanel(new BorderLayout(10, 10));
        centro.setBorder(new EmptyBorder(12, 14, 12, 14));
        JPanel izquierda = new JPanel(new BorderLayout(8, 8));
        izquierda.add(comun, BorderLayout.NORTH);
        especificos.setBorder(BorderFactory.createTitledBorder("Datos de la subcategoria"));
        izquierda.add(especificos, BorderLayout.CENTER);
        centro.add(izquierda, BorderLayout.CENTER);
        centro.add(img, BorderLayout.EAST);
        add(centro, BorderLayout.CENTER);

        JButton guardar = Estilo.boton(original == null ? "Publicar" : "Guardar cambios", true);
        JButton cancelar = Estilo.boton("Cancelar", false);
        guardar.addActionListener(e -> guardar());
        cancelar.addActionListener(e -> dispose());
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        sur.add(cancelar); sur.add(guardar);
        add(sur, BorderLayout.SOUTH);

        tipo.addActionListener(e -> cambiarTipo());
        if (original != null) cargar(original);
        cambiarTipo();
        mostrarImagen();
        pack();
        setLocationRelativeTo(padre);
    }

    private static JPanel fila(String e1, JComponent c1, String e2, JComponent c2) {
        JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
        p.setBorder(new EmptyBorder(6, 6, 6, 6));
        p.add(new JLabel(e1)); p.add(c1);
        if (e2 != null) { p.add(new JLabel(e2)); p.add(c2); }
        return p;
    }

    private static String grupo(String t) {
        switch (t) {
            case "Abarrotes": case "Lacteos": case "Salchichoneria": case "Frutas": case "Verduras": return "Alimentos";
            case "Hombre": case "Mujer": return "Moda";
            default: return t;
        }
    }

    private void cambiarTipo() {
        String g = grupo((String) tipo.getSelectedItem());
        tarjetas.show(especificos, g);
        boolean tec = !g.equals("Alimentos") && !g.equals("Moda");
        garantia.setEnabled(tec);
        garantiaEtiqueta.setEnabled(tec);
    }

    private void cargar(Articulo a) {
        tipo.setSelectedItem(a.getTipo());
        tipo.setEnabled(false); // el tipo define la clase: no se cambia al editar
        nombre.setText(a.getNombre());
        precio.setText(String.valueOf(a.getPrecio()));
        stock.setText(String.valueOf(a.getStock()));
        if (a instanceof Tecnologia) garantia.setText(String.valueOf(((Tecnologia) a).getGarantiaMeses()));
        if (a instanceof Computadora) { procesador.setText(((Computadora) a).getProcesador()); ram.setText("" + ((Computadora) a).getRamGb()); }
        if (a instanceof Laptop) { peso.setText("" + ((Laptop) a).getPesoKg()); bateria.setText("" + ((Laptop) a).getBateriaHoras()); }
        if (a instanceof Celular) { almacenamiento.setText("" + ((Celular) a).getAlmacenamientoGb()); so.setText(((Celular) a).getSistemaOperativo()); }
        if (a instanceof Televisor) { pulgadas.setText("" + ((Televisor) a).getPulgadas()); resolucion.setText(((Televisor) a).getResolucion()); }
        if (a instanceof Alimentos) caducidad.setText(((Alimentos) a).getFechaCaducidad().toString());
        if (a instanceof Moda) { talla.setSelectedItem(((Moda) a).getTalla()); color.setText(((Moda) a).getColor()); temporada.setSelected(((Moda) a).isEnTemporada()); }
    }

    private void elegirImagen() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Imagenes (png, jpg, gif)", "png", "jpg", "jpeg", "gif"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            imagenElegida = fc.getSelectedFile().toPath();
            quitarImagen = false;
            mostrarImagen();
        }
    }

    private void mostrarImagen() {
        vistaImagen.removeAll();
        Path ruta = imagenElegida != null ? imagenElegida
                : tienda.getRepositorio().resolverImagen(quitarImagen || original == null ? null : original.getImagen());
        vistaImagen.add(Estilo.imagen(ruta, nombre.getText().isBlank() ? "Sin imagen" : nombre.getText(), 170, 130));
        vistaImagen.revalidate();
        vistaImagen.repaint();
    }

    private static int entero(JTextField f, String campo) {
        try { return Integer.parseInt(f.getText().trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(campo + " debe ser un numero entero"); }
    }

    private static double decimal(JTextField f, String campo) {
        try { return Double.parseDouble(f.getText().trim().replace(",", "")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(campo + " debe ser un numero"); }
    }

    private Articulo construir(int id, String rutaImagen) {
        String t = (String) tipo.getSelectedItem();
        String n = nombre.getText();
        double p = decimal(precio, "El precio");
        int s = entero(stock, "El stock");
        switch (grupo(t)) {
            case "Alimentos": {
                LocalDate f;
                try { f = LocalDate.parse(caducidad.getText().trim()); }
                catch (DateTimeParseException e) { throw new IllegalArgumentException("La fecha de caducidad debe tener el formato AAAA-MM-DD"); }
                switch (t) {
                    case "Abarrotes": return new Abarrotes(id, n, p, s, rutaImagen, f);
                    case "Lacteos": return new Lacteos(id, n, p, s, rutaImagen, f);
                    case "Salchichoneria": return new Salchichoneria(id, n, p, s, rutaImagen, f);
                    case "Frutas": return new Frutas(id, n, p, s, rutaImagen, f);
                    default: return new Verduras(id, n, p, s, rutaImagen, f);
                }
            }
            case "Moda":
                return t.equals("Hombre")
                        ? new Hombre(id, n, p, s, rutaImagen, (Talla) talla.getSelectedItem(), color.getText(), temporada.isSelected())
                        : new Mujer(id, n, p, s, rutaImagen, (Talla) talla.getSelectedItem(), color.getText(), temporada.isSelected());
            default: {
                int g = entero(garantia, "La garantia");
                switch (t) {
                    case "Computadora": return new Computadora(id, n, p, s, rutaImagen, g, procesador.getText(), entero(ram, "La RAM"));
                    case "Laptop": return new Laptop(id, n, p, s, rutaImagen, g, decimal(peso, "El peso"), entero(bateria, "La bateria"));
                    case "Celular": return new Celular(id, n, p, s, rutaImagen, g, entero(almacenamiento, "El almacenamiento"), so.getText());
                    default: return new Televisor(id, n, p, s, rutaImagen, g, entero(pulgadas, "Las pulgadas"), resolucion.getText());
                }
            }
        }
    }

    private void guardar() {
        Estilo.intentar(this, () -> {
            int id = original != null ? original.getId() : tienda.reservarIdArticulo();
            String ruta = original == null || quitarImagen ? null : original.getImagen();
            construir(id, ruta); // valida todos los campos antes de copiar la imagen
            if (imagenElegida != null) ruta = tienda.importarImagen(imagenElegida, id);
            Articulo a = construir(id, ruta);
            try {
                if (original == null) admin.agregarProducto(a); else admin.editarProducto(a);
            } catch (CorreoDuplicadoException e) {
                Estilo.aviso(this, e.getMessage()); // CU-05 flujo alterno: el producto ya existe
                return;
            }
            dispose();
        });
    }
}
