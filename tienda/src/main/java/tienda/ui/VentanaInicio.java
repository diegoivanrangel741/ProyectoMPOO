package tienda.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import tienda.servicio.Tienda;
import tienda.usuarios.Administrador;
import tienda.usuarios.Usuario;
import tienda.usuarios.UsuarioRegistrado;

/**
 * Inicio de sesion, registro (CU-01) y acceso como invitado.
 * La ventana se queda abierta: se pueden abrir varias sesiones a la vez (casos simultaneos).
 */
class VentanaInicio extends JFrame {
    private final Tienda tienda;
    private final JTextField correo = new JTextField(22);
    private final JPasswordField contrasena = new JPasswordField(22);
    private final JButton entrar = Estilo.boton("Iniciar sesion", true);

    VentanaInicio(Tienda tienda) {
        super("Tienda en Linea MPOO - Acceso");
        this.tienda = tienda;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(Estilo.encabezado("Tienda en Linea MPOO", "Simulador de tienda - Facultad de Ingenieria, UNAM"), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(18, 24, 8, 24));
        form.setBackground(Color.WHITE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0; form.add(etiqueta("Correo"), g);
        g.gridx = 1; form.add(correo, g);
        g.gridx = 0; g.gridy = 1; form.add(etiqueta("Contrasena"), g);
        g.gridx = 1; form.add(contrasena, g);
        g.gridx = 1; g.gridy = 2; g.fill = GridBagConstraints.HORIZONTAL; form.add(entrar, g);
        JButton registrar = Estilo.boton("Crear cuenta", false);
        JButton invitado = Estilo.boton("Entrar como invitado", false);
        g.gridy = 3; form.add(registrar, g);
        g.gridy = 4; form.add(invitado, g);
        JLabel nota = new JLabel("<html><i>Puedes abrir varias sesiones a la vez para probar compras simultaneas.</i></html>");
        nota.setFont(Estilo.CHICA);
        nota.setForeground(Estilo.TEXTO_SUAVE);
        g.gridx = 0; g.gridy = 5; g.gridwidth = 2; form.add(nota, g);
        add(form, BorderLayout.CENTER);

        entrar.addActionListener(e -> iniciarSesion());
        contrasena.addActionListener(e -> iniciarSesion());
        registrar.addActionListener(e -> new DialogoRegistro(this, tienda, correo).setVisible(true));
        invitado.addActionListener(e -> abrir(tienda.entrarComoInvitado()));
        getRootPane().setDefaultButton(entrar);
        pack();
        setLocationRelativeTo(null);
    }

    private static JLabel etiqueta(String t) {
        JLabel l = new JLabel(t);
        l.setFont(Estilo.NORMAL.deriveFont(Font.BOLD));
        return l;
    }

    private void iniciarSesion() {
        String c = correo.getText();
        String p = new String(contrasena.getPassword());
        if (c.isBlank() || p.isEmpty()) {
            Estilo.aviso(this, "Escribe tu correo y tu contrasena");
            return;
        }
        entrar.setEnabled(false);
        Estilo.enSegundoPlano(this, () -> tienda.iniciarSesion(c, p), u -> {
            contrasena.setText("");
            abrir(u);
        });
        entrar.setEnabled(true);
    }

    void abrir(Usuario u) {
        JFrame v = (u instanceof Administrador) ? new VentanaAdmin(tienda, (Administrador) u) : new VentanaTienda(tienda, u);
        v.setLocationByPlatform(true);
        v.setVisible(true);
    }

    /** CU-01: registro con correo unico; flujo alterno "el correo ya existe". */
    static class DialogoRegistro extends JDialog {
        DialogoRegistro(VentanaInicio padre, Tienda tienda, JTextField correoLogin) {
            super(padre, "Crear cuenta", true);
            JTextField nombre = new JTextField(22);
            JTextField correo = new JTextField(22);
            JPasswordField pass = new JPasswordField(22);
            JPasswordField pass2 = new JPasswordField(22);
            JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
            p.setBorder(new EmptyBorder(14, 16, 8, 16));
            p.add(new JLabel("Nombre")); p.add(nombre);
            p.add(new JLabel("Correo")); p.add(correo);
            p.add(new JLabel("Contrasena (min. 6)")); p.add(pass);
            p.add(new JLabel("Confirmar contrasena")); p.add(pass2);
            JButton crear = Estilo.boton("Registrarme", true);
            JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            sur.add(crear);
            add(p, BorderLayout.CENTER);
            add(sur, BorderLayout.SOUTH);
            crear.addActionListener(e -> {
                String c1 = new String(pass.getPassword()), c2 = new String(pass2.getPassword());
                if (!c1.equals(c2)) { Estilo.aviso(this, "Las contrasenas no coinciden"); return; }
                Estilo.enSegundoPlano(this, () -> tienda.registrar(correo.getText(), c1, nombre.getText()),
                        (UsuarioRegistrado u) -> {
                            Estilo.info(this, "Cuenta creada", "Bienvenido(a), " + u.getNombre() + ". Tu numero de cliente es " + u.getId() + ".");
                            correoLogin.setText(u.getCorreo());
                            dispose();
                            padre.abrir(u);
                        });
            });
            getRootPane().setDefaultButton(crear);
            pack();
            setLocationRelativeTo(padre);
        }
    }
}
