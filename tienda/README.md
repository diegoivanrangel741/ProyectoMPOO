# Simulador de Tienda en Linea (MPOO 2027-1)

Aplicacion de escritorio en Java 17 + Swing con persistencia en archivos JSON.

## Ejecutar
```
mvn compile exec:java        # abre la ventana de acceso
mvn test                     # 47 pruebas automatizadas (JUnit 5)
```
En NetBeans/IntelliJ: abrir la carpeta como proyecto Maven y ejecutar `tienda.ui.Main`.

* Los datos se guardan en la carpeta desde donde se ejecuta (o en `-Dtienda.datos=ruta`):
  `catalogo.json`, `usuarios.json`, `pedidos.json` e `imagenes/`.
* Administrador inicial: `admin@todopoderso.com` / `admin123`.
* `.env` contiene `PEPPER` (pimienta para las contrasenas). Si no existe se genera automaticamente.
  No se sube al repositorio.

## Paquetes
| Paquete | Contenido |
|---|---|
| `articulos` | Articulo y sus 3 categorias / 11 subcategorias (herencia y precio polimorfico) |
| `usuarios` | Usuario, Invitado, UsuarioRegistrado, Administrador, Carrito, Pedido, LineaPedido |
| `pagos` | Interfaz Pago, PagoTarjeta, PagoTransferencia, PagoWallet, Marca, Notificador |
| `excepciones` | 5 excepciones propias |
| `persistencia` | RepositorioJson (lectura/escritura atomica, recuperacion de archivos danados) |
| `servicio` | Tienda: fachada de la logica, segura para uso simultaneo |
| `seguridad`, `configuracion` | HMAC + BCrypt, lectura de `.env` |
| `ui` | Interfaz Swing (unico paquete que usa Swing) |

Reparto original: A = articulos y excepciones | B = usuarios y carrito | C = pagos, pedidos y JSON | D = Swing e imagenes.
