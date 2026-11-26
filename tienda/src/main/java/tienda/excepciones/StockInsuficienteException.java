package tienda.excepciones;

/** No hay stock suficiente para agregar o pagar un articulo. */
public class StockInsuficienteException extends Exception {
    public StockInsuficienteException(String mensaje) {
        super(mensaje);
    }
}
