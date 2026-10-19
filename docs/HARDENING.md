# Revisión y endurecimiento — 2026-10-19

## Alcance

- Upstream: `heroiamarelo1/Local-Cache`, commit `895f47c2cf530a6ba1a991bd4313f2c899f11c3c` (v0.5.8).
- Fork: https://github.com/D4n1984/Local-Cache ; rama `hardening/xiaomi-android11`.
- Objetivo: Xiaomi MiTV-AFKR0 / Android 11 / ARM 32 bits / 2 GB RAM / USB exFAT.
- Revisión estática y pruebas sintéticas. No se usan cuentas ni URLs reales de Real-Debrid.
- No se instala el upstream ni el fork en la TV durante esta revisión.
- No es auditoría independiente ni garantía de ausencia de vulnerabilidades.

## Hallazgos en el upstream

### Alto — administración sin autenticación en la red

`server/LocalHttpServer.kt:29` escucha en `0.0.0.0`. `/settings` (117), `/cache` con
configuración completa (147), `/logs` y `/cancel` (152) no comprueban identidad.
`SettingsPage.handlePost` permite cambiar fuentes y almacenamiento, reanudar y eliminar caché.
CORS es `*` (332) y `/cancel` acepta GET. Requiere acceso de red al puerto: no implica
exposición automática a Internet, pero sí acceso desde otros equipos de esa red.

### Alto — secretos en USB y backup

`config/AddonConfig.kt:262–300` exporta configuración al USB, con URLs que pueden contener
claves en su ruta. `storage/LocalLibrary.kt:44` incluye URLs de descarga en sidecars.
El manifest original permite backup de datos de la app.

### Alto — integridad de reanudación/finalización insuficiente

`storage/DownloadEngine.kt` añade Range sin exigir el offset correcto de Content-Range
ni conservar validadores de entidad. Al llegar EOF solo comprueba un mínimo de 1 MiB (536)
antes de renombrar (541) y marcar completo, no el tamaño esperado. Un reset cae en catch (551)
sin reintento automático. El timeout de lectura es 6 horas (36).

### Medio — limpieza de archivos ajenos

`DiskQuota` toma como candidatos todos los archivos de la carpeta salvo `.meta.json`,
incluidos configuración, logs o medios colocados allí, sin exigir sidecar de propiedad.

### Recursos y otras observaciones

- Mantiene wake lock al arrancar el servicio, aun sin descarga activa.
- Incluye dependencias nativas de varias ABI; esta Xiaomi solo admite ARM 32 bits.
- Wrapper original Gradle 9.3.0 con AGP 8.13.2/Kotlin 1.9.24. El fork fija Gradle 8.13
  y SHA-256; no se afirma haber compilado y comparado el upstream sin cambios.
- No aparecen SDKs de publicidad/analítica en las dependencias examinadas. Sí hay consulta
  de actualizaciones a GitHub y detección de IP pública para WuPlay; no es auditoría de tráfico.

## Cambios

### Seguridad

- Servidor/prueba de puerto en loopback, sin LAN/WAN ni apertura de router.
- Host, Origin y Referer estrictos contra DNS rebinding y solicitudes web cruzadas.
- Token aleatorio de 256 bits para administración; cookie HttpOnly/SameSite=Strict.
- `/cancel` exige POST, sin CORS comodín ni errores externos reflejados completos.
- Manifest/streams/video siguen accesibles a clientes nativos locales sin token administrativo.
  **No aísla frente a aplicaciones maliciosas del mismo Android ni frente a root.**
- Configuración y URLs de reanudación en datos privados; backup desactivado.
- Sidecars nuevos sin URL. No se borran exports antiguos del usuario: un archivo importado
  del USB sigue siendo sensible y debe retirarse manualmente.
- La caché USB se guarda en el directorio específico de esta app (`Android/data/.../files`),
  sin necesitar el selector SAF ni acceso general a archivos. Android puede eliminarla al
  desinstalar la app; no equivale a una biblioteca portátil independiente de la instalación.
- URLs redactadas en log de diagnóstico; actualizador apunta al fork.

### Descargas

- `HttpTransfer`, independiente de Android: una conexión, memoria acotada, hasta tres
  reintentos adicionales con esperas 1/2/4 s para fallos IO/transitorios.
- Reintenta desde el tamaño realmente escrito en `.part`, con `Accept-Encoding: identity`.
- Valida Content-Range, longitud y cambios de entidad conocidos. Usa If-Range con ETag fuerte
  o Last-Modified cuando existen; guarda el validador en datos privados.
- HTTP 200 al pedir Range reinicia de cero: nunca concatena el cuerpo completo al parcial.
- EOF corto no es éxito. Verifica bytes en disco/tamaño conocido antes de promover a final.
- Conserva parciales al agotar reintentos o caducar/cambiar el enlace.
- Sin tamaño verificable no declara éxito. Sin ETag/Last-Modified no garantiza identidad
  criptográfica: valida rango/tamaño sobre el mismo enlace.
- No renueva automáticamente enlaces RD caducados ni importa parciales de Unchained.

### Recursos y build

- ABI predeterminada `armeabi-v7a`; otras ABI solo si se piden al compilar.
- Sin Jetifier; workers limitados y Kotlin en proceso para acotar memoria de build.
- Buffer de salida HTTP de 1 MiB (antes 4 MiB), lectura de 256 KiB, timeout 60 s.
- No inicializa interacción torrent al cancelar HTTP; wake lock solo durante descargas.
- Evicción/borrado limitado a medios con sidecar propio, no archivos arbitrarios.

## Pruebas y pendientes

Tests de descarga exacta, Range/If-Range, Range ignorado, ETag cambiado, truncado,
caída con reanudación, presupuesto, cancelación, rangos inválidos, protección del panel,
redacción de URLs y propiedad de archivos. MockWebServer solo usa datos sintéticos.

Validación local del 2026-10-19, en contenedor OrbStack (sin credenciales reales):

- `testStandardDebugUnitTest`: 17 pruebas, 0 fallos y 0 errores.
- `assembleStandardRelease`: correcto; APK sin firmar de 8.910.152 bytes, solo `armeabi-v7a`.
  SHA-256: `1026ba3af913d9c8e0813b07ef969572145059de4b3d7d872d2a0439b51224dc`.
- `lintStandardRelease`: correcto, 0 errores y 74 advertencias (principalmente textos,
  modernización de API/Gradle y layout). No se presenta como un informe sin advertencias.
  Se corrigió el uso original de NotificationChannel sin guardia de API 26 para minSDK 24.
- `validate-dev-standards`: 0 hallazgos; análisis estático, no prueba de seguridad exhaustiva.

Pendiente la prueba real en Xiaomi (USB, Stremio, reinicio y recuperación de un corte),
con consentimiento para instalar y sin compartir tokens en el chat.

## Fuentes

- Upstream fijado: https://github.com/heroiamarelo1/Local-Cache/tree/895f47c2cf530a6ba1a991bd4313f2c899f11c3c
- HTTP Range/If-Range: https://www.rfc-editor.org/rfc/rfc9110.html#name-range-requests
- Datos privados Android: https://developer.android.com/training/data-storage/app-specific
