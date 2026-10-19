# Nombres originales y borrado con VLC — v0.5.9-d4n.4

## Nombres de las nuevas descargas

Se conserva el nombre del último segmento de la URL HTTP si termina en una extensión de
vídeo reconocida, decodificando escapes sin convertir los signos `+` en espacios.
Tiene prioridad sobre el título genérico que pueda enviar Stremio. Si el enlace no incluye
un nombre de vídeo, se usa el título recibido. No se puede reconstruir un nombre original
que ni Stremio ni el enlace proporcionen; no se añade una consulta nueva a Real-Debrid.

- Mantiene espacios, Unicode, temporada/episodio y etiquetas de la publicación.
- Reemplaza caracteres de control, direcciones de texto y caracteres incompatibles con
  exFAT; limita el tamaño del nombre para dejar espacio a parciales y sidecars.
- Si ya existe un archivo, parcial, sidecar o reserva con ese nombre, añade `(2)`, `(3)`, etc.
  Las colisiones también se comprueban sin distinguir mayúsculas/minúsculas.
- La identidad privada sigue siendo el hash de la URL: cambiar el nombre visible no mezcla
  descargas de enlaces distintos. Los sidecars siguen sin incluir URLs ni credenciales.
- Reutiliza la ruta de una entrada o su sidecar al reanudar; no renombra los parciales
  antiguos y no vuelve a calcular un nombre opaco al arrancar el motor.
- No renombra automáticamente películas antiguas. En esta TV se renombró por separado la
  película completa y su sidecar con el nombre ya guardado en sus metadatos. Se verificaron
  mismo inodo/tamaño, hashes del primer/último MiB y sidecar idéntico. Sin sobrescrituras.

## VLC en esta Xiaomi Android 11

El VLC instalado (3.7.1) declara `MANAGE_EXTERNAL_STORAGE`, pero el dispositivo rechazaba
ese acceso. Los permisos tradicionales de lectura/escritura no equivalen al acceso especial
en Android 11: https://developer.android.com/training/data-storage/manage-all-files

Se habilitó únicamente el AppOp de acceso a todos los archivos para VLC (usuario personal).
Es un permiso amplio para el almacenamiento compartido, no limitado a Movies. No se
reactivó Gallery, MediaExplorer, Miracast ni ningún otro componente Xiaomi.

La prueba desde la interfaz real de VLC, en Movies, mostró la confirmación de eliminación
y borró correctamente un archivo desechable de 16 KiB. La película del usuario siguió
intacta. Para eliminar: pulsación larga/Información → Eliminar → confirmación permanente.

## Validación de .4

- Compilación ARM32 instalada sin borrar datos: versionCode 84 / 0.5.9-d4n.4.
- 30 pruebas unitarias: 0 fallos/errores. Lint: 0 errores y 79 advertencias.
- Validador global de desarrollo: 0 hallazgos.
- Prueba sintética real en USB: `PRUEBA-Serie.S01E02.mp4` frente a un archivo centinela ya
  existente. La descarga creó `PRUEBA-Serie.S01E02 (2).mp4`, conservando intacto el centinela.
- Corte HTTP a 8 MiB y reanudación If-Range; después se cerró forzosamente la app con
  24.117.248 bytes escritos. Al abrirla y reanudar conservó la ruta y solicitó desde ese byte.
- Resultado final: 33.554.432 bytes; SHA-256 completo
  `e09320c5b00b34bb704802136c599a95b3996332ba84d7c7f21112b6231b6bd0`.
- Se eliminaron únicamente los archivos sintéticos identificados y se cerró el túnel de prueba.
- APK de prueba firmado: 8.956.759 bytes; SHA-256
  `ae951732d190201abefc65e66e195c7f24dd71d0fd44433c983fc1e33e71996f`.
- Los cambios no renuevan enlaces de Real-Debrid expirados ni permiten recuperar un nombre
  ausente del enlace/título. No se realizó una descarga nueva de contenido del usuario.
