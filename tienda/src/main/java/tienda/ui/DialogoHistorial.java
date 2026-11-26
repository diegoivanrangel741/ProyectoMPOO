package tienda.ui;

import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import tienda.servicio.Tienda;
import tienda.usuarios.LineaPedido;
import tienda.usuarios.Pedido;
import tienda.usuarios.UsuarioRegistrado;

/** RF-11: historial de pedidos. Lo usa el cliente (sus pedidos) y el administrador (todos). */
class DialogoHistorial extends JDialog {
    DialogoHistorial(Window padre, Tienda tienda, UsuarioRegistrado usuario) {
        super(padre, "Mis pedidos", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        add(Estilo.encabezado("Mis pedidos", usuario.getNombre()), BorderLayout.NORTH);
        add(new PanelPedidos(tienda.getHistorial(usuario), false), BorderLayout.CENTER);
        setSize(760, 460);
        setLocationRelativeTo(padre);
    }

    /** Tabla maestra de pedidos + detalle de lineas. */
    static class PanelPedidos extends JPanel {
        private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        private final DefaultTableModel pedidosModelo;
        private final DefaultTableModel lineasModelo = new DefaultTableModel(new Object[]{"Producto", "Precio unitario", "Cantidad", "Subtotal"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        private final JTable tabla;
        private List<Pedido> pedidos;
        private final boolean admin;

        PanelPedidos(List<Pedido> pedidos, boolean admin) {
            super(new BorderLayout());
            this.admin = admin;
            pedidosModelo = new DefaultTableModel(admin
                    ? new Object[]{"Pedido", "Fecha", "Usuario id", "Correo", "Metodo", "Unidades", "Total"}
                    : new Object[]{"Pedido", "Fecha", "Metodo", "Unidades", "Total"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            tabla = new JTable(pedidosModelo);
            tabla.setRowHeight(24);
            tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JTable detalle = new JTable(lineasModelo);
            detalle.setRowHeight(22);
            tabla.getSelectionModel().addListSelectionListener(e -> mostrarLineas());
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tabla), new JScrollPane(detalle));
            split.setResizeWeight(0.6);
            add(split, BorderLayout.CENTER);
            cargar(pedidos);
        }

        void cargar(List<Pedido> lista) {
            this.pedidos = lista;
            pedidosModelo.setRowCount(0);
            for (Pedido p : lista) {
                if (admin) pedidosModelo.addRow(new Object[]{p.getId(), p.getFecha().format(F), p.getUsuarioId(),
                        p.getCorreoUsuario(), p.getMetodoPago(), p.getUnidades(), Estilo.dinero(p.getTotal())});
                else pedidosModelo.addRow(new Object[]{p.getId(), p.getFecha().format(F), p.getMetodoPago(),
                        p.getUnidades(), Estilo.dinero(p.getTotal())});
            }
            lineasModelo.setRowCount(0);
            if (!lista.isEmpty()) tabla.setRowSelectionInterval(lista.size() - 1, lista.size() - 1);
        }

        private void mostrarLineas() {
            lineasModelo.setRowCount(0);
            int f = tabla.getSelectedRow();
            if (f < 0 || f >= pedidos.size()) return;
            for (LineaPedido l : pedidos.get(f).getLineas())
                lineasModelo.addRow(new Object[]{l.getNombre(), Estilo.dinero(l.getPrecioUnitario()), l.getCantidad(), Estilo.dinero(l.getSubtotal())});
        }
    }
}
