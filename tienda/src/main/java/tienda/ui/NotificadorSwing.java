package tienda.ui;

import java.awt.Component;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import tienda.pagos.Notificador;

/** Implementacion Swing del Notificador: muestra el pop-up de la wallet (RF-09). */
class NotificadorSwing implements Notificador {
    private final Component padre;

    NotificadorSwing(Component padre) { this.padre = padre; }

    @Override
    public void notificar(String titulo, String mensaje, boolean exito) {
        // invokeLater: el pop-up se muestra cuando la operacion ya termino y libero el candado de la tienda
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(padre, mensaje, titulo,
                exito ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE));
    }
}
