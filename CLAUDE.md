# CLAUDE.md — UltimateVisitCards

App Android de tarjetero digital, offline y local-first. Lee primero `README.md` y `docs/`.
La documentación manda: si el código y `docs/` se contradicen, se arregla uno de los dos en el mismo cambio.

## Forma de trabajar

- **Una fase de `docs/07-fases.md` cada vez.** Al cerrar una fase el proyecto compila (`./gradlew assemblePlayDebug assembleFossDebug`) y los tests pasan (`./gradlew testPlayDebugUnitTest testFossDebugUnitTest`).
- No empieces la fase N+1 sin cerrar la N. No implementes todo en un único bloque.
- Antes de dar una fase por cerrada: tests nuevos de esa fase, estados loading/error/empty en la UI que toque y actualización de `docs/` si cambió una decisión.
- Cada decisión que se desvíe de `docs/` se anota en `docs/07-fases.md` (sección *Desviaciones*) con el motivo.

## Reglas duras

- **Nunca** añadas el permiso `INTERNET`, SDKs de analítica/crash, ni dependencias que lo requieran. Los datos no salen del dispositivo.
- Permisos permitidos: `CAMERA` y `WRITE_CONTACTS` (esta última solo se pide al usar «Añadir a contactos»). Sin permisos de almacenamiento: se usa SAF para exportar e importar.
- `android:allowBackup="false"` y `dataExtractionRules` que excluyan todo.
- Las imágenes viven en el almacenamiento privado de la app. Se comparten fuera solo con `FileProvider` y por acción explícita del usuario.
- No metas en el código sectores ni textos fijos: los sectores se siembran en BD en el primer arranque y son editables.
- Todo texto de UI va en `strings.xml` (`es` y `en`). Sin cadenas literales en Compose.
- La capa `domain` es Kotlin puro (sin imports de `android.*`).
- Dependencias mínimas. Antes de añadir una, justifícala en `docs/01-arquitectura.md`. Lista aprobada: ver ese documento.
- Los datos de prueba son **sintéticos**. No metas tarjetas ni datos personales reales en fixtures ni en commits.

## Convenciones

- Código e identificadores en inglés; documentación y mensajes de commit en español.
- IDs de entidad: UUID v4 como `TEXT`.
- Fechas: `Long` epoch millis UTC en BD; formatear solo en la capa de UI.
- Corrutinas: `Dispatchers` inyectados (nada de `Dispatchers.IO` fijo en la lógica).
- Errores: tipos sellados en `domain` (`Result`/`DomainError`), nunca excepciones que lleguen a la UI.
- Flavours: el código específico va en `src/play/` o `src/foss/`. Nada de `if (BuildConfig.FLAVOR == …)`.

## Comandos útiles

```
./gradlew assemblePlayDebug assembleFossDebug
./gradlew testPlayDebugUnitTest testFossDebugUnitTest
./gradlew connectedPlayDebugAndroidTest   # tests de Room y backup en dispositivo/emulador
./gradlew lint ktlintCheck
```
