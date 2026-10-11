# Decisiones

Registro de decisiones tomadas durante el desarrollo cuando `docs/` no lo fijaba o había dudas.
Formato: fecha · fase · decisión · motivo · alternativas descartadas. Las que contradicen `docs/` también se anotan en *Desviaciones* de `docs/07-fases.md`.

## Fase 0

- **2026-10-11 · Versionado y publicación.** SemVer `X.Y.Z(-rc.N)`; cada fase cerrada sube el *minor* (fase 0 → `0.1.0`). `versionCode` derivado de la versión. Flujo PR → CI → merge → tag → Release firmada (`docs/08-versiones-y-publicacion.md`). *Motivo*: publicación reproducible y sin pasos manuales. *Descartado*: `versionCode` por fecha (no reproducible).
- **2026-10-11 · Merge de PR con merge commit.** Se conservan los commits pequeños de cada rama. *Descartado*: *squash* (pierde el detalle que pide `CLAUDE.md`).
- **2026-10-11 · Release falla sin clave.** El workflow de Release no publica con la clave de debug. *Motivo*: un APK firmado con debug no se puede actualizar luego con la clave real.

## Fase 1

- **2026-10-11 · Room 3 (`androidx.room3` 3.0.3).** Es la versión mayor actual de Room, la que usan los demás proyectos Ultimate en esta máquina, y su API de drivers permite probar la BD en JVM con el SQLite empaquetado (mismo FTS4 que en el dispositivo, sin emulador). En producción se usa `AndroidSQLiteDriver` (sin librería nativa extra en el APK). *Descartado*: Room 2.x (API heredada `SupportSQLite`); `BundledSQLiteDriver` en producción (+~1 MB nativo por ABI sin necesidad).
- **2026-10-11 · Plegado de texto en la app, no en el tokenizer.** `remove_diacritics=2` necesita SQLite ≥ 3.27; Android 10 trae 3.22 y la tabla FTS no se podría crear. `TextNormalizer` pliega índice y consulta igual en todas las versiones. *Descartado*: `remove_diacritics=1` (deja sin plegar algunos caracteres) y FTS5 (no garantizado en Android 10).
- **2026-10-11 · Unicidad de sectores con `normalizedName`.** NOCASE solo pliega ASCII. Se añade la columna plegada con índice único (igual que `Company.normalizedName`).
- **2026-10-11 · Sufijos legales como fichero de datos.** `domain/src/main/resources/dictionaries/legal_suffixes.txt` (regla de `CLAUDE.md`: diccionarios fuera del código). Se quitan solo al final del nombre y nunca el nombre entero (`AG` sigue siendo `ag`).
- **2026-10-11 · Siembra de sectores en `onCreate` de la BD.** Ocurre una sola vez, en el idioma del dispositivo en ese momento; si el usuario borra sectores no reaparecen. Los nombres salen de `R.array.default_sectors` (`:data`, es/en). *Descartado*: sembrar «si la tabla está vacía» (resucitaría sectores borrados a propósito).
- **2026-10-11 · `contactId UNINDEXED` y borrado por búsqueda lineal en `contact_fts`.** Como en `docs/02`. Usar el `rowid` del contacto como `docid` sería más rápido, pero el `rowid` de una tabla con PK `TEXT` puede cambiar tras un `VACUUM`. Se medirá con 5 000 contactos en la fase 10.
- **2026-10-11 · Índice FTS dentro de la misma transacción.** Todas las escrituras de los repositorios van en `withWriteTransaction` y reindexan los contactos afectados (empresa, sector, evento o tarjeta renombrados o borrados). Los fallos de SQLite se convierten en `DomainError.Storage`.
- **2026-10-11 · Validan antes de escribir.** Los repositorios devuelven `DomainResult`: `Invalid` (nombre vacío), `Conflict` (nombre normalizado ya usado, con el id existente), `NotFound` y `NotAllowed(ONLY_CARD_OF_CONTACT)`.
- **2026-10-11 · Tarjeta principal = la más reciente al enlazar.** Al enlazar una tarjeta a un contacto, la más reciente por `capturedAt` pasa a ser la principal; `setPrimary` permite elegir otra después. Al borrar la principal, la siguiente más reciente ocupa su lugar.
- **2026-10-11 · Email en minúsculas al guardar.** Facilita la regla de duplicados «email exacto» (`docs/06`).
- **2026-10-11 · Qualifiers de dispatchers en `:domain/util`.** `:data`, `:ocr` y los casos de uso los necesitan; `:domain` solo depende de `javax.inject` (sin Android).

## Fase 2

- **2026-10-11 · Detector propio en Kotlin puro (no OpenCV).** Otsu en ambas polaridades + morfología + componentes conexas + envolvente convexa + simplificación a 4 vértices, con filtros de forma (proporción, cobertura, solidez) y descarte de regiones que tocan 3+ bordes (la mesa). *Motivo*: cero dependencias nativas (OpenCV añade decenas de MB por ABI y complica F-Droid), código testeable en JVM con imágenes sintéticas, y el recorte por guía + recorte manual cubren los fallos. *Descartado*: OpenCV (peso, nativo); detector por bordes Canny + contornos (más frágil con texto y fondos con textura, y más código). Pendiente validar con fotos reales (`docs/07-fases.md`, *Resultados de medición*).
- **2026-10-11 · Orden de esquinas por el borde superior más horizontal.** Mantiene la orientación de la tarjeta tal como aparece en la foto (apaisada o vertical). A 45° exactos la orientación es ambigua por naturaleza; el usuario puede reencuadrar.
- **2026-10-11 · Guía única para dibujar y para recortar.** `CaptureGuide` (85×55 apaisada, 88 % del encuadre) se calcula en coordenadas de imagen; la vista previa la dibuja transformando esas coordenadas con `CoordinateTransform`, y el recorte de respaldo usa el mismo rectángulo sobre la foto: lo encuadrado es lo que se guarda. Preview, análisis y captura usan 4:3.
- **2026-10-11 · API experimental de transformaciones de CameraX.** `ImageProxyTransformFactory` y `CoordinateTransform` (`@TransformExperimental`) son la forma documentada de mapear coordenadas del análisis a la vista previa; el *opt-in* está acotado a `FrameAnalyzer` y `CameraPreview`.
- **2026-10-11 · `LifecycleCameraController`.** Da enfoque por toque y pellizco sin código propio. Captura en memoria (`OnImageCapturedCallback`) a ~2400×1800 (lo justo para el original ≤ 2400 px), girada según `rotationDegrees`; el JPEG se reescribe desde píxeles y no lleva EXIF.
- **2026-10-11 · Disparo automático opcional y apagado por defecto.** Se dispara si la tarjeta lleva 0,7 s quieta (esquinas < 2 % de la diagonal) y nítida. *Motivo*: un disparo inesperado molesta más que pulsar el botón.
- **2026-10-11 · Umbral de nitidez conservador** (varianza del laplaciano < 40 a 640 px, media de 5 fotogramas). Se ajustará con fotos reales; un falso aviso es peor que ninguno.
- **2026-10-11 · Las fotos van a disco en cuanto se hacen.** `CardCaptureSaver` guarda original y enderezada de cada cara al momento y crea la fila `PENDING` al final; si la fila falla, borra los archivos. Si se abandona la captura sin guardar, se borran en un `@ApplicationScope` (el `viewModelScope` ya está cancelado). Lo que quede por un cierre forzado lo limpia `ImageStore.deleteOrphans`.
- **2026-10-11 · `ImageStore` lanza `IOException`.** Es la frontera con el sistema de archivos; quien la usa (p. ej. `CaptureViewModel`) la convierte en un estado de error de UI. Rechaza rutas fuera del almacén y ids que no sean UUID.
- **2026-10-11 · Banner «Pendientes de revisar: N» ya en la fase 2** (solo informativo, sin navegación hasta la fase 5) para que la captura tenga respuesta visible en la pantalla principal.

## Fase 3

- **2026-10-11 · Extractor determinista por capas.** Email → web → país escrito → teléfonos → dirección → pistas de país → cargo → empresa → nombre; cada detector retira de las líneas lo que reconoce. *Motivo*: los patrones fuertes (email, URL, teléfono) liberan las líneas ambiguas para los detectores débiles (nombre, empresa). Sin modelos ni red (`docs/04`).
- **2026-10-11 · Empresa por puntuación.** Forma jurídica (+4), coincidencia con el dominio del correo/web (+3: contenido, iniciales o prefijos como `atlprop` ← «Atlantique Propulsion»), tamaño de letra (+1,5), posición alta, mayúsculas, bloque propio; cargo fuerte (−4) o parecido a la persona del correo (−5). Umbral 2,0; empate cercano baja la confianza.
- **2026-10-11 · Nombre con la pista del correo.** La parte local del correo (`ana.ruiz`, `aruiz`, `adp`) es la señal más fiable para elegir la línea del nombre y descartarla como empresa.
- **2026-10-11 · Dos apellidos solo en español.** Por país de la tarjeta o, si no consta, por marcadores de idioma (`language_markers.txt`); «María José García» respeta segundos nombres frecuentes (`compound_first_names.txt`).
- **2026-10-11 · Móvil por prefijo y longitud.** La tabla de la doc (ES/DE/FR/IT/UK) se amplía a 22 países y añade la longitud del número nacional y el 0 troncal, porque sin ellos `04…` (móvil en Bélgica) o `05…` (UAE) convertían fijos franceses o alemanes en móviles.
- **2026-10-11 · Confianza.** Base por detector (email/web 0,95, teléfono con etiqueta 0,9 / sin ella 0,7, país escrito 0,92/0,85, dirección 0,85/0,65, cargo 0,65–0,92, empresa y nombre según puntuación) × (0,5 + 0,5·confianza del motor en la línea). Por debajo de 0,6 se resalta en la revisión (`ExtractedFields.DEFAULT_THRESHOLD`, configurable en la fase 10).
- **2026-10-11 · Tratamientos a notas.** `Dr.`, `Dipl.-Ing.`, `Dott.`, `Mme`, `Kapt.`… se quitan del nombre y se proponen como nota.
- **2026-10-11 · Cola con un reintento.** Dos fallos → `OCR_ERROR` (la foto se conserva, se rellena a mano). `OcrWorker` devuelve éxito en ese caso para que la cadena única siga. `OCR_ERROR` vuelve a entrar si se reencola (reintento manual, fase 5).
- **2026-10-11 · OCR lanzado al guardar la captura (modo normal).** Se encola en la cola normal de WorkManager (sin trabajo *expedited*, que exigiría servicio en primer plano y notificación). WorkManager lo ejecuta al momento si no hay otra tarjeta delante.
- **2026-10-11 · Tesseract4Android desde JitPack, acotado.** Solo existe allí; el repositorio se limita al grupo `cz.adaptech.tesseract4android`. Los modelos *fast* se versionan en el repo (Apache-2.0, ~12 MB) con su commit y SHA-256, en lugar de descargarlos al compilar (builds reproducibles y sin red, también para F-Droid).
- **2026-10-11 · Tamaño del APK pendiente.** Con ML Kit (play) y Tesseract + modelos (foss) los APK de debug con todas las ABI pasan de 80 MB. La reducción (ABIs, *splits*, AAB) es parte de la fase 10 («tamaño de APK por flavour»).
