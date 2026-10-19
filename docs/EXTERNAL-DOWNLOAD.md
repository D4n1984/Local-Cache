# Descargar directamente desde una fuente de Stremio

Prototipo `0.5.9-d4n.2`, Android 11 / ARM 32 bits. El receptor se instala con el mismo
paquete del fork, sin una segunda app, sin permisos generales de almacenamiento y sin
modificar Torrentio. Conserva la selección USB y los parciales anteriores.

## Uso

1. En Stremio, selecciona la fuente **Torrentio RD** que te interese.
2. Usa la opción de reproductor externo y selecciona **Local Cache · Descargar al USB**
   en el perfil **PERSONAL**, con **Solo una vez**.
3. Comprueba el título y confirma **Descargar al USB** en nuestra pantalla.
4. Se muestra porcentaje, bytes descargados/total, velocidad y estado, actualizados cada
   segundo. No se reproduce el archivo. Si hay otra descarga, queda en cola.
5. Puedes volver a Stremio o Home: el servicio mantiene la descarga. Para volver a ver el
   progreso, abre **Local Cache → Descargas USB / progreso**; la notificación también abre
   esta pantalla cuando el sistema la muestra.
6. **Pausar (conservar parcial)** no elimina lo descargado; **Iniciar / reanudar** utiliza el
   parcial y los validadores privados. El servidor debe admitir rangos y el enlace seguir válido.

En Stremio TV 1.11.2 se ha observado el ajuste **Playback → Default Player** con opciones
Exo, VLC, MPV y External; no hay «Preguntar siempre». External permite seleccionar nuestro
destino o un reproductor externo instalado. Cambiar este predeterminado afecta a las demás
reproducciones: no equivale a añadir un botón nativo «Descargar» junto a Exo dentro de Stremio.
No elegir «Siempre» en el selector Android si se quiere conservar la elección de aplicaciones.

El addon Local Cache no es necesario para esta ruta. El servidor de la app se mantiene por
compatibilidad con el servicio existente, pero la descarga externa no consulta sus manifests.
El receptor no necesita otra copia de la clave RD: recibe el enlace resuelto que entrega Stremio.

## Seguridad y límites

- `ACTION_VIEW` solo HTTP(S) de tipo vídeo u octet-stream. No magnet, file, content ni scripts.
- La actividad es pública para poder recibir enlaces; el remitente y sus extras no son de
  confianza. Un enlace recibido **nunca** inicia una descarga sin confirmación visible.
- Identidad SHA-256 del enlace; ni nombres ni rutas externas determinan el archivo de destino.
- Título saneado, sin URLs ni controles bidireccionales; el enlace se guarda solo en datos
  privados al comenzar la descarga, nunca en metadata USB ni en diagnóstico.
- No se registran valores arbitrarios de extras. Diagnóstico de recepción: fecha, esquema,
  MIME y nombres acotados de extras, no su contenido ni la URL.
- Enlaces RD caducados no se renuevan automáticamente. Un enlace nuevo puede tener otra
  identidad; no se mezclan por nombre dos archivos que no se han verificado como iguales.
- No admite HLS/DASH, DRM ni torrents P2P a través de este receptor. No reenvía cabeceras
  especiales que un reproductor externo pudiera recibir; se valida la ruta RD HTTP normal.
- El contenido se conserva en el directorio específico de la app dentro del USB; desinstalar
  la app puede eliminarlo. La pantalla es un gestor de descarga, no un nuevo reproductor.
- Al detener el proceso o actualizar el APK, se conserva el parcial pero hay que reanudar;
  no se afirma recuperación automática tras un reinicio completo de Android.

## Validación

- Compilación ARM y 20 pruebas unitarias correctas; Android Lint sin errores (78 advertencias).
  Validación global: 0 hallazgos. APK de prueba firmado: 8.964.951 bytes,
  SHA-256 `8161a39384997be1fd9aa01e9b8d996f64d698faf4025850ac48da524039909c`.
- Android reconoce el destino junto a VLC para `ACTION_VIEW` de vídeo HTTP(S).
- Recepción y confirmación sintéticas en TV; transferencia de 32 MiB con corte a 8 MiB,
  reanudación y SHA-256 exacto. Se observó avance en vivo hasta 100%.
- Archivo y cola sintéticos retirados sin borrar el parcial preexistente del usuario.
- Entrega real verificada desde una fuente de Torrentio RD (`tt0063350`) con Default Player
  temporalmente en External: selector Android → Local Cache → Solo una vez. La actividad
  recibió el enlace HTTPS y el nombre del MKV; no inició otra transferencia sin confirmar.
- La descarga real preexistente se retomó y siguió avanzando en segundo plano al navegar
  por Stremio. No se usa su título ni su enlace en las pruebas automatizadas.
- Pendiente: una descarga RD completa iniciada por este receptor, enlaces caducados,
  reinicio completo y revisión visual más amplia. Es un prototipo, no una versión estable.
