package tienda.ui;

import java.awt.*;
import java.time.YearMonth;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import tienda.excepciones.TarjetaInvalidaException;
import tienda.pagos.*;
import tienda.servicio.Tienda;
import tienda.usuarios.Pedido;
import tienda.usuarios.UsuarioRegistrado;

/** CU-03: tarjeta (RF-07), transferencia (RF-08) o wallet con pop-up (RF-09). */
class DialogoPago extends JDialog {
    private final Tienda tienda;
    private final UsuarioRegistrado usuario;
    private final JTabbedPane pestanas = new JTabbedPane();
    private final JTextField numero = new JTextField(20);
    private final JTextField vencimiento = new JTextField(6);
    private final JLabel marca = new JLabel("Marca: -");
    private final JCheckBox transferido = new JCheckBox("Ya realice la transferencia");
    private boolean pagado = false;

    DialogoPago(Window padre, Tienda tienda, UsuarioRegistrado usuario) {
        super(padre, "Pagar", ModalityType.APPLICATION_MODAL);
        this.tienda = tienda;
        this.usuario = usuario;
        double total = usuario.getCarrito().calcularTotal();
        setLayout(new BorderLayout());
        add(Estilo.encabezado("Total a pagar: " + Estilo.dinero(total), usuario.getCarrito().getCantidad() + " articulo(s)"), BorderLayout.NORTH);

        JPanel tarjeta = new JPanel(new GridBagLayout());
        tarjeta.setBorder(new EmptyBorder(14, 14, 14, 14));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0; tarjeta.add(new JLabel("Numero (15 o 16 digitos)"), g);
        g.gridx = 1; tarjeta.add(numero, g);
        g.gridx = 0; g.gridy = 1; tarjeta.add(new JLabel("Vencimiento (MM/AA)"), g);
        g.gridx = 1; tarjeta.add(vencimiento, g);
        g.gridx = 1; g.gridy = 2; marca.setFont(Estilo.NORMAL.deriveFont(Font.BOLD)); tarjeta.add(marca, g);
        numero.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { detectar(); }
            public void removeUpdate(DocumentEvent e) { detectar(); }
            public void changedUpdate(DocumentEvent e) { detectar(); }
        });
        pestanas.addTab("Tarjeta", tarjeta);

        PagoTransferencia datos = new PagoTransferencia();
        datos.setReferencia("CLIENTE-" + usuario.getId());
        JTextArea area = new JTextArea(datos.mostrarDatos() + "\nMonto: " + Estilo.dinero(total));
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 14));
        area.setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel trans = new JPanel(new BorderLayout(6, 6));
        trans.setBorder(new EmptyBorder(10, 10, 10, 10));
        trans.add(new JLabel("Transfiere a la siguiente cuenta:"), BorderLayout.NORTH);
        trans.add(area, BorderLayout.CENTER);
        trans.add(transferido, BorderLayout.SOUTH);
        pestanas.addTab("Transferencia", trans);

        JPanel wallet = new JPanel(new GridLayout(0, 1, 6, 6));
        wallet.setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel saldo = new JLabel("Saldo disponible (simulado): " + Estilo.dinero(usuario.getSaldoWallet()));
        saldo.setFont(Estilo.SUBTITULO);
        wallet.add(saldo);
        wallet.add(new JLabel("Al confirmar se cobrara el total y veras el resultado en un pop-up."));
        pestanas.addTab("Wallet", wallet);
        add(pestanas, BorderLayout.CENTER);

        JButton confirmar = Estilo.boton("Confirmar pago", true);
        JButton cancelar = Estilo.boton("Cancelar", false);
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        sur.add(cancelar); sur.add(confirmar);
        add(sur, BorderLayout.SOUTH);
        cancelar.addActionListener(e -> dispose());
        confirmar.addActionListener(e -> confirmar());
        getRootPane().setDefaultButton(confirmar);
        setSize(520, 340);
        setLocationRelativeTo(padre);
    }

    boolean fuePagado() { return pagado; }

    private void detectar() {
        String n = numero.getText().replace(" ", "").replace("-", "");
        if (n.length() == 15 || n.length() == 16) {
            try {
                marca.setText("Marca: " + new PagoTarjeta(n, YearMonth.now()).getMarca());
                return;
            } catch (TarjetaInvalidaException ignorada) { }
        }
        marca.setText("Marca: - (" + n.length() + " digitos)");
    }

    private void confirmar() {
        Estilo.intentar(this, () -> {
            Pago pago;
            switch (pestanas.getSelectedIndex()) {
                case 0:
                    pago = new PagoTarjeta(numero.getText(), PagoTarjeta.leerVencimiento(vencimiento.getText()));
                    break;
                case 1:
                    if (!transferido.isSelected()) {
                        Estilo.aviso(this, "Marca la casilla cuando hayas realizado la transferencia");
                        return;
                    }
                    pago = new PagoTransferencia();
                    break;
                default:
                    pago = new PagoWallet(usuario.getSaldoWallet(), new NotificadorSwing(getOwner()));
            }
            Pedido p = tienda.pagar(usuario, pago);
            pagado = true;
            if (!(pago instanceof PagoWallet))
                Estilo.info(this, "Pago exitoso", "Pedido #" + p.getId() + " registrado.\nTotal: " + Estilo.dinero(p.getTotal())
                        + "\nMetodo: " + p.getMetodoPago());
            dispose();
        });
    }
}
