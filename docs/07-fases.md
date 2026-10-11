# 07 · Fases de desarrollo

Regla: **cada fase termina con el proyecto compilando en `play` y `foss`, tests verdes y docs actualizados.** No se empieza la siguiente hasta cerrar la anterior.

## Fase 0 · Esqueleto
- Proyecto multi-módulo (`:app`, `:domain`, `:data`, `:ocr`), flavours `play`/`foss`, catálogo de versiones, Hilt, tema M3 claro/oscuro, navegación inferior con pantallas vacías, `strings.xml` es/en.
- Lint, ktlint y CI local (`./gradlew` en un único comando: `./gradlew ci`).
- Manifest sin `INTERNET`; `allowBackup=false`; permiso `CAMERA`.
- **Cierre**: instala y arranca en ambos flavours; test de humo de Hilt.

## Fase 1 · Datos
- Entidades, DAOs, relaciones, migración inicial, siembra de sectores localizada, repositorios, `SearchIndexer` + FTS4.
- **Tests**: DAOs, FTS (acentos, prefijos), cascadas, siembra, `MigrationTestHelper`.
- **Cierre**: CRUD completo vía tests con datos sintéticos.

## Fase 2 · Captura e imágenes
- CameraX con guía, enfoque, flash, desenfoque; `ImageStore`; `CardDetector` + recorte manual; miniaturas.
- Spike: detector propio vs OpenCV con fotos reales (decisión documentada).
- **Tests**: `ImageStore` (atómico, huérfanos), `CardDetector` con imágenes sintéticas.
- **Cierre**: se pueden capturar anverso y reverso y guardar `BusinessCard(PENDING)`.

## Fase 3 · OCR y extracción
- `OcrService` en `domain`; `MlKitOcrService` (`play`), `TesseractOcrService` (`foss`); `OcrWorker` y cola con estados.
- `CardFieldExtractor` completo con diccionarios y corpus sintético (≥ 60 casos, 5 idiomas).
- **Medición** `play` vs `foss` documentada aquí (tabla de acierto por campo).
- **Cierre**: una foto produce campos con confianza en ambos flavours; el fallo de OCR deja la tarjeta en `OCR_ERROR` sin perder la imagen.

## Fase 4 · Revisión y guardado
- Pantalla de revisión, autocompletado de empresas, selector de evento y sector (con alta rápida), `SaveReviewedCardUseCase`, empresa auto-enlazada por `normalizedName`.
- Detección de duplicados (reglas, aviso, «ver existente», «guardar como nuevo»).
- «Guardar y escanear siguiente».
- **Tests**: reglas de duplicados, caso de uso de guardado, ViewModel de revisión.
- **Cierre**: flujo completo de una tarjeta, de la foto al contacto guardado.

## Fase 5 · Escaneo rápido y cola de revisión
- Modo Escaneo rápido con evento activo, contador, sin OCR en primer plano; banner «Pendientes de revisar: N»; cola de revisión con «Guardar y revisar siguiente»; reintento de `OCR_ERROR`; recuperación de `PROCESSING` tras cierre forzado.
- **Tests**: máquina de estados, reanudación del worker.
- **Cierre**: 50 capturas seguidas sin bloquear la UI, todas llegan a `PENDING_REVIEW`.

## Fase 6 · Navegación, búsqueda y filtros
- Tarjetas (tarjetero + lista), Eventos, Empresas, Sectores (CRUD), estadísticas, buscador global, búsqueda dentro de un evento, filtros combinables (nombre, apellidos, empresa, cargo, país, sector, evento, fecha).
- **Tests**: consulta dinámica con combinaciones, estados loading/empty/error.
- **Cierre**: el escenario «evento + sector + país» funciona sobre datos sintéticos.

## Fase 7 · Ficha, histórico, acciones y contactos
- Ficha completa y edición, visor ampliado, histórico de tarjetas (UI completa), fusión manual y desde aviso.
- Acciones rápidas, compartir como vCard, **escritura directa en contactos** (`WRITE_CONTACTS` bajo demanda) y exportación `.vcf` por selección/evento/empresa/sector.
- **Tests**: fusión, VCF (parser independiente), `PhoneContactsWriter` (instrumentado).
- **Cierre**: un contacto aparece en la agenda del teléfono con todos sus campos.

## Fase 8 · Exportación de datos
- CSV y XLSX (escritor propio), todo o filtrado.
- **Tests**: round-trip de lectura, inyección de fórmulas, caracteres especiales.

## Fase 9 · Copia de seguridad y restauración
- Exportar (con/sin originales, cifrado opcional), validar, versionar, restaurar en modo combinar o reemplazar, rollback, índice FTS.
- **Tests**: round-trip por hash, idempotencia, corrupto, versión futura, contraseña incorrecta, *zip slip*, volumen (2 000 contactos).
- **Cierre**: cambiar de teléfono sin perder nada (probado entre dos emuladores).

## Fase 10 · Seguridad, ajustes y pulido
- Bloqueo biométrico/PIN opcional, `FLAG_SECURE` opcional, reindexar, «Posibles duplicados», ajustes de originales, ajuste de umbral de confianza, accesibilidad, rendimiento con **5 000 contactos sintéticos** (búsqueda < 100 ms, scroll fluido), tamaño de APK por flavour.
- Revisión de permisos y manifest: comprobar que no hay `INTERNET`.

## Fase 11 · Publicación
- Firma, `versionCode`, metadatos F-Droid (flavour `foss` sin dependencias propietarias, verificar con `fdroid scanner`), ficha de Google Play (flavour `play`), política de privacidad («los datos no salen del dispositivo»), licencias de terceros en «Acerca de».

## Definición de «hecho» para cualquier fase
- [ ] Compila `assemblePlayDebug` y `assembleFossDebug`.
- [ ] Tests nuevos pasan y no rompen los anteriores.
- [ ] Loading / empty / error cubiertos en la UI tocada.
- [ ] Sin cadenas literales; strings en es y en.
- [ ] Sin permisos nuevos no previstos; sin dependencias nuevas sin justificar.
- [ ] `docs/` actualizados.

## Desviaciones
*(Anotar aquí cualquier cambio respecto a estos documentos, con fecha y motivo.)*

- **2026-10-11 · Fase 0 · `missingDimensionStrategy`**: no se usa. `:data` y `:domain` no tienen flavours y AGP los resuelve sin ella; `docs/01-arquitectura.md` corregido.
- **2026-10-11 · Fase 0 · `OcrEngineInfo`**: marcador provisional en `:domain` (`engineId` = `mlkit`/`tesseract`) que el `OcrModule` de cada flavour enlaza en Hilt, para probar el cableado por flavour antes de que exista `OcrService`. En la fase 3 se sustituye por `OcrService.engineId`.
- **2026-10-11 · Fase 0 · Permisos de red**: además de no declarar `INTERNET`, el manifest de `:app` los elimina con `tools:node="remove"` (por si una dependencia futura, p. ej. ML Kit, los arrastra) y la tarea `verifyNoInternetPermission` falla si aparecen en el manifest fusionado de cualquier variante.
- **2026-10-11 · Fase 0 · Memoria del build**: `gradle.properties` limita el daemon a 2 GB, compila Kotlin dentro del daemon y los tests corren en un único fork de 1 GB, para poder compilar en máquinas con poca RAM libre.
- **2026-10-11 · Fase 1 · Room 3**: se usa `androidx.room3` (API KMP con drivers) en lugar de Room 2; ver `docs/decisions.md`.
- **2026-10-11 · Fase 1 · `Sector.normalizedName`**: columna nueva con índice único para la unicidad sin mayúsculas ni acentos.
- **2026-10-11 · Fase 1 · Tokenizer FTS**: `unicode61` sin `remove_diacritics=2`; el plegado lo hace la app (`TextNormalizer`).
- **2026-10-11 · Fase 2 · Detector**: umbral de Otsu + componentes conexas + envolvente convexa en lugar de «bordes → contorno»; ver `docs/04` y `docs/decisions.md`.
- **2026-10-11 · Fase 2 · Recorte manual en captura**: además de en la revisión (fase 4), se ofrece «Reencuadrar» al confirmar cada cara.
- **2026-10-11 · Fase 2 · Disparo automático desactivado por defecto** (interruptor «Auto» en la captura).
- **2026-10-11 · Fase 3 · Corpus en texto compacto**: no en JSON de `OcrResult` (ilegible); `CardCorpus` lo convierte. Los `OcrResult` sí se serializan a JSON (`kotlinx-serialization`).
- **2026-10-11 · Fase 3 · `OcrEngineInfo` retirado**: sustituido por `OcrService.engineId`, como estaba previsto.
- **2026-10-11 · Fase 3 · Permisos de WorkManager**: `WAKE_LOCK` y `RECEIVE_BOOT_COMPLETED` se admiten (normales); `FOREGROUND_SERVICE` se elimina. `verifyNoInternetPermission` pasa a ser `verifyPermissions` (lista blanca).
- **2026-10-11 · Fase 3 · Sin estirado global de contraste** antes de binarizar para Tesseract (ver `docs/04`).
- **2026-10-11 · Fase 0 · JVM 21**: todos los módulos compilan con target Java/Kotlin 21 (JDK de desarrollo: Temurin 21).

## Resultados de medición
*(Fase 2: detector. Fase 3: acierto de OCR por campo `play` vs `foss`. Fase 10: rendimiento.)*

### Fase 3 · Extracción de campos (2026-10-11)

Sobre el corpus sintético (62 tarjetas, es/en/fr/de/it): **100 %** en todos los campos, y ≥ 95 % (patrones) / ≥ 80 % (resto) con los bloques en orden inverso. **Ojo**: las reglas se ajustaron sobre este mismo corpus, así que es una red contra regresiones, no una medida de acierto real.

### Fase 3 · `play` vs `foss` (pendiente)

La comparación de acierto por campo con ML Kit y con Tesseract sobre **fotos reales** del mismo conjunto de tarjetas no se ha podido hacer en esta sesión (sin fotos y sin usar los dispositivos del usuario). Procedimiento previsto: 30+ tarjetas fotografiadas con la app en cada flavour → exportar `rawOcrText` + `extractedJson` → comparar con lo que el usuario corrige en la revisión (fase 4) → tabla de acierto por campo aquí. Los tests instrumentados de humo (`MlKitOcrServiceTest`, `TesseractOcrServiceTest`) están escritos y se compilan en `ci`, pero solo corren en dispositivo/emulador (`./gradlew :ocr:connectedPlayDebugAndroidTest :ocr:connectedFossDebugAndroidTest`).

### Fase 2 · Detector de tarjeta (2026-10-11)

Medido sobre imágenes **sintéticas** generadas en los tests (`CardDetectorTest`, `CardImageProcessorTest`, `CaptureViewModelTest`): tarjeta clara u oscura, giros de −40° a +45°, perspectiva, vertical, fondo con textura y ruido, 800×600 y 2400×1800. Todas las esquinas se encuentran a menos del 2,5 % de la diagonal; sin tarjeta, tarjeta diminuta o cuadrado → no se detecta (recorte por guía). Coste: ~640 px de trabajo, apto para el análisis en vivo.

**Pendiente**: la medición con fotos reales (fondos y luces variados, no versionadas) que pide el spike no se ha podido hacer en esta sesión (no hay fotos ni se han usado los teléfonos del usuario). Criterio para reabrir la decisión: si con fotos reales falla en más del 20 % de los casos habituales (tarjeta sobre mesa, encuadrada con la guía), evaluar OpenCV en un spike aparte.
