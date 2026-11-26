package tienda.servicio;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import tienda.articulos.Alimentos;
import tienda.articulos.Articulo;
import tienda.excepciones.ContrasenaIncorrectaException;
import tienda.excepciones.CorreoDuplicadoException;
import tienda.excepciones.PagoRechazadoException;
import tienda.excepciones.StockInsuficienteException;
import tienda.excepciones.TarjetaInvalidaException;
import tienda.pagos.Pago;
import tienda.pagos.PagoWallet;
import tienda.persistencia.RepositorioJson;
import tienda.usuarios.Administrador;
import tienda.usuarios.Carrito;
import tienda.usuarios.Invitado;
import tienda.usuarios.LineaPedido;
import tienda.usuarios.Pedido;
import tienda.usuarios.Usuario;
import tienda.usuarios.UsuarioRegistrado;

/**
 * Fachada de la logica de negocio (no depende de Swing, RNF-05).
 * Concentra catalogo, usuarios y pedidos y es segura para uso simultaneo: todas las operaciones que
 * cambian datos se ejecutan bajo un mismo candado, de modo que dos compras simultaneas nunca venden
 * mas unidades de las que hay, y dos registros simultaneos con el mismo correo no crean dos cuentas.
 * Cada cambio se guarda de inmediato en el JSON correspondiente (persistencia tras un fallo).
 */
public class Tienda {
    public static final String[] CATEGORIAS = {"Tecnologia", "Alimentos", "Moda"};
    public static final Map<String, String[]> SUBCATEGORIAS = new LinkedHashMap<>();

    static {
        SUBCATEGORIAS.put("Tecnologia", new String[]{"Computadora", "Laptop", "Celular", "Televisor"});
        SUBCATEGORIAS.put("Alimentos", new String[]{"Abarrotes", "Lacteos", "Salchichoneria", "Frutas", "Verduras"});
        SUBCATEGORIAS.put("Moda", new String[]{"Hombre", "Mujer"});
    }

    private final Object candado = new Object();
    private final RepositorioJson repo;
    private final List<Articulo> catalogo;
    private final List<UsuarioRegistrado> usuarios;
    private final List<Pedido> pedidos;
    private final Administrador administrador;
    private final AtomicLong version = new AtomicLong();
    private final List<Runnable> oyentes = new CopyOnWriteArrayList<>();
    private int siguienteIdArticulo;
    private int siguienteIdUsuario;
    private int siguienteIdPedido;

    public Tienda(Path carpetaDatos) {
        this(new RepositorioJson(carpetaDatos));
    }

    public Tienda(RepositorioJson repo) {
        this.repo = repo;
        this.catalogo = new ArrayList<>(repo.cargarCatalogo());
        this.usuarios = new ArrayList<>(repo.cargarUsuarios(catalogo));
        this.pedidos = new ArrayList<>(repo.cargarPedidos());
        // Los ids nunca se reutilizan: si se reusara el id de un usuario borrado heredaria su historial.
        int maxArt = Math.max(0, repo.leerSiguienteId(RepositorioJson.CATALOGO) - 1);
        for (Articulo a : catalogo) maxArt = Math.max(maxArt, a.getId());
        int maxUsr = Math.max(0, repo.leerSiguienteId(RepositorioJson.USUARIOS) - 1);
        for (UsuarioRegistrado u : usuarios) maxUsr = Math.max(maxUsr, u.getId());
        int maxPed = 0;
        for (Pedido p : pedidos) {
            maxPed = Math.max(maxPed, p.getId());
            maxUsr = Math.max(maxUsr, p.getUsuarioId());
        }
        siguienteIdArticulo = maxArt + 1;
        siguienteIdUsuario = maxUsr + 1;
        siguienteIdPedido = maxPed + 1;
        for (UsuarioRegistrado u : usuarios) {
            for (Pedido p : pedidos) if (p.getUsuarioId() == u.getId()) u.agregarPedido(p);
        }
        this.administrador = new Administrador(Administrador.CORREO, Administrador.CONTRASENA_INICIAL, "Administrador", this);
    }

    public RepositorioJson getRepositorio() { return repo; }
    public Administrador getAdministrador() { return administrador; }

    // ================================================================== observadores (la UI se refresca)
    public void agregarOyente(Runnable r) { oyentes.add(r); }
    public void quitarOyente(Runnable r) { oyentes.remove(r); }
    public long getVersion() { return version.get(); }

    private void cambio() {
        version.incrementAndGet();
        for (Runnable r : oyentes) {
            try { r.run(); } catch (RuntimeException ignorada) { }
        }
    }

    // ================================================================== catalogo (RF-01)
    public List<Articulo> getCatalogo() {
        synchronized (candado) { return new ArrayList<>(catalogo); }
    }

    public Articulo buscarArticulo(int id) {
        synchronized (candado) {
            for (Articulo a : catalogo) if (a.getId() == id) return a;
            return null;
        }
    }

    /** CA-01: subcategorias de una categoria y sus articulos (incluye subcategorias vacias). */
    public Map<String, List<Articulo>> consultarCategoria(String categoria) {
        String[] subs = SUBCATEGORIAS.get(categoria);
        if (subs == null) throw new IllegalArgumentException("Categoria desconocida: " + categoria);
        Map<String, List<Articulo>> r = new LinkedHashMap<>();
        for (String s : subs) r.put(s, new ArrayList<>());
        synchronized (candado) {
            for (Articulo a : catalogo) if (a.getCategoria().equals(categoria)) r.get(a.getTipo()).add(a);
        }
        return r;
    }

    public int reservarIdArticulo() {
        synchronized (candado) { return siguienteIdArticulo++; }
    }

    // ================================================================== usuarios (RF-02, RF-03)
    /** CU-01. El hash (lento a proposito) se calcula fuera del candado para no frenar a los demas. */
    public UsuarioRegistrado registrar(String correo, String contrasena, String nombre) throws CorreoDuplicadoException {
        if (!Usuario.correoValido(correo)) throw new IllegalArgumentException("El correo no tiene un formato valido");
        Usuario.validarContrasena(contrasena);
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Se necesita un nombre");
        String normal = Usuario.normalizarCorreo(correo);
        synchronized (candado) {
            if (correoOcupado(normal)) throw new CorreoDuplicadoException("El correo " + normal + " ya esta registrado");
        }
        UsuarioRegistrado candidato = new UsuarioRegistrado(1, normal, contrasena, nombre);
        synchronized (candado) {
            if (correoOcupado(normal)) throw new CorreoDuplicadoException("El correo " + normal + " ya esta registrado");
            UsuarioRegistrado nuevo = UsuarioRegistrado.desdeHash(siguienteIdUsuario++, normal, candidato.getNombre(),
                    candidato.getContrasenaHash());
            usuarios.add(nuevo);
            guardarUsuarios();
            return nuevo;
        }
    }

    private boolean correoOcupado(String correoNormal) {
        if (Administrador.CORREO.equals(correoNormal)) return true;
        for (UsuarioRegistrado u : usuarios) if (u.getCorreo().equals(correoNormal)) return true;
        return false;
    }

    /** Devuelve Administrador o UsuarioRegistrado. Mismo mensaje para correo o contrasena incorrectos. */
    public Usuario iniciarSesion(String correo, String contrasena) throws ContrasenaIncorrectaException {
        String normal = Usuario.normalizarCorreo(correo);
        Usuario encontrado = null;
        synchronized (candado) {
            if (Administrador.CORREO.equals(normal)) encontrado = administrador;
            else for (UsuarioRegistrado u : usuarios) if (u.getCorreo().equals(normal)) encontrado = u;
        }
        if (encontrado == null || !encontrado.autenticar(contrasena))
            throw new ContrasenaIncorrectaException("Correo o contrasena incorrectos");
        return encontrado;
    }

    public Invitado entrarComoInvitado() { return new Invitado(); }

    /** RF-06: registrado -> se guarda su carrito; invitado -> su carrito se vacia y nunca toca el disco. */
    public void cerrarSesion(Usuario u) {
        if (u instanceof Invitado) {
            ((Invitado) u).cerrarSesion();
        } else if (u instanceof UsuarioRegistrado) {
            synchronized (candado) { if (usuarios.contains(u)) guardarUsuarios(); }
        }
    }

    public boolean sigueActivo(Usuario u) {
        if (!(u instanceof UsuarioRegistrado)) return true;
        synchronized (candado) { return usuarios.contains(u); }
    }

    public List<UsuarioRegistrado> getUsuarios() {
        synchronized (candado) { return new ArrayList<>(usuarios); }
    }

    /** CU-04 / RF-04. Contrasena incorrecta -> la cuenta no se toca (CA-06). Los pedidos se conservan. */
    public void eliminarCuenta(UsuarioRegistrado u, String contrasena) throws ContrasenaIncorrectaException {
        if (!u.autenticar(contrasena))
            throw new ContrasenaIncorrectaException("La contrasena no coincide; no se elimino la cuenta");
        synchronized (candado) {
            u.eliminarCuenta(contrasena);
            usuarios.remove(u);
            guardarUsuarios();
        }
        cambio();
    }

    // ================================================================== carrito (RF-05, RF-06)
    public static Carrito carritoDe(Usuario u) {
        if (u instanceof UsuarioRegistrado) return ((UsuarioRegistrado) u).getCarrito();
        if (u instanceof Invitado) return ((Invitado) u).getCarrito();
        throw new IllegalArgumentException("El administrador no tiene carrito");
    }

    /** CU-02. Lanza StockInsuficienteException si no hay stock (flujo alterno). */
    public void agregarAlCarrito(Usuario u, int articuloId) throws StockInsuficienteException {
        synchronized (candado) {
            verificarActivo(u);
            Articulo a = buscarArticulo(articuloId);
            if (a == null) throw new IllegalArgumentException("El articulo ya no existe en el catalogo");
            Carrito c = carritoDe(u);
            if (a instanceof Alimentos && ((Alimentos) a).estaCaducado())
                throw new StockInsuficienteException("\"" + a.getNombre() + "\" esta caducado y no esta disponible");
            if (c.contar(a.getId()) >= a.getStock())
                throw new StockInsuficienteException(a.getStock() == 0
                        ? "\"" + a.getNombre() + "\" esta agotado"
                        : "Solo hay " + a.getStock() + " unidad(es) de \"" + a.getNombre() + "\" y ya estan en tu carrito");
            c.agregar(a); // valida el limite de 15
            if (u instanceof UsuarioRegistrado) guardarUsuarios();
        }
    }

    public void quitarDelCarrito(Usuario u, int articuloId, boolean todos) {
        synchronized (candado) {
            Carrito c = carritoDe(u);
            if (todos) c.quitarTodos(articuloId); else c.quitar(articuloId);
            if (u instanceof UsuarioRegistrado && usuarios.contains(u)) guardarUsuarios();
        }
    }

    /**
     * Nota 14: valida disponibilidad al abrir el carrito y antes de pagar.
     * Devuelve id -> motivo para cada articulo que ya no se puede comprar.
     */
    public Map<Integer, String> validarCarrito(Usuario u) {
        Map<Integer, String> problemas = new LinkedHashMap<>();
        synchronized (candado) {
            for (Map.Entry<Articulo, Integer> e : carritoDe(u).agrupar().entrySet()) {
                Articulo a = e.getKey();
                Articulo actual = buscarArticulo(a.getId());
                if (actual == null) problemas.put(a.getId(), "Ya no existe en el catalogo");
                else if (actual instanceof Alimentos && ((Alimentos) actual).estaCaducado())
                    problemas.put(a.getId(), "Producto caducado");
                else if (actual.getStock() == 0) problemas.put(a.getId(), "Agotado");
                else if (actual.getStock() < e.getValue())
                    problemas.put(a.getId(), "Solo quedan " + actual.getStock() + " unidad(es)");
            }
        }
        return problemas;
    }

    // ================================================================== pago (RF-07..RF-11, CU-03)
    /**
     * CU-03. Todo o nada: valida stock de todas las lineas, cobra, descuenta inventario, registra el pedido,
     * vacia el carrito y guarda los tres JSON. Bajo el candado, dos compras simultaneas no pueden
     * vender la misma ultima unidad.
     */
    public Pedido pagar(UsuarioRegistrado u, Pago pago)
            throws StockInsuficienteException, TarjetaInvalidaException, PagoRechazadoException {
        if (pago == null) throw new IllegalArgumentException("Selecciona un metodo de pago");
        synchronized (candado) {
            verificarActivo(u);
            Map<Articulo, Integer> grupos = u.getCarrito().agrupar();
            if (grupos.isEmpty()) throw new IllegalStateException("El carrito esta vacio");
            Map<Integer, String> problemas = validarCarrito(u);
            if (!problemas.isEmpty()) {
                StringBuilder sb = new StringBuilder("No se puede pagar:");
                for (Map.Entry<Integer, String> p : problemas.entrySet()) {
                    Articulo a = buscarArticulo(p.getKey());
                    sb.append("\n - ").append(a == null ? "Articulo #" + p.getKey() : a.getNombre()).append(": ").append(p.getValue());
                }
                throw new StockInsuficienteException(sb.toString());
            }
            List<LineaPedido> lineas = new ArrayList<>();
            double total = 0;
            for (Map.Entry<Articulo, Integer> e : grupos.entrySet()) {
                double subtotal = e.getKey().getPrecioFinal(e.getValue());
                total += subtotal;
                lineas.add(new LineaPedido(e.getKey().getNombre(),
                        Math.round(subtotal / e.getValue() * 100.0) / 100.0, e.getValue()));
            }
            total = Math.round(total * 100.0) / 100.0;
            if (!pago.procesar(total)) throw new PagoRechazadoException("El pago no fue autorizado");
            for (Map.Entry<Articulo, Integer> e : grupos.entrySet()) e.getKey().descontarStock(e.getValue());
            if (pago instanceof PagoWallet) u.setSaldoWallet(((PagoWallet) pago).getSaldo());
            Pedido pedido = new Pedido(siguienteIdPedido++, u.getId(), u.getCorreo(), LocalDateTime.now(), total,
                    pago.getDescripcion(), lineas);
            pedidos.add(pedido);
            u.agregarPedido(pedido);
            u.getCarrito().vaciar();
            repo.guardarPedidos(pedidos);
            guardarCatalogo();
            guardarUsuarios();
            cambio();
            return pedido;
        }
    }

    public List<Pedido> getPedidos() {
        synchronized (candado) { return new ArrayList<>(pedidos); }
    }

    public List<Pedido> getHistorial(UsuarioRegistrado u) {
        synchronized (candado) { return new ArrayList<>(u.getHistorial()); }
    }

    // ================================================================== administracion (RF-12, RF-13)
    public void agregarProducto(Articulo a) throws CorreoDuplicadoException {
        if (a == null) throw new IllegalArgumentException("Producto nulo");
        synchronized (candado) {
            for (Articulo x : catalogo) {
                if (x.getId() == a.getId()) throw new CorreoDuplicadoException("Ya existe un producto con el id " + a.getId());
                if (mismoProducto(x, a)) throw new CorreoDuplicadoException("El producto \"" + a.getNombre() + "\" ya existe");
            }
            catalogo.add(a);
            siguienteIdArticulo = Math.max(siguienteIdArticulo, a.getId() + 1);
            guardarCatalogo();
        }
        cambio();
    }

    /** Reemplaza el producto con el mismo id y actualiza las referencias en los carritos. */
    public void editarProducto(Articulo editado) throws CorreoDuplicadoException {
        synchronized (candado) {
            int pos = -1;
            for (int i = 0; i < catalogo.size(); i++) {
                Articulo x = catalogo.get(i);
                if (x.getId() == editado.getId()) pos = i;
                else if (mismoProducto(x, editado))
                    throw new CorreoDuplicadoException("Ya existe otro producto \"" + editado.getNombre() + "\"");
            }
            if (pos < 0) throw new IllegalArgumentException("El producto #" + editado.getId() + " ya no existe");
            catalogo.set(pos, editado);
            for (UsuarioRegistrado u : usuarios) u.getCarrito().reemplazar(editado);
            guardarCatalogo();
            guardarUsuarios();
        }
        cambio();
    }

    private static boolean mismoProducto(Articulo x, Articulo a) {
        return x.getNombre().equalsIgnoreCase(a.getNombre()) && x.getTipo().equals(a.getTipo());
    }

    public void eliminarProducto(int id) {
        synchronized (candado) {
            if (!catalogo.removeIf(a -> a.getId() == id))
                throw new IllegalArgumentException("El producto #" + id + " no existe");
            for (UsuarioRegistrado u : usuarios) u.getCarrito().quitarTodos(id);
            guardarCatalogo();
            guardarUsuarios();
        }
        cambio();
    }

    /** RN-04: borra cuenta y carrito; pedidos.json no se toca. */
    public void eliminarUsuario(int id) {
        synchronized (candado) {
            UsuarioRegistrado u = null;
            for (UsuarioRegistrado x : usuarios) if (x.getId() == id) u = x;
            if (u == null) throw new IllegalArgumentException("El usuario #" + id + " no existe");
            u.getCarrito().vaciar();
            usuarios.remove(u);
            guardarUsuarios();
        }
        cambio();
    }

    public String importarImagen(Path origen, int idArticulo) {
        return repo.copiarImagen(origen, idArticulo);
    }

    public Path resolverImagen(Articulo a) {
        return repo.resolverImagen(a == null ? null : a.getImagen());
    }

    // ================================================================== persistencia
    /** Cierre de la app o recuperacion tras un fallo: guarda todo. */
    public void guardarTodo() {
        synchronized (candado) {
            guardarCatalogo();
            guardarUsuarios();
            repo.guardarPedidos(pedidos);
        }
    }

    private void guardarCatalogo() { repo.guardarCatalogo(catalogo, siguienteIdArticulo); }
    private void guardarUsuarios() { repo.guardarUsuarios(usuarios, siguienteIdUsuario); }

    private void verificarActivo(Usuario u) {
        if (u instanceof Administrador) throw new IllegalStateException("El administrador no compra");
        if (u instanceof UsuarioRegistrado && !usuarios.contains(u))
            throw new IllegalStateException("Esta cuenta fue eliminada; inicia sesion de nuevo");
    }

    public List<String> consumirAvisos() { return Collections.unmodifiableList(repo.consumirAvisos()); }
}
