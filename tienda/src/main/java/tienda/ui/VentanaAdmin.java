package tienda.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import tienda.articulos.Articulo;
import tienda.servicio.Tienda;
import tienda.usuarios.Administrador;
import tienda.usuarios.UsuarioRegistrado;

/** Panel del administrador: productos (RF-12), usuarios (RF-13) e historial completo (RF-11). */
class VentanaAdmin extends JFrame {
    private final Tienda tienda;
    private final Administrador admin;
    private final DefaultTableModel productos = modelo("Id", "Tipo", "Categoria", "Nombre", "Precio", "Precio final", "Stock", "Imagen");
    private final DefaultTableModel usuarios = modelo("Id", "Nombre", "Correo", "Articulos en carrito", "Pedidos", "Saldo wallet");
    private final JTable tablaProductos = new JTable(productos);
    private final JTable tablaUsuarios = new JTable(usuarios);
    private final DialogoHistorial.PanelPedidos pedidos;
    private final List<Integer> idsProductos = new ArrayList<>();
    private final List<Integer> idsUsuarios = new ArrayList<>();
    private boolean cerrada = false;
    private final Runnable oyente = () -> SwingUtilities.invokeLater(this::refrescar);

    VentanaAdmin(Tienda tienda, Administrador admin) {
        super("Tienda MPOO - Administrador");
        this.tienda = tienda;
        this.admin = admin;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        JPanel norte = Estilo.encabezado("Panel de administracion", admin.getCorreo());
        JButton salir = Estilo.boton("Cerrar sesion", false);
        salir.addActionListener(e -> dispose());
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        der.setOpaque(false);
        der.add(salir);
        norte.add(der, BorderLayout.EAST);
        add(norte, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Productos", panelProductos());
        tabs.addTab("Usuarios", panelUsuarios());
        pedidos = new DialogoHistorial.PanelPedidos(tienda.getPedidos(), true);
        tabs.addTab("Pedidos (historial completo)", pedidos);
        add(tabs, BorderLayout.CENTER);

        tienda.agregarOyente(oyente);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { cerrada = true; tienda.quitarOyente(oyente); }
        });
        setSize(1000, 600);
        refrescar();
    }

    private static DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    private JPanel panelProductos() {
        JPanel p = new JPanel(new BorderLayout());
        tablaProductos.setRowHeight(24);
        tablaProductos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        p.add(new JScrollPane(tablaProductos), BorderLayout.CENTER);
        JButton agregar = Estilo.boton("Agregar producto", true);
        JButton editar = Estilo.boton("Editar", false);
        JButton eliminar = Estilo.boton("Eliminar", false);
        agregar.addActionListener(e -> new FormularioProducto(this, tienda, admin, null).setVisible(true));
        editar.addActionListener(e -> {
            Articulo a = productoSeleccionado();
            if (a != null) new FormularioProducto(this, tienda, admin, a).setVisible(true);
        });
        eliminar.addActionListener(e -> {
            Articulo a = productoSeleccionado();
            if (a == null) return;
            if (JOptionPane.showConfirmDialog(this, "Eliminar \"" + a.getNombre() + "\"?\nTambien se quitara de los carritos de todos los usuarios.",
                    "Eliminar producto", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                Estilo.intentar(this, () -> admin.eliminarProducto(a.getId()));
        });
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.setBorder(new EmptyBorder(4, 4, 4, 4));
        botones.add(agregar); botones.add(editar); botones.add(eliminar);
        p.add(botones, BorderLayout.SOUTH);
        return p;
    }

    private JPanel panelUsuarios() {
        JPanel p = new JPanel(new BorderLayout());
        tablaUsuarios.setRowHeight(24);
        tablaUsuarios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        p.add(new JScrollPane(tablaUsuarios), BorderLayout.CENTER);
        JButton eliminar = Estilo.boton("Eliminar usuario", true);
        eliminar.addActionListener(e -> {
            int f = tablaUsuarios.getSelectedRow();
            if (f < 0) { Estilo.aviso(this, "Selecciona un usuario"); return; }
            int id = idsUsuarios.get(f);
            if (JOptionPane.showConfirmDialog(this, "Eliminar al usuario #" + id + " (" + usuarios.getValueAt(f, 2) + ")?\n"
                    + "Se borran su cuenta y su carrito. Sus pedidos se conservan en el historial.",
                    "Eliminar usuario", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
                Estilo.intentar(this, () -> admin.eliminarUsuario(id));
        });
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.add(eliminar);
        p.add(botones, BorderLayout.SOUTH);
        return p;
    }

    private Articulo productoSeleccionado() {
        int f = tablaProductos.getSelectedRow();
        if (f < 0) { Estilo.aviso(this, "Selecciona un producto"); return null; }
        Articulo a = tienda.buscarArticulo(idsProductos.get(f));
        if (a == null) Estilo.aviso(this, "El producto ya no existe");
        return a;
    }

    void refrescar() {
        if (cerrada) return;
        productos.setRowCount(0);
        idsProductos.clear();
        for (Articulo a : tienda.getCatalogo()) {
            idsProductos.add(a.getId());
            productos.addRow(new Object[]{a.getId(), a.getTipo(), a.getCategoria(), a.getNombre(), Estilo.dinero(a.getPrecio()),
                    Estilo.dinero(a.getPrecioFinal()), a.getStock(), a.getImagen()});
        }
        usuarios.setRowCount(0);
        idsUsuarios.clear();
        for (UsuarioRegistrado u : tienda.getUsuarios()) {
            idsUsuarios.add(u.getId());
            usuarios.addRow(new Object[]{u.getId(), u.getNombre(), u.getCorreo(), u.getCarrito().getCantidad(),
                    tienda.getHistorial(u).size(), Estilo.dinero(u.getSaldoWallet())});
        }
        pedidos.cargar(tienda.getPedidos());
    }
}
