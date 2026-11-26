package tienda.seguridad;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RNF-04: las contrasenas nunca se guardan en texto plano.
 * Se aplica HMAC-SHA256 con una "pimienta" secreta (.env) y despues BCrypt con sal aleatoria.
 */
public final class HashContrasena {
    public static final int COSTO = 12;
    private static final String ALGORITMO_HMAC = "HmacSHA256";
    private final byte[] pimienta;

    public HashContrasena(String pimienta) {
        if (pimienta == null || pimienta.isBlank()) throw new IllegalArgumentException("La pimienta no puede estar vacia");
        this.pimienta = pimienta.getBytes(StandardCharsets.UTF_8);
    }

    public String cifrar(String contrasena) {
        if (contrasena == null) throw new IllegalArgumentException("La contrasena no puede ser nula");
        return BCrypt.withDefaults().hashToString(COSTO, preparar(contrasena));
    }

    public boolean verificar(String contrasena, String hash) {
        if (contrasena == null || hash == null || hash.isBlank()) return false;
        try {
            return BCrypt.verifyer().verify(preparar(contrasena), hash.toCharArray()).verified;
        } catch (RuntimeException e) {
            return false; // hash danado en el JSON: se trata como contrasena incorrecta
        }
    }

    private char[] preparar(String contrasena) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO_HMAC);
            mac.init(new SecretKeySpec(pimienta, ALGORITMO_HMAC));
            byte[] digest = mac.doFinal(contrasena.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString().toCharArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se puede generar el cifrado", e);
        }
    }
}
