package tienda.articulos;

/** Tallas permitidas para Moda: XS a XL. */
public enum Talla {
    XS, S, M, L, XL;

    /** Convierte texto ("m", " XL ") a Talla; lanza IllegalArgumentException si no es XS..XL. */
    public static Talla desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("Se necesita una talla (XS a XL)");
        try {
            return Talla.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Talla no valida: " + texto + " (usa XS, S, M, L o XL)");
        }
    }
}
