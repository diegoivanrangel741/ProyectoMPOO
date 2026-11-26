package tienda.usuarios;

import java.util.regex.Pattern;
import tienda.configuracion.Configuracion;
import tienda.seguridad.HashContrasena;

/** Usuario base. La contrasena solo existe como hash (RNF-04). */
public abstract class Usuario {
    private static final Pattern FORMATO_CORREO =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    public static final int LONGITUD_MINIMA_CONTRASENA = 6;
    private static volatile HashContrasena hasher;

    private String correo;
    private String contrasenaHash;
    private String nombre;

    /** Crea un usuario nuevo cifrando la contrasena. */
    protected Usuario(String correo, String contrasena, String nombre) {
        setCorreo(correo);
        setNombre(nombre);
        cambiarContrasena(contrasena);
    }

    /** Reconstruye un usuario desde JSON con un hash ya calculado (o sin contrasena, para el invitado). */
    protected Usuario(String correo, String nombre, String contrasenaHash, boolean desdeHash) {
        setCorreo(correo);
        setNombre(nombre);
        this.contrasenaHash = contrasenaHash;
    }

    protected static HashContrasena hasher() {
        if (hasher == null) {
            synchronized (Usuario.class) {
                if (hasher == null) hasher = new HashContrasena(Configuracion.obtenerPimienta());
            }
        }
        return hasher;
    }

    public static String normalizarCorreo(String correo) {
        return correo == null ? null : correo.trim().toLowerCase();
    }

    public static boolean correoValido(String correo) {
        return correo != null && correo.length() <= 254 && FORMATO_CORREO.matcher(correo.trim()).matches();
    }

    public static void validarContrasena(String contrasena) {
        if (contrasena == null || contrasena.isBlank())
            throw new IllegalArgumentException("Se necesita una contrasena");
        if (contrasena.length() < LONGITUD_MINIMA_CONTRASENA)
            throw new IllegalArgumentException("La contrasena debe tener al menos " + LONGITUD_MINIMA_CONTRASENA + " caracteres");
    }

    public final void setCorreo(String correo) {
        if (!correoValido(correo)) throw new IllegalArgumentException("El correo no tiene un formato valido: " + correo);
        this.correo = normalizarCorreo(correo);
    }

    public final void setNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Se necesita un nombre");
        this.nombre = nombre.trim();
    }

    public final void cambiarContrasena(String contrasena) {
        validarContrasena(contrasena);
        this.contrasenaHash = hasher().cifrar(contrasena);
    }

    public boolean autenticar(String contrasena) {
        if (contrasena == null || contrasenaHash == null) return false;
        return hasher().verificar(contrasena, contrasenaHash);
    }

    /** Tipo de usuario para mostrar y guardar: Invitado, UsuarioRegistrado o Administrador. */
    public String getTipo() {
        return getClass().getSimpleName();
    }

    public String getCorreo() { return correo; }
    public String getNombre() { return nombre; }
    public String getContrasenaHash() { return contrasenaHash; }

    @Override
    public String toString() {
        return nombre + " <" + correo + ">";
    }
}
