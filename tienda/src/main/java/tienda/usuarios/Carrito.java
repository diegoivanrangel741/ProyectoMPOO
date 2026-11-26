package tienda.usuarios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tienda.articulos.Alimentos;
import tienda.articulos.Articulo;

/**
 * RF-05: arreglo polimorfico de articulos. Cada unidad ocupa una posicion de la lista.
 * Limite: 15 articulos. No se pueden agregar mas unidades de las que hay en stock.
 * Los metodos son synchronized para que dos ventanas/hilos no lo corrompan.
 */
public class Carrito {
    public static final int MAX_ARTICULOS = 15;

    private final List<Articulo> articulos = new ArrayList<>();

    public synchronized void agregar(Articulo articulo) {
        if (articulo == null) throw new IllegalArgumentException("El articulo no puede ser nulo");
        if (articulos.size() >= MAX_ARTICULOS)
            throw new IllegalStateException("El carrito admite maximo " + MAX_ARTICULOS + " articulos");
        if (articulo instanceof Alimentos && ((Alimentos) articulo).estaCaducado())
            throw new IllegalStateException("\"" + articulo.getNombre() + "\" esta caducado y no se puede vender");
        if (contar(articulo.getId()) + 1 > articulo.getStock())
            throw new IllegalStateException("Sin stock suficiente de \"" + articulo.getNombre() + "\" (disponibles: "
                    + articulo.getStock() + ")");
        articulos.add(articulo);
    }

    /** Agrega sin validar stock; solo para restaurar el carrito guardado tal cual (RF-06). */
    public synchronized void restaurar(Articulo articulo) {
        if (articulo != null && articulos.size() < MAX_ARTICULOS) articulos.add(articulo);
    }

    /** Quita una unidad del articulo con ese id. */
    public synchronized boolean quitar(int id) {
        for (int i = 0; i < articulos.size(); i++) {
            if (articulos.get(i).getId() == id) {
                articulos.remove(i);
                return true;
            }
        }
        return false;
    }

    /** Quita todas las unidades del articulo con ese id. */
    public synchronized int quitarTodos(int id) {
        int antes = articulos.size();
        articulos.removeIf(a -> a.getId() == id);
        return antes - articulos.size();
    }

    /** Cuando el administrador edita un producto, el carrito apunta al objeto nuevo. */
    public synchronized void reemplazar(Articulo nuevo) {
        for (int i = 0; i < articulos.size(); i++) {
            if (articulos.get(i).getId() == nuevo.getId()) articulos.set(i, nuevo);
        }
    }

    /** Suma getPrecioFinal() de cada articulo (polimorfismo). */
    public synchronized double calcularTotal() {
        double total = 0;
        for (Articulo a : articulos) total += a.getPrecioFinal();
        return Math.round(total * 100.0) / 100.0;
    }

    public synchronized int contar(int id) {
        int n = 0;
        for (Articulo a : articulos) if (a.getId() == id) n++;
        return n;
    }

    /** Articulo -> cantidad, en el orden en que se agregaron. */
    public synchronized Map<Articulo, Integer> agrupar() {
        Map<Integer, Articulo> porId = new LinkedHashMap<>();
        Map<Articulo, Integer> resultado = new LinkedHashMap<>();
        for (Articulo a : articulos) {
            Articulo clave = porId.computeIfAbsent(a.getId(), k -> a);
            resultado.merge(clave, 1, Integer::sum);
        }
        return resultado;
    }

    public synchronized List<Articulo> getArticulos() {
        return Collections.unmodifiableList(new ArrayList<>(articulos));
    }

    public synchronized int getCantidad() { return articulos.size(); }
    public synchronized boolean estaVacio() { return articulos.isEmpty(); }
    public synchronized void vaciar() { articulos.clear(); }
}
