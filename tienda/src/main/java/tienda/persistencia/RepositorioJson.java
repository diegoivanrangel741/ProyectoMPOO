package tienda.persistencia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import tienda.articulos.*;
import tienda.usuarios.LineaPedido;
import tienda.usuarios.Pedido;
import tienda.usuarios.UsuarioRegistrado;

/**
 * Persistencia en catalogo.json, usuarios.json y pedidos.json (RNF-02, seccion 12).
 * <ul>
 * <li>Si un archivo no existe se crea vacio y se continua (inicio limpio).</li>
 * <li>Si un archivo esta danado se respalda como *.danado-FECHA.json y se continua vacio, sin cerrar la app.</li>
 * <li>La escritura es atomica: se escribe un .tmp y se renombra, asi un fallo a media escritura no deja un JSON roto.</li>
 * <li>Se usa un candado por carpeta (hilos) y un FileLock (otros procesos) para escrituras simultaneas.</li>
 * </ul>
 */
public class RepositorioJson {
    public static final String CATALOGO = "catalogo.json";
    public static final String USUARIOS = "usuarios.json";
    public static final String PEDIDOS = "pedidos.json";
    public static final String CARPETA_IMAGENES = "imagenes";

    private static final Map<Path, ReentrantLock> CANDADOS = new ConcurrentHashMap<>();
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path carpeta;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final ReentrantLock candado;
    private final List<String> avisos = new ArrayList<>();

    public RepositorioJson() {
        this(Path.of("."));
    }

    public RepositorioJson(Path carpeta) {
        this.carpeta = carpeta.toAbsolutePath().normalize();
        this.candado = CANDADOS.computeIfAbsent(this.carpeta, k -> new ReentrantLock(true));
        try {
            Files.createDirectories(this.carpeta.resolve(CARPETA_IMAGENES));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta de datos " + this.carpeta, e);
        }
    }

    public Path getCarpeta() { return carpeta; }

    /** Mensajes de recuperacion (archivo faltante o danado) para mostrarlos al usuario. */
    public synchronized List<String> consumirAvisos() {
        List<String> copia = new ArrayList<>(avisos);
        avisos.clear();
        return copia;
    }

    private synchronized void avisar(String mensaje) {
        avisos.add(mensaje);
        System.err.println("[RepositorioJson] " + mensaje);
    }

    // ------------------------------------------------------------------ catalogo
    public List<Articulo> cargarCatalogo() {
        JsonObject raiz = leer(CATALOGO, "articulos");
        List<Articulo> lista = new ArrayList<>();
        for (JsonElement e : arreglo(raiz, "articulos")) {
            try {
                lista.add(articuloDesdeJson(e.getAsJsonObject()));
            } catch (RuntimeException ex) {
                avisar("Se omitio un articulo invalido de " + CATALOGO + ": " + ex.getMessage());
            }
        }
        return lista;
    }

    public void guardarCatalogo(List<Articulo> articulos) {
        guardarCatalogo(articulos, 0);
    }

    public void guardarCatalogo(List<Articulo> articulos, int siguienteId) {
        JsonObject raiz = new JsonObject();
        if (siguienteId > 0) raiz.addProperty("siguienteId", siguienteId);
        JsonArray arr = new JsonArray();
        for (Articulo a : articulos) arr.add(articuloAJson(a));
        raiz.add("articulos", arr);
        escribir(CATALOGO, raiz);
    }

    public int leerSiguienteId(String archivo) {
        JsonObject raiz = leer(archivo, null);
        return raiz.has("siguienteId") ? raiz.get("siguienteId").getAsInt() : 0;
    }

    // ------------------------------------------------------------------ usuarios
    /** Carga usuarios resolviendo su carrito contra el catalogo actual del disco. */
    public List<UsuarioRegistrado> cargarUsuarios() {
        return cargarUsuarios(cargarCatalogo());
    }

    /**
     * Carga usuarios y restaura su carrito con los articulos del catalogo dado.
     * Si un articulo del carrito ya no existe (p. ej. se borro catalogo.json) se omite.
     */
    public List<UsuarioRegistrado> cargarUsuarios(List<Articulo> catalogo) {
        Map<Integer, Articulo> porId = new HashMap<>();
        for (Articulo a : catalogo) porId.put(a.getId(), a);
        JsonObject raiz = leer(USUARIOS, "usuarios");
        List<UsuarioRegistrado> lista = new ArrayList<>();
        for (JsonElement e : arreglo(raiz, "usuarios")) {
            try {
                JsonObject o = e.getAsJsonObject();
                UsuarioRegistrado u = UsuarioRegistrado.desdeHash(o.get("id").getAsInt(), texto(o, "correo"),
                        o.has("nombre") ? texto(o, "nombre") : texto(o, "correo"), texto(o, "contrasenaHash"));
                if (o.has("saldoWallet")) u.setSaldoWallet(o.get("saldoWallet").getAsDouble());
                for (JsonElement c : arreglo(o, "carrito")) {
                    JsonObject linea = c.getAsJsonObject();
                    Articulo a = porId.get(linea.get("articuloId").getAsInt());
                    int cantidad = linea.has("cantidad") ? linea.get("cantidad").getAsInt() : 1;
                    if (a == null) continue;
                    for (int i = 0; i < cantidad; i++) u.getCarrito().restaurar(a);
                }
                lista.add(u);
            } catch (RuntimeException ex) {
                avisar("Se omitio un usuario invalido de " + USUARIOS + ": " + ex.getMessage());
            }
        }
        return lista;
    }

    public void guardarUsuarios(List<UsuarioRegistrado> usuarios) {
        guardarUsuarios(usuarios, 0);
    }

    public void guardarUsuarios(List<UsuarioRegistrado> usuarios, int siguienteId) {
        JsonObject raiz = new JsonObject();
        if (siguienteId > 0) raiz.addProperty("siguienteId", siguienteId);
        JsonArray arr = new JsonArray();
        for (UsuarioRegistrado u : usuarios) {
            JsonObject o = new JsonObject();
            o.addProperty("id", u.getId());
            o.addProperty("tipo", "UsuarioRegistrado");
            o.addProperty("correo", u.getCorreo());
            o.addProperty("nombre", u.getNombre());
            o.addProperty("contrasenaHash", u.getContrasenaHash());
            o.addProperty("saldoWallet", u.getSaldoWallet());
            JsonArray carrito = new JsonArray();
            for (Map.Entry<Articulo, Integer> en : u.getCarrito().agrupar().entrySet()) {
                JsonObject l = new JsonObject();
                l.addProperty("articuloId", en.getKey().getId());
                l.addProperty("cantidad", en.getValue());
                carrito.add(l);
            }
            o.add("carrito", carrito);
            arr.add(o);
        }
        raiz.add("usuarios", arr);
        escribir(USUARIOS, raiz);
    }

    // ------------------------------------------------------------------ pedidos
    public List<Pedido> cargarPedidos() {
        JsonObject raiz = leer(PEDIDOS, "pedidos");
        List<Pedido> lista = new ArrayList<>();
        for (JsonElement e : arreglo(raiz, "pedidos")) {
            try {
                JsonObject o = e.getAsJsonObject();
                List<LineaPedido> lineas = new ArrayList<>();
                for (JsonElement l : arreglo(o, "lineas")) {
                    JsonObject lo = l.getAsJsonObject();
                    lineas.add(new LineaPedido(texto(lo, "nombre"), lo.get("precioUnitario").getAsDouble(),
                            lo.get("cantidad").getAsInt()));
                }
                lista.add(new Pedido(o.get("id").getAsInt(), o.get("usuarioId").getAsInt(),
                        o.has("correoUsuario") ? texto(o, "correoUsuario") : "",
                        LocalDateTime.parse(texto(o, "fecha")), o.get("total").getAsDouble(),
                        o.has("metodoPago") ? texto(o, "metodoPago") : "", lineas));
            } catch (RuntimeException ex) {
                avisar("Se omitio un pedido invalido de " + PEDIDOS + ": " + ex.getMessage());
            }
        }
        return lista;
    }

    public void guardarPedidos(List<Pedido> pedidos) {
        JsonObject raiz = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Pedido p : pedidos) {
            JsonObject o = new JsonObject();
            o.addProperty("id", p.getId());
            o.addProperty("usuarioId", p.getUsuarioId());
            o.addProperty("correoUsuario", p.getCorreoUsuario());
            o.addProperty("fecha", p.getFecha().toString());
            o.addProperty("total", p.getTotal());
            o.addProperty("metodoPago", p.getMetodoPago());
            JsonArray lineas = new JsonArray();
            for (LineaPedido l : p.getLineas()) {
                JsonObject lo = new JsonObject();
                lo.addProperty("nombre", l.getNombre());
                lo.addProperty("precioUnitario", l.getPrecioUnitario());
                lo.addProperty("cantidad", l.getCantidad());
                lineas.add(lo);
            }
            o.add("lineas", lineas);
            arr.add(o);
        }
        raiz.add("pedidos", arr);
        escribir(PEDIDOS, raiz);
    }

    // ------------------------------------------------------------------ imagenes
    /**
     * RF-12: copia la imagen elegida a la carpeta imagenes/ del proyecto y devuelve la ruta relativa.
     * Si origen es null devuelve la imagen generica.
     */
    public String copiarImagen(Path origen, int idArticulo) {
        if (origen == null) return Articulo.IMAGEN_GENERICA;
        if (!Files.isRegularFile(origen)) throw new IllegalArgumentException("No existe la imagen: " + origen);
        String nombre = origen.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        String destinoRel = CARPETA_IMAGENES + "/" + idArticulo + "-" + nombre;
        Path destino = carpeta.resolve(destinoRel);
        try {
            if (!origen.toAbsolutePath().normalize().equals(destino.toAbsolutePath().normalize()))
                Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo copiar la imagen", e);
        }
        return destinoRel;
    }

    /**
     * Seccion 12: si falta la imagen se usa la generica; si tampoco existe devuelve null
     * (la interfaz muestra el nombre del producto en texto plano).
     */
    public Path resolverImagen(String rutaRelativa) {
        Path p = rutaRelativa == null ? null : carpeta.resolve(rutaRelativa);
        if (p != null && Files.isRegularFile(p)) return p;
        Path generica = carpeta.resolve(Articulo.IMAGEN_GENERICA);
        return Files.isRegularFile(generica) ? generica : null;
    }

    // ------------------------------------------------------------------ utilerias de archivo
    private JsonObject leer(String archivo, String clave) {
        Path ruta = carpeta.resolve(archivo);
        candado.lock();
        try {
            if (!Files.exists(ruta)) {
                JsonObject vacio = vacio(clave);
                if (clave != null) {
                    escribirSinCandado(archivo, vacio);
                }
                return vacio;
            }
            String contenido = Files.readString(ruta, StandardCharsets.UTF_8);
            if (contenido.isBlank()) throw new JsonParseException("archivo vacio");
            JsonElement e = JsonParser.parseString(contenido);
            if (!e.isJsonObject()) throw new JsonParseException("la raiz no es un objeto JSON");
            JsonObject o = e.getAsJsonObject();
            if (clave != null && o.has(clave) && !o.get(clave).isJsonArray())
                throw new JsonParseException("\"" + clave + "\" no es un arreglo");
            return o;
        } catch (IOException | JsonParseException | IllegalStateException ex) {
            if (clave == null) return new JsonObject();
            Path respaldo = carpeta.resolve(archivo.replace(".json", "") + ".danado-" + LocalDateTime.now().format(SELLO) + ".json");
            try {
                Files.move(ruta, respaldo, StandardCopyOption.REPLACE_EXISTING);
                avisar(archivo + " estaba danado (" + ex.getMessage() + "). Se respaldo como "
                        + respaldo.getFileName() + " y se inicio vacio.");
            } catch (IOException mv) {
                avisar(archivo + " estaba danado y no se pudo respaldar: " + mv.getMessage());
            }
            JsonObject vacio = vacio(clave);
            escribirSinCandado(archivo, vacio);
            return vacio;
        } finally {
            candado.unlock();
        }
    }

    private void escribir(String archivo, JsonObject raiz) {
        candado.lock();
        try {
            escribirSinCandado(archivo, raiz);
        } finally {
            candado.unlock();
        }
    }

    /** Escritura atomica protegida tambien entre procesos con un FileLock. */
    private void escribirSinCandado(String archivo, JsonObject raiz) {
        Path destino = carpeta.resolve(archivo);
        Path tmp = carpeta.resolve(archivo + "." + java.util.UUID.randomUUID() + ".tmp");
        try (FileChannel canal = FileChannel.open(carpeta.resolve(".tienda.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock bloqueo = canal.lock()) {
            Files.writeString(tmp, gson.toJson(raiz), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignorada) { }
            throw new UncheckedIOException("No se pudo guardar " + archivo + ": " + e.getMessage(), e);
        }
    }

    private static JsonObject vacio(String clave) {
        JsonObject o = new JsonObject();
        if (clave != null) o.add(clave, new JsonArray());
        return o;
    }

    private static JsonArray arreglo(JsonObject o, String clave) {
        return (o.has(clave) && o.get(clave).isJsonArray()) ? o.getAsJsonArray(clave) : new JsonArray();
    }

    private static String texto(JsonObject o, String clave) {
        if (!o.has(clave) || o.get(clave).isJsonNull()) throw new JsonParseException("falta \"" + clave + "\"");
        return o.get(clave).getAsString();
    }

    // ------------------------------------------------------------------ conversion polimorfica
    static JsonObject articuloAJson(Articulo a) {
        JsonObject o = new JsonObject();
        o.addProperty("id", a.getId());
        o.addProperty("tipo", a.getTipo());
        o.addProperty("categoria", a.getCategoria());
        o.addProperty("nombre", a.getNombre());
        o.addProperty("precio", a.getPrecio());
        o.addProperty("stock", a.getStock());
        o.addProperty("imagen", a.getImagen());
        if (a instanceof Tecnologia) o.addProperty("garantiaMeses", ((Tecnologia) a).getGarantiaMeses());
        if (a instanceof Computadora) {
            o.addProperty("procesador", ((Computadora) a).getProcesador());
            o.addProperty("ramGb", ((Computadora) a).getRamGb());
        } else if (a instanceof Laptop) {
            o.addProperty("pesoKg", ((Laptop) a).getPesoKg());
            o.addProperty("bateriaHoras", ((Laptop) a).getBateriaHoras());
        } else if (a instanceof Celular) {
            o.addProperty("almacenamientoGb", ((Celular) a).getAlmacenamientoGb());
            o.addProperty("sistemaOperativo", ((Celular) a).getSistemaOperativo());
        } else if (a instanceof Televisor) {
            o.addProperty("pulgadas", ((Televisor) a).getPulgadas());
            o.addProperty("resolucion", ((Televisor) a).getResolucion());
        } else if (a instanceof Alimentos) {
            o.addProperty("fechaCaducidad", ((Alimentos) a).getFechaCaducidad().toString());
            o.addProperty("perecedero", ((Alimentos) a).isPerecedero());
        } else if (a instanceof Moda) {
            o.addProperty("talla", ((Moda) a).getTalla().name());
            o.addProperty("color", ((Moda) a).getColor());
            o.addProperty("enTemporada", ((Moda) a).isEnTemporada());
        }
        return o;
    }

    static Articulo articuloDesdeJson(JsonObject o) {
        int id = o.get("id").getAsInt();
        String tipo = texto(o, "tipo");
        String nombre = texto(o, "nombre");
        double precio = o.get("precio").getAsDouble();
        int stock = o.get("stock").getAsInt();
        String imagen = o.has("imagen") && !o.get("imagen").isJsonNull() ? o.get("imagen").getAsString() : null;
        int garantia = o.has("garantiaMeses") ? o.get("garantiaMeses").getAsInt() : 0;
        switch (tipo) {
            case "Computadora":
                return new Computadora(id, nombre, precio, stock, imagen, garantia, texto(o, "procesador"), o.get("ramGb").getAsInt());
            case "Laptop":
                return new Laptop(id, nombre, precio, stock, imagen, garantia, o.get("pesoKg").getAsDouble(), o.get("bateriaHoras").getAsInt());
            case "Celular":
                return new Celular(id, nombre, precio, stock, imagen, garantia, o.get("almacenamientoGb").getAsInt(), texto(o, "sistemaOperativo"));
            case "Televisor":
            case "Television":
                return new Televisor(id, nombre, precio, stock, imagen, garantia, o.get("pulgadas").getAsInt(), texto(o, "resolucion"));
            case "Abarrotes":
                return new Abarrotes(id, nombre, precio, stock, imagen, LocalDate.parse(texto(o, "fechaCaducidad")));
            case "Lacteos":
                return new Lacteos(id, nombre, precio, stock, imagen, LocalDate.parse(texto(o, "fechaCaducidad")));
            case "Salchichoneria":
                return new Salchichoneria(id, nombre, precio, stock, imagen, LocalDate.parse(texto(o, "fechaCaducidad")));
            case "Frutas":
                return new Frutas(id, nombre, precio, stock, imagen, LocalDate.parse(texto(o, "fechaCaducidad")));
            case "Verduras":
                return new Verduras(id, nombre, precio, stock, imagen, LocalDate.parse(texto(o, "fechaCaducidad")));
            case "Hombre":
                return new Hombre(id, nombre, precio, stock, imagen, Talla.desdeTexto(texto(o, "talla")), texto(o, "color"),
                        o.has("enTemporada") && o.get("enTemporada").getAsBoolean());
            case "Mujer":
                return new Mujer(id, nombre, precio, stock, imagen, Talla.desdeTexto(texto(o, "talla")), texto(o, "color"),
                        o.has("enTemporada") && o.get("enTemporada").getAsBoolean());
            default:
                throw new JsonParseException("tipo de articulo desconocido: " + tipo);
        }
    }
}
