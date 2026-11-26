package tienda.usuarios;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** RF-11: registro inmutable de una compra. Sobrevive aunque se borre el usuario o el producto. */
public class Pedido {
    private final int id;
    private final int usuarioId;
    private final String correoUsuario;
    private final LocalDateTime fecha;
    private final double total;
    private final String metodoPago;
    private final List<LineaPedido> lineas;

    public Pedido(int id, int usuarioId, LocalDateTime fecha, double total, List<LineaPedido> lineas) {
        this(id, usuarioId, "", fecha, total, "", lineas);
    }

    public Pedido(int id, int usuarioId, String correoUsuario, LocalDateTime fecha, double total, String metodoPago,
                  List<LineaPedido> lineas) {
        if (id < 1) throw new IllegalArgumentException("El id del pedido debe ser mayor a 0");
        if (fecha == null) throw new IllegalArgumentException("El pedido necesita fecha");
        if (lineas == null || lineas.isEmpty()) throw new IllegalArgumentException("El pedido debe tener al menos una linea");
        if (total <= 0) throw new IllegalArgumentException("El total debe ser mayor a 0");
        this.id = id;
        this.usuarioId = usuarioId;
        this.correoUsuario = correoUsuario == null ? "" : correoUsuario;
        this.fecha = fecha.withNano(0);
        this.total = total;
        this.metodoPago = metodoPago == null ? "" : metodoPago;
        this.lineas = new ArrayList<>(lineas);
    }

    public int getId() { return id; }
    public int getUsuarioId() { return usuarioId; }
    public String getCorreoUsuario() { return correoUsuario; }
    public LocalDateTime getFecha() { return fecha; }
    public double getTotal() { return total; }
    public String getMetodoPago() { return metodoPago; }
    public List<LineaPedido> getLineas() { return Collections.unmodifiableList(lineas); }

    public int getUnidades() {
        int n = 0;
        for (LineaPedido l : lineas) n += l.getCantidad();
        return n;
    }
}
