package tienda.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import tienda.articulos.Articulo;
import tienda.servicio.Tienda;
import tienda.usuarios.Carrito;
import tienda.usuarios.Invitado;
import tienda.usuarios.Usuario;
import tienda.usuarios.UsuarioRegistrado;

/**
 * Carrito (RF-05). Al abrirlo y antes de pagar valida la disponibilidad de cada articulo (nota 14):
 * si alguno ya no esta disponible avisa y solo permite eliminarlo.
 */
class DialogoCarrito extends JDialog {
    private final Tienda tienda;
    private final Usuario usuario;
    private final VentanaTienda ventana;
    private final DefaultTableModel modelo = new DefaultTableModel(
            new Object[]{"Producto", "Tipo", "Precio unitario", "Cantidad", "Subtotal", "Estado"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable tabla = new JTable(modelo);
    private final List<Integer> ids = new ArrayList<>();
    private final JLabel total = new JLabel();
    private final JButton pagar = Estilo.boton("Pagar", true);
    private Map<Integer, String> problemas = Map.of();

    DialogoCarrito(VentanaTienda ventana, Tienda tienda, Usuario usuario) {
        super(ventana, "Mi carrito", true);
        this.ventana = ventana;
        this.tienda = tienda;
        this.usuario = usuario;
        setLayout(new BorderLayout());
        add(Estilo.encabezado("Mi carrito", "Maximo " + Carrito.MAX_ARTICULOS + " articulos"), BorderLayout.NORTH);
        tabla.setRowHeight(26);
        tabla.setFont(Estilo.NORMAL);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                comp.setForeground("Disponible".equals(v) ? Estilo.VERDE : Estilo.ROJO);
                return comp;
            }
        });
        add(new JScrollPane(tabla), BorderLayout.CENTER);

        JButton quitarUno = Estilo.boton("Quitar 1", false);
        JButton quitarTodo = Estilo.boton("Quitar producto", false);
        JButton cerrar = Estilo.boton("Seguir comprando", false);
        total.setFont(Estilo.SUBTITULO);
        JPanel sur = new JPanel(new BorderLayout());
        sur.setBorder(new EmptyBorder(8, 10, 8, 10));
        sur.add(total, BorderLayout.WEST);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(quitarUno); botones.add(quitarTodo); botones.add(cerrar); botones.add(pagar);
        sur.add(botones, BorderLayout.EAST);
        add(sur, BorderLayout.SOUTH);

        quitarUno.addActionListener(e -> quitar(false));
        quitarTodo.addActionListener(e -> quitar(true));
        cerrar.addActionListener(e -> dispose());
        pagar.addActionListener(e -> pagar());
        setSize(820, 420);
        setLocationRelativeTo(ventana);
        cargar(true);
    }

    private void cargar(boolean avisar) {
        modelo.setRowCount(0);
        ids.clear();
        problemas = tienda.validarCarrito(usuario);
        Carrito c = Tienda.carritoDe(usuario);
        for (Map.Entry<Articulo, Integer> e : c.agrupar().entrySet()) {
            Articulo a = e.getKey();
            ids.add(a.getId());
            String est = problemas.getOrDefault(a.getId(), "Disponible");
            modelo.addRow(new Object[]{a.getNombre(), a.getTipo(), Estilo.dinero(a.getPrecioFinal()), e.getValue(),
                    Estilo.dinero(a.getPrecioFinal(e.getValue())), est});
        }
        total.setText("Total: " + Estilo.dinero(c.calcularTotal()) + "   (" + c.getCantidad() + " articulo(s))");
        pagar.setEnabled(!c.estaVacio() && problemas.isEmpty());
        if (avisar && !problemas.isEmpty()) {
            StringBuilder sb = new StringBuilder("Algunos articulos ya no estan disponibles y debes quitarlos para poder pagar:\n");
            for (int i = 0; i < ids.size(); i++)
                if (problemas.containsKey(ids.get(i))) sb.append("\n - ").append(modelo.getValueAt(i, 0)).append(": ").append(problemas.get(ids.get(i)));
            SwingUtilities.invokeLater(() -> Estilo.aviso(this, sb.toString()));
        }
        ventana.refrescar();
    }

    private void quitar(boolean todos) {
        int fila = tabla.getSelectedRow();
        if (fila < 0) { Estilo.aviso(this, "Selecciona un producto de la tabla"); return; }
        tienda.quitarDelCarrito(usuario, ids.get(fila), todos);
        cargar(false);
    }

    private void pagar() {
        if (usuario instanceof Invitado) {
            Estilo.aviso(this, "Para pagar necesitas una cuenta.\nCierra la sesion de invitado y usa \"Crear cuenta\".");
            return;
        }
        cargar(true); // nota 14: se valida de nuevo justo antes de pagar
        if (!problemas.isEmpty()) return;
        DialogoPago dp = new DialogoPago(this, tienda, (UsuarioRegistrado) usuario);
        dp.setVisible(true);
        if (dp.fuePagado()) dispose(); else cargar(false);
    }
}
