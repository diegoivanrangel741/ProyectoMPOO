package tienda.configuracion;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Lee valores de configuracion de variables de entorno o del archivo .env.
 * Si falta la "pimienta" (PEPPER) se genera una aleatoria y se guarda en .env para que la
 * aplicacion nunca se cierre por no encontrar el archivo (antes lanzaba ExceptionInInitializerError).
 */
public final class Configuracion {
    private static Dotenv env = cargar();

    private Configuracion() {
    }

    private static Dotenv cargar() {
        return Dotenv.configure().directory(directorio().toString()).ignoreIfMissing().ignoreIfMalformed().load();
    }

    /** Carpeta de datos: propiedad -Dtienda.datos o la carpeta de trabajo. */
    public static Path directorio() {
        String d = System.getProperty("tienda.datos");
        return (d == null || d.isBlank()) ? Path.of(".").toAbsolutePath().normalize() : Path.of(d).toAbsolutePath();
    }

    public static synchronized String obtener(String clave, String porDefecto) {
        String valor = System.getenv(clave);
        if (valor == null || valor.isBlank()) valor = env.get(clave, null);
        return (valor == null || valor.isBlank()) ? porDefecto : valor;
    }

    public static synchronized String obtenerObligatorio(String clave) {
        String valor = obtener(clave, null);
        if (valor == null) throw new IllegalStateException("Falta el valor de " + clave);
        return valor;
    }

    /** Devuelve PEPPER; si no existe la crea y la agrega a .env. */
    public static synchronized String obtenerPimienta() {
        String valor = obtener("PEPPER", null);
        if (valor != null) return valor;
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        valor = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        Path archivo = directorio().resolve(".env");
        try {
            Files.writeString(archivo, System.lineSeparator() + "PEPPER=" + valor + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.err.println("[Configuracion] No se encontro PEPPER; se genero una nueva en " + archivo);
        } catch (IOException e) {
            System.err.println("[Configuracion] No se pudo guardar PEPPER en .env: " + e.getMessage());
        }
        env = cargar();
        return valor;
    }
}
