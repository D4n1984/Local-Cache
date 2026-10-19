# Descargas USB persistentes — v0.5.9-d4n.3

Esta variante ya no usa el addon local ni su servidor HTTP. La pantalla principal solo ofrece
progreso, selección del USB y acceso especial a archivos. No contiene «Start server».
El servicio de descargas no abre puertos, no es sticky y se detiene tras unos 10–15 segundos
sin transferencias. Las URLs de manifests duplicadas de Torrentio/Comet se eliminan de los
datos privados al abrir la pantalla principal; se conservan las URLs privadas de reanudación.

## Destino

**`/storage/<UUID-del-USB>/Movies`**, fuera de `Android/data` y `Android/obb`.
Los vídeos y sus sidecars sin claves permanecen en el USB al desinstalar esta app. No se
promete conservar la configuración ni los enlaces de reanudación: esos sí son datos privados
que Android borra al desinstalar. Un archivo completo no depende de la app para permanecer
en el USB y puede abrirse con otro gestor/reproductor que tenga permiso de lectura.

En una reinstalación hay que volver a autorizar el acceso y seleccionar el USB. La app puede
recuperar su lista de archivos completos mediante los sidecars. Los archivos ajenos que ya
estuvieran en Movies no se adoptan ni borran.

## Permiso especial en esta Xiaomi / Android 11

El selector SAF de carpetas no está disponible en esta TV. Se solicita
`MANAGE_EXTERNAL_STORAGE` para acceder al USB compartido sin depender del proveedor que
habíamos creado antes. **El permiso del sistema es amplio**, no un permiso limitado a Movies.
El código de selección restringe el destino a Movies de un volumen removible detectado;
los enlaces externos no pueden escoger una ruta. Es una compilación sideload para este
dispositivo, no una declaración de cumplimiento de políticas de Google Play.

Se puede autorizar desde **Autorizar acceso a archivos**. En la TV administrada por ADB:

```sh
rtk proxy adb -s <TV>:5555 shell appops set --uid app.localcache.d4n1984 MANAGE_EXTERNAL_STORAGE allow
```

Referencia de Android: https://developer.android.com/training/data-storage/manage-all-files

## Migración y conservación

- No permite cambiar de destino mientras haya una descarga activa.
- Tras comprobar escritura en Movies con un archivo temporal único, traslada solo medios
  propios con sidecar desde su antiguo directorio específico del USB.
- Precomprueba las colisiones de todos los archivos y usa `Files.move` sin REPLACE_EXISTING.
  Si algo falla, intenta revertir los movimientos; el error pide revisar ambas carpetas.
  No hay garantía transaccional frente a un corte eléctrico entre dos renombrados.
- Actualiza la selección solo tras el traslado; conserva los enlaces privados asociados.
- No hay evicción automática. Si el límite o el espacio libre no alcanzan, informa del error
  y conserva los parciales, sin eliminar películas para crear espacio. Se reservan 5 GiB de
  espacio físico y se mantiene el límite de 100 GiB por defecto.
- Los vídeos completos se notifican al escáner multimedia para facilitar que otros
  reproductores los encuentren. No se guardan URLs de RD en Movies.

## Limpieza del dispositivo

Se retiran Unchained, el selector personalizado y el monitor anterior, con sus datos internos.
Se elimina únicamente el addon Local Cache de Stremio, no Torrentio RD ni los demás addons.
No se borran los vídeos/parciales existentes de Download ni otras carpetas del USB.

## Validación

- Compilación ARM 32 bits correcta; 24 pruebas, 0 fallos/errores. Lint: 0 errores y
  79 advertencias; validación global: 0 hallazgos. La advertencia sobre acceso amplio a
  almacenamiento es deliberada y no se oculta.
- APK firmado de prueba: 8.952.663 bytes; SHA-256
  `e7058ae296854d32befa52362a3470805f98e5a866ac400de92520e997bb5d81`.
- Instalación .3, permiso autorizado y selección desde la UI: se trasladó el archivo completo
  de 5.121.570.152 bytes y su sidecar a Movies. Mismo dispositivo/inodo y tamaño; hashes
  del primer/último MiB y del sidecar idénticos. No se afirma un hash completo nuevo de ese vídeo.
- Descarga sintética de 32 MiB escrita en Movies con corte a 8 MiB y reanudación If-Range.
  Hash completo exacto: `e09320c5b00b34bb704802136c599a95b3996332ba84d7c7f21112b6231b6bd0`.
- Sin respuesta del servidor HTTP antiguo durante esta ruta; servicio ausente tras quedar
  inactivo. El addon local se verificó desinstalado en Stremio, sin cambiar Torrentio RD.
- No se prueba una desinstalación destructiva sobre los datos del usuario; la persistencia se
  basa en la carpeta compartida y las reglas de Android, no en haber desinstalado esta app.
