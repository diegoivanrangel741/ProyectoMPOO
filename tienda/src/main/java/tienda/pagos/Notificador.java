package tienda.pagos;

/**
 * Punto de extension para mostrar mensajes sin que la logica dependa de Swing (RNF-05).
 * La interfaz grafica entrega una implementacion que abre un pop-up (JOptionPane).
 */
@FunctionalInterface
public interface Notificador {
    void notificar(String titulo, String mensaje, boolean exito);

    /** Notificador por defecto: escribe en consola (util en pruebas automatizadas). */
    Notificador CONSOLA = (t, m, ok) -> System.out.println("[" + t + "] " + m.replace('\n', ' '));
}
