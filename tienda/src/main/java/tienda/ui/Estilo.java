package tienda.ui;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import tienda.articulos.*;

/** Colores, tipografia y utilerias comunes de la interfaz. */
final class Estilo {
    static final Color AZUL = new Color(0x1F3A68);
    static final Color AZUL_CLARO = new Color(0xE8EEF7);
    static final Color ORO = new Color(0xC9A227);
    static final Color FONDO = new Color(0xF6F7F9);
    static final Color TEXTO_SUAVE = new Color(0x5B6472);
    static final Color VERDE = new Color(0x2E7D32);
    static final Color ROJO = new Color(0xC62828);
    static final Font TITULO = new Font("SansSerif", Font.BOLD, 22);
    static final Font SUBTITULO = new Font("SansSerif", Font.BOLD, 15);
    static final Font NORMAL = new Font("SansSerif", Font.PLAIN, 13);
    static final Font CHICA = new Font("SansSerif", Font.PLAIN, 11);
    private static final NumberFormat MONEDA = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"));
    private static final Map<String, ImageIcon> CACHE = new ConcurrentHashMap<>();

    private Estilo() { }

    /** Textos de los cuadros de dialogo estandar en espanol. */
    static void configurarIdioma() {
        UIManager.put("OptionPane.yesButtonText", "Si");
        UIManager.put("OptionPane.noButtonText", "No");
        UIManager.put("OptionPane.cancelButtonText", "Cancelar");
        UIManager.put("OptionPane.okButtonText", "Aceptar");
        UIManager.put("FileChooser.openButtonText", "Abrir");
        UIManager.put("FileChooser.cancelButtonText", "Cancelar");
    }

    static String dinero(double v) { return MONEDA.format(v); }

    static JButton boton(String texto, boolean principal) {
        JButton b = new JButton(texto);
        b.setFont(NORMAL.deriveFont(Font.BOLD));
        b.setFocusPainted(false);
        if (principal) {
            b.setBackground(AZUL);
            b.setForeground(Color.WHITE);
        }
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    static JPanel encabezado(String titulo, String subtitulo) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(AZUL);
        p.setBorder(new EmptyBorder(12, 16, 12, 16));
        JLabel t = new JLabel(titulo);
        t.setFont(TITULO);
        t.setForeground(Color.WHITE);
        p.add(t, BorderLayout.NORTH);
        if (subtitulo != null) {
            JLabel s = new JLabel(subtitulo);
            s.setFont(NORMAL);
            s.setForeground(new Color(0xD6DEEB));
            p.add(s, BorderLayout.SOUTH);
        }
        return p;
    }

    /** Leyenda del precio segun la categoria (polimorfismo visible para el usuario). */
    static String leyendaPrecio(Articulo a) {
        if (a instanceof Tecnologia) return "IVA 16% incluido";
        if (a instanceof Alimentos) {
            Alimentos al = (Alimentos) a;
            if (al.estaCaducado()) return "Caducado";
            return al.tieneDescuento() ? "-10% por caducar pronto" : "Caduca " + al.getFechaCaducidad();
        }
        if (a instanceof Moda) return ((Moda) a).isEnTemporada() ? "-15% de temporada" : "Talla " + ((Moda) a).getTalla();
        return "";
    }

    /**
     * Seccion 12: si falta la imagen se usa la generica; si tampoco existe se muestra el nombre en texto.
     * La excepcion de archivo no encontrado / ilegible se captura aqui.
     */
    static JComponent imagen(Path ruta, String nombre, int ancho, int alto) {
        ImageIcon icono = null;
        if (ruta != null) {
            try {
                String clave = ruta + "|" + Files.getLastModifiedTime(ruta).toMillis() + "|" + ancho + "x" + alto;
                icono = CACHE.get(clave);
                if (icono == null) {
                    Image img = ImageIO.read(ruta.toFile());
                    if (img != null) {
                        double esc = Math.min((double) ancho / img.getWidth(null), (double) alto / img.getHeight(null));
                        int w = Math.max(1, (int) (img.getWidth(null) * esc));
                        int h = Math.max(1, (int) (img.getHeight(null) * esc));
                        icono = new ImageIcon(img.getScaledInstance(w, h, Image.SCALE_SMOOTH));
                        CACHE.put(clave, icono);
                    }
                }
            } catch (Exception e) {
                icono = null; // archivo inexistente o danado -> texto plano
            }
        }
        JLabel l = icono != null ? new JLabel(icono)
                : new JLabel("<html><div style='text-align:center;width:" + (ancho - 20) + "px'>" + escapar(nombre) + "</div></html>");
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setPreferredSize(new Dimension(ancho, alto));
        l.setOpaque(true);
        l.setBackground(Color.WHITE);
        if (icono == null) {
            l.setFont(SUBTITULO);
            l.setForeground(TEXTO_SUAVE);
            l.setBorder(BorderFactory.createDashedBorder(new Color(0xB0B8C4)));
        }
        return l;
    }

    static String escapar(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static void error(Component padre, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, "No se pudo completar", JOptionPane.ERROR_MESSAGE);
    }

    static void aviso(Component padre, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    static void info(Component padre, String titulo, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, titulo, JOptionPane.INFORMATION_MESSAGE);
    }

    /** RNF-03: cualquier excepcion de una accion se muestra como mensaje; la app no se cierra. */
    interface Accion { void ejecutar() throws Exception; }

    static void intentar(Component padre, Accion a) {
        try {
            a.ejecutar();
        } catch (Exception e) {
            error(padre, mensaje(e));
        }
    }

    static String mensaje(Throwable e) {
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m.trim();
    }

    /** Ejecuta trabajo lento (bcrypt, disco) fuera del hilo de Swing y regresa el resultado en el hilo de Swing. */
    static <T> void enSegundoPlano(Component padre, Callable<T> trabajo, Consumer<T> alTerminar) {
        Cursor antes = padre.getCursor();
        padre.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return trabajo.call(); }
            @Override protected void done() {
                padre.setCursor(antes);
                try {
                    alTerminar.accept(get());
                } catch (java.util.concurrent.ExecutionException e) {
                    error(padre, mensaje(e.getCause()));
                } catch (Exception e) {
                    error(padre, mensaje(e));
                }
            }
        }.execute();
    }
}
