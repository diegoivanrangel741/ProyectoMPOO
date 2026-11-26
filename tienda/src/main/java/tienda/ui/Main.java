package tienda.ui;

import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import tienda.configuracion.Configuracion;
import tienda.servicio.Tienda;

/** Punto de entrada. Los datos viven en la carpeta de trabajo o en -Dtienda.datos=ruta. */
public class Main {
    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels())
                if ("Nimbus".equals(i.getName())) UIManager.setLookAndFeel(i.getClassName());
        } catch (Exception ignorada) { }
        Estilo.configurarIdioma();
        Path datos = args.length > 0 ? Path.of(args[0]) : Configuracion.directorio();
        final Tienda tienda;
        try {
            tienda = new Tienda(datos);
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(null, "No se pudo abrir la carpeta de datos:\n" + e.getMessage(),
                    "Tienda", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // RF-06: al cerrar la app (o si la JVM termina) se guarda todo
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { tienda.guardarTodo(); } catch (RuntimeException ignorada) { }
        }, "guardar-al-cerrar"));
        // RNF-03 / RF-06 "tras un fallo": un error inesperado se informa, se guarda y la app sigue abierta
        Thread.setDefaultUncaughtExceptionHandler((hilo, error) -> {
            try { tienda.guardarTodo(); } catch (RuntimeException ignorada) { }
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Ocurrio un error inesperado, tus datos se guardaron:\n" + Estilo.mensaje(error),
                    "Tienda", JOptionPane.ERROR_MESSAGE));
        });
        SwingUtilities.invokeLater(() -> {
            VentanaInicio inicio = new VentanaInicio(tienda);
            inicio.setVisible(true);
            for (String aviso : tienda.consumirAvisos()) Estilo.aviso(inicio, aviso);
        });
    }
}
