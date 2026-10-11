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

## Fase 4

- **2026-10-11 · Guardar en una transacción (`TransactionRunner`).** Empresa (enlazar o crear), contacto, evento de la tarjeta, enlace tarjeta→contacto y estado `REVIEWED` se escriben juntos; cualquier fallo lo deshace todo (las transacciones de cada repositorio se anidan). *Descartado*: escribir paso a paso (dejaría empresas o contactos huérfanos si falla el último paso).
- **2026-10-11 · Empresa enlazada por nombre normalizado.** «Navaltech, S.L.» enlaza con «NAVALTECH». Si se elige un sector para una empresa existente, se le asigna (es la intención del usuario); una empresa nueva toma sector, país y web de la tarjeta.
- **2026-10-11 · Duplicados en memoria.** Al guardar se cargan todos los contactos con su empresa y se aplican las reglas de `docs/06` en Kotlin (`DuplicateDetector`). Con miles de contactos es un recorrido de milisegundos y mantiene las reglas testeables sin SQL. Se medirá con 5 000 contactos en la fase 10.
- **2026-10-11 · El aviso de duplicado no bloquea.** Se muestra antes de escribir nada; «Guardar como nuevo» guarda igualmente; cualquier cambio en el formulario vuelve a comprobar.
- **2026-10-11 · El formulario no espera al OCR.** Se puede escribir mientras se lee la tarjeta; al terminar se rellenan solo los campos no tocados y se resaltan los dudosos (< 0,6). Si el OCR falla, formulario vacío con la foto y un aviso.
- **2026-10-11 · Mínimo para guardar.** Nombre, apellidos, empresa, email, teléfono o móvil: al menos uno. Un contacto sin nada de eso no se podría encontrar ni usar.
- **2026-10-11 · Sugerencias.** Empresa: las existentes que contienen lo escrito (plegado). País: nombres normalizados cuyo alias en cualquiera de los 5 idiomas empieza por lo escrito («Corea» → «South Korea»); se guarda el nombre normalizado en inglés, como pide `docs/02`.
- **2026-10-11 · Coil solo para archivos locales.** `coil-compose` sin módulos de red: no arrastra OkHttp ni permisos.

## Fase 5

- **2026-10-11 · Escaneo rápido con un canal ordenado y búfer de 3.** Cada disparo entra en un `Channel` que procesa una corrutina en orden (anverso, reverso, alta de la tarjeta, encolado del OCR). La UI nunca espera; si hay 3 fotos sin guardar, el disparador se pausa para no llenar la memoria con mapas de bits de ~17 MB. *Descartado*: procesar en paralelo (más memoria y orden incierto entre anverso y reverso).
- **2026-10-11 · Nada se pierde al salir.** «Terminar» y el gesto atrás esperan a que todo esté en disco; un anverso cuyo reverso no llegó se guarda solo. Si la pantalla se destruye igualmente, los anversos ya guardados se dan de alta en el `@ApplicationScope`. Una muerte del proceso pierde, como mucho, las ≤ 3 fotos aún en memoria.
- **2026-10-11 · Recuperación al arrancar.** `PROCESSING` → `PENDING` y reencolado de todas las `PENDING` en cada arranque. El worker es idempotente, así que reencolar de más no lee dos veces.
- **2026-10-11 · `OcrScheduler` como interfaz.** La captura, el escaneo rápido y la recuperación solo necesitan «encolar esta tarjeta»; `OcrQueue` lo implementa con WorkManager y los tests usan un registrador. La semántica de la cadena (una a una, el resto `BLOCKED`) se prueba aparte con WorkManager real.
- **2026-10-11 · La cola de revisión incluye las que aún se leen.** Se pueden revisar ya (el formulario se completa cuando termina el OCR); el orden es por fecha de captura.
- **2026-10-11 · Límite de tiempo en los tests (10 min).** Un test colgado hace fallar la build en lugar de bloquear la máquina.

## Fase 6

- **2026-10-11 · Filtros de nombre y apellidos con columnas FTS propias (esquema v2).** `docs/02` pide filtrar por nombre y por apellidos por separado; el índice solo tenía `name` (ambos juntos). Se añaden `firstName`/`lastName` a `contact_fts` con su migración y su test. Como una tabla FTS4 no se puede alterar, la migración la recrea vacía y `SearchIndexHealth` la reconstruye al arrancar. *Descartado*: un único filtro «nombre o apellidos» (se aparta de la doc) o `LIKE` sobre las columnas (no pliega acentos en SQLite).
- **2026-10-11 · Un único `MATCH` para texto libre y filtros de campo.** Los filtros de nombre, apellidos y cargo son filtros de columna FTS sumados al texto libre; los demás (evento, sector, país, empresa, fechas) son condiciones SQL. El orden: sin filtros, más recientes primero; con filtros, por apellido y nombre.
- **2026-10-11 · Un texto sin nada buscable no devuelve todo.** «*» o comillas sueltas dan cero resultados en lugar de ignorar el filtro.
- **2026-10-11 · Imagen de la rejilla sin generar nada.** `ImageStore.preview` devuelve la miniatura si existe o la enderezada (Coil la reduce); generar miniaturas al pintar una lista de miles bloquearía.
- **2026-10-11 · Preferencias de UI en SharedPreferences.** Solo la vista tarjetero/lista por ahora; sin dependencia nueva (DataStore no está en la lista aprobada). Irán al `settings.json` del backup (fase 9).
- **2026-10-11 · Búsqueda global desde un evento.** Quitar el chip «Solo en este evento» abre Tarjetas con el mismo texto (`cards?q=`).
- **2026-10-11 · Ficha de contacto básica ya en la fase 6** (solo lectura) para que la lista lleve a algún sitio; la fase 7 añade edición, acciones, histórico y fusión.
- **2026-10-11 · Paralelización con un subagente.** Las pantallas de Eventos, Empresas y Sectores las escribió un subagente en un worktree sin compilar (solo quedaban ~2,5 GB de RAM libres y dos builds de Gradle a la vez no caben); se integraron y compilaron después en una sola build.

## Fase 7 · vCard y contactos del teléfono

- **2026-10-11 · Escritor vCard 3.0 propio (`VCardWriter`, Kotlin puro).** UTF-8, CRLF, escapado RFC 2426 (`\`, `,`, `;`, saltos) y plegado a 75 octetos sin partir caracteres multibyte. Sin foto por ahora (desactivada por defecto en `docs/05`). *Descartado*: ez-vcard (dependencia no aprobada, solo para unas pocas propiedades).
- **2026-10-11 · Validación con un parser independiente escrito en el test.** `docs/05` pide validar contra un parser independiente; añadir una librería solo para tests no está aprobado, así que el test trae su propio lector mínimo (desplegado, parámetros, desescapado, componentes) sin compartir código con el escritor.
- **2026-10-11 · `PhoneContactsWriter` en un solo lote.** `ContentProviderOperation` con el contacto en bruto en la cuenta local/por defecto (sin tipo ni nombre de cuenta) y una fila por dato. No lee la agenda (sin `READ_CONTACTS`); el permiso que falta se devuelve como resultado (`Failed(PERMISSION)`), nunca como excepción. Su test instrumentado concede `READ_CONTACTS` solo al test para comprobar los campos.
- **2026-10-11 · Exportación `.vcf` reutilizando los filtros.** `VcfExporter` exporta lo que seleccione un `ContactFilter` (evento, empresa, sector…) o una lista de ids (en bloques de 500 parámetros), con el país de la empresa si el contacto no tiene, sobre un `OutputStream` que abre quien llama (SAF o FileProvider).

## Fase 7 · Ficha, fusión e histórico

- **2026-10-11 · Plan de fusión explícito (`MergePlan`).** Por campo: el valor no vacío; si ambos difieren, el del lado con la tarjeta más reciente; el otro, a un toque. Las notas se unen bajo un separador con la fecha (ISO). La pantalla solo pregunta por los campos en conflicto y resume el resto.
- **2026-10-11 · «Fusionar» y «Añadir como nueva tarjeta» desde la revisión.** «Fusionar» abre la comparación (la tarjeta nueva gana por defecto en los conflictos, por ser la más reciente). «Añadir como nueva tarjeta» la une al historial sin tocar los datos del contacto salvo para rellenar huecos. Ambas usan `AddCardToContactUseCase` en una transacción.
- **2026-10-11 · Deshacer la fusión en memoria.** `MergeUndo` guarda ambos contactos, sus eventos y las tarjetas movidas; la ficha lo ofrece con un *snackbar* largo y desaparece al cerrarla o reiniciar (`MergeUndoHolder`, nunca persistido).
- **2026-10-11 · «Añadir nueva tarjeta» desde el histórico.** Captura ligada al contacto → revisión que, al guardar, compara con ese contacto (misma comparación que «Fusionar»).
- **2026-10-11 · `PhoneContacts` como interfaz.** El ViewModel de la ficha se prueba con un falso; `PhoneContactsWriter` tiene su test instrumentado (contactos reales del dispositivo).
- **2026-10-11 · Compartir solo la carpeta `cache/shared`.** El `FileProvider` expone únicamente esa carpeta (vCards y exportaciones), nunca las fotos; se usa solo por acción explícita.
- **2026-10-11 · Si se niega `WRITE_CONTACTS`, se ofrece el `.vcf`.** Como pide `docs/05 §5`.
- **2026-10-11 · Exportar `.vcf` lo que se ve.** El botón de Tarjetas exporta exactamente la lista con la búsqueda y filtros actuales (todo, un evento, una empresa, un sector…), por SAF.
- **2026-10-11 · Fusión manual desde la ficha** («Fusionar con…» → elegir contacto → comparar). La selección múltiple de la lista y «Posibles duplicados» llegan en las fases 8 y 10.

## Repositorio

- **2026-10-11 · Público, con GitGuardian, Dependabot y *secret scanning*.** Por petición del usuario el repositorio pasa a público (se revisó antes que la historia no tuviera claves ni secretos). GitGuardian revisa cada PR; Dependabot propone actualizaciones semanales agrupadas (Gradle y Actions) que pasan por el CI como cualquier cambio; *secret scanning* y *push protection* de GitHub activados.
- **2026-10-11 · Cambios que no tocan la app no generan versión.** La configuración del repositorio (p. ej. Dependabot) se fusiona tras el CI sin nueva etiqueta; cada fase y cada cambio de la app sí sube versión.

## Fase 8

- **2026-10-11 · Escritores propios de CSV y XLSX en `:domain`.** Kotlin puro con `java.util.zip`; el XLSX tiene el mínimo de partes OOXML (tipos, relaciones, libro, estilos con negrita, hoja con celdas `inlineStr`, fila congelada y anchos). *Descartado*: Apache POI (prohibido por la doc, decenas de MB).
- **2026-10-11 · Validación independiente sin dependencias.** Los tests leen el CSV con un lector RFC 4180 propio del test y el XLSX con el parser DOM del JDK (`javax.xml`), ajenos al código que escribe. No hay librería de hojas de cálculo aprobada para tests.
- **2026-10-11 · Protección contra fórmulas en CSV y XLSX.** Prefijo `'` si la celda empieza por `=`, `+`, `-`, `@`, tabulador o retorno de carro. Implica que un teléfono «+34…» se exporta como «'+34…»: es la contrapartida conocida de esta protección y la recomendada por OWASP.
- **2026-10-11 · Una consulta para eventos y otra para sectores.** La exportación carga los nombres de eventos por contacto y el sector de cada empresa en dos consultas, no una por contacto.
- **2026-10-11 · BOM escrito como `Char(0xFEFF)`.** Un literal con el carácter (o su escape en una cadena) desaparecía al formatear el código.

## Fase 9 · Restauración

- **2026-10-11 · Dos pasadas sobre el documento SAF.** La primera valida y lee `manifest.json`, `database.json` y `settings.json` y recorre todas las imágenes (comprueba CRC y descifrado); la segunda copia solo las imágenes de las tarjetas que se importan. Así no hace falta copiar a la caché un ZIP que puede pasar de 1 GB.
- **2026-10-11 · Todo o nada.** La parte de base de datos va en una sola transacción, y las imágenes se copian a una carpeta de preparación (`filesDir/.restore-<uuid>`) dentro de esa misma transacción. Solo se mueven a su sitio, renombrando en el mismo sistema de archivos, cuando la transacción se confirma. Cualquier fallo o cancelación deshace la base de datos y borra la preparación. En modo «reemplazar», las imágenes que ya no tienen tarjeta se borran después, con `deleteOrphans`, para no quedarse nunca sin las nuevas.
- **2026-10-11 · Reglas de «combinar».** El mismo UUID es la misma fila. Contactos y empresas se actualizan solo si el `updatedAt` de la copia es más reciente. Sectores, eventos y tarjetas no tienen fecha de actualización, así que si ya existen se conservan. Empresas (por nombre normalizado) y sectores (por nombre plegado) que ya existen con otro id se **enlazan**: el id de la copia se reasigna en todas sus referencias. Una empresa no se renombra si chocaría con otra existente. Si al combinar un contacto queda con varias tarjetas principales, se deja solo la más reciente. Restaurar dos veces la misma copia no cambia nada.
- **2026-10-11 · Validación estricta.**
  - Solo se admiten `manifest.json`, `database.json`, `settings.json`, `images/<id>/` y `images/<id>/(front|back)[_original].jpg`. Cualquier nombre con `..`, ruta absoluta o barra invertida se rechaza como dañado (*zip slip*).
  - Las rutas de imagen de cada tarjeta deben estar dentro de su propia carpeta (`cards/<id>/`).
  - Se comprueban ids duplicados y referencias entre entidades.
  - Las imágenes enderezadas son obligatorias; los originales lo son solo si `includes_originals` es verdadero.
  - Un ZIP sin `manifest.json` ni `database.json` no es una copia («no es una copia de seguridad»). Una copia con contenido inesperado está dañada.
  - Una contraseña incorrecta (`WRONG_PASSWORD` de zip4j) se distingue de un archivo dañado.
- **2026-10-11 · Migraciones de formato.** `BackupMigrations.upgrade` encadena pasos vN→vN+1 sobre el JSON antes de decodificarlo. La v1 es la actual y todavía no tiene pasos. Una versión mayor que la soportada se rechaza.
- **2026-10-11 · Ajustes.** `settings.json` se devuelve en el informe para que la app los aplique. Si no se puede leer, se ignora: los datos se restauran igualmente.

## Fase 9 · Copia de seguridad

- **2026-10-11 · Instantánea en una transacción de lectura.** La copia lee todas las tablas dentro de `withReadTransaction`: es coherente aunque se esté escribiendo (p. ej. la cola de OCR).
- **2026-10-11 · Verificación leyendo todo el archivo.** Tras escribir, se reabre el documento SAF y se descomprime (y descifra) entera, comparando entradas y tamaños. Si no coincide, se borra el archivo y se avisa: una copia que no se puede leer es peor que ninguna.
- **2026-10-11 · Una copia cancelada o fallida se borra** (`DocumentsContract.deleteDocument`).
- **2026-10-11 · Contraseña solo en memoria.** `CharArray` que se sobrescribe al terminar; mínimo 8 caracteres y repetida.
- **2026-10-11 · Mantenimiento de imágenes al arrancar.** `StorageMaintenance` borra carpetas sin tarjeta, escrituras interrumpidas y *staging* de restauraciones a medias; se ejecuta al iniciar el proceso, cuando no puede haber una captura en curso.
- **2026-10-11 · Restauración escrita en paralelo por un subagente** sobre el formato común (`BackupFormat`); el test de ida y vuelta une el exportador y el restaurador reales (sin y con cifrado, hash de imágenes, idempotencia).
- **2026-10-11 · Pendiente**: «cambiar de teléfono sin perder nada (probado entre dos emuladores)» no se ha ejecutado en emuladores en esta sesión; lo cubre el test de ida y vuelta entre dos bases de datos y almacenes independientes.

## Fase 10 · Duplicados y Acerca de

- **2026-10-11 · «Posibles duplicados» con las reglas del aviso al guardar.** `DuplicateScanner` usa las mismas reglas y puntuaciones que `DuplicateDetector` (≥ 55), expuestas como `internal` (`normalise`, `bestReason`) sin cambiar `find`. Cada pareja aparece una vez, la mejor primero.
- **2026-10-11 · Comparación por grupos, no todos contra todos.** Se agrupan los contactos por las claves que exige alguna regla (email, últimos 9 dígitos de cada teléfono, nombre + apellido, nombre + empresa, apellido + empresa) y solo se comparan dentro de cada grupo. Un test comprueba que da lo mismo que comparar todas las parejas; 5 000 contactos tardan bastante menos de 2 s.
- **2026-10-11 · Se conserva el contacto más antiguo.** Al tocar una pareja se abre la fusión con el de `createdAt` menor como destino; la lista se recalcula al volver.
- **2026-10-11 · Licencias en un JSON escrito a mano** (`res/raw/third_party_licenses.json`, leído con `org.json`: la app no tiene el plugin de serialización). Las entradas de un solo sabor llevan `flavour` y «Acerca de» muestra solo las del sabor instalado (por el motor OCR: ML Kit → `play`, Tesseract → `foss`). Sin enlaces abiertos desde la app: las URL se muestran como texto.

## Fase 10 · Seguridad, ajustes y pulido

- **2026-10-11 · Bloqueo con `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`.** Es la única combinación con PIN/patrón/contraseña del sistema que funciona desde Android 10 (minSdk 29). El bloqueo protege la pantalla, no los datos en reposo (para eso está el cifrado de la copia).
- **2026-10-11 · 30 s de gracia.** Se bloquea al volver tras más de 30 s en segundo plano (medido con `elapsedRealtime`), no al ir al selector de archivos, a compartir o al diálogo de contactos. Al arrancar en frío con el bloqueo activo, la app empieza bloqueada. Activarlo no bloquea la sesión actual; desactivarlo desbloquea al momento.
- **2026-10-11 · El bloqueo se superpone, no sustituye.** La pantalla de bloqueo es opaca y tapa la app, que queda fuera del árbol de accesibilidad (`clearAndSetSemantics`). Así no se pierden la navegación ni los resultados pendientes (SAF, cámara) al desbloquear. `MainActivity` pasa a ser `FragmentActivity`, que es lo que pide `BiometricPrompt`.
- **2026-10-11 · Sin bloqueo de pantalla en el teléfono no hay bloqueo de la app.** El interruptor se desactiva si `BiometricManager` no puede autenticar. Si el usuario quita el bloqueo del teléfono después de activarlo, al pulsar «Desbloquear» se desactiva la opción en vez de dejarle fuera de sus propias tarjetas: sin credencial no hay nada que comprobar.
- **2026-10-11 · Permiso `USE_BIOMETRIC`.** Lo declara androidx.biometric (dependencia aprobada en docs/01 para este bloqueo) y `BiometricPrompt` no funciona sin él. Es normal: se concede al instalar y no da acceso a datos. Se añade a la lista blanca de `verifyPermissions`, como los de WorkManager en la fase 3; CLAUDE.md y docs/01 lo recogen. `USE_FINGERPRINT` (solo Android ≤ 9) se elimina del manifest.
- **2026-10-11 · Ajustes que no viajan en la copia.** Bloqueo, «ocultar en recientes», «conservar originales» y umbral son del dispositivo: en `settings.json` solo va la vista de Tarjetas.
- **2026-10-11 · «Conservar originales» desactivado.** Las capturas nuevas guardan el original solo mientras dura la captura (para el recorte manual) y lo borran al guardar la tarjeta. Al desactivarlo se pide confirmación y se borran los originales ya guardados. Las rutas siguen en la base de datos (`frontOriginal` no admite nulos): un original que falta no rompe nada, porque solo se usa en la captura. La copia de seguridad declara `includes_originals = false` si falta alguno; si no, la restauración exigiría archivos que no existen y la daría por dañada.
- **2026-10-11 · Umbral de confianza en Ajustes.** Va de 0,30 a 0,90 en pasos de 0,05 (0,60 por defecto). La revisión lo lee al abrir cada tarjeta y el deslizador lo guarda al soltarlo.
- **2026-10-11 · «Reindexar búsqueda».** Reconstruye `contact_fts` en una transacción y devuelve `DomainResult` (antes `Unit`), para que la pantalla pueda decir si ha fallado.
- **2026-10-11 · APK de release solo ARM.** `abiFilters` arm64-v8a y armeabi-v7a en el tipo release: los teléfonos son ARM y las bibliotecas x86/x86_64 solo sirven para emuladores. Las compilaciones debug conservan todas las ABI para los emuladores. Tamaño (sin firmar): play 45 → 24,5 MB, foss 38 → 23,6 MB. En la fase 11 Google Play recibirá un AAB y dividirá por ABI él solo.
- **2026-10-11 · Rendimiento medido en la JVM.** El test de 5 000 contactos usa el mismo SQLite empaquetado que el teléfono, pero en la máquina de CI. El presupuesto de 100 ms se aplica a la mediana con mucho margen. La fluidez del scroll y los tiempos en un teléfono real quedan como comprobación manual (no se han usado los dispositivos del usuario).
- **2026-10-11 · Accesibilidad.** Las filas de ajustes son un único interruptor para TalkBack (`toggleable` con `Role.Switch`). Los iconos sin descripción son decorativos y van junto a un texto, y los campos dudosos lo dicen también con texto. La pantalla de bloqueo oculta la app al lector de pantalla.

## Fase 11 · Publicación

- **2026-10-11 · Licencia GPL-3.0-or-later**, como el resto de la familia Ultimate (UltimateGallery, UltimateDeck…). Las dependencias son Apache-2.0 o BSD, compatibles con ella. ML Kit (solo `play`) se distribuye con sus términos propios, igual que en las apps hermanas con flavour de Play; el flavour `foss` no lo incluye.
- **2026-10-11 · F-Droid distribuye nuestro APK** (`Binaries` + `AllowedAPKSigningKeys`), como UltimateDeck, porque la compilación de `foss` es reproducible: probado en local con dos árboles, sale el mismo SHA-256. El `prebuild` borra las líneas de ML Kit del catálogo y de `:ocr` para que el escáner no vea dependencias privativas declaradas. Esto no cambia el APK `foss`, también comprobado.
- **2026-10-11 · Categorías de F-Droid** `Contact` y `OCR`, de la lista vigente de fdroiddata (`config/categories.yml`).
- **2026-10-11 · App Bundle solo para `play`.** La Release adjunta el AAB firmado con la misma clave, que en Play será la clave de subida. F-Droid y GitHub siguen con APK.
- **2026-10-11 · Icono de tienda** generado a partir del vector del icono adaptativo: zona visible de 72 dp sobre el fondo, a 512 px. No es un diseño nuevo.
- **2026-10-11 · Sin capturas de pantalla.** Hacerlas exige ejecutar la app en un dispositivo o emulador, y no se han usado los del usuario. Quedan en la lista de pendientes manuales.

