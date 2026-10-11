# 01 · Arquitectura

## Principios

1. **Local-first**: la BD y las fotos son la única fuente de verdad. Sin red.
2. **Capas con dependencias hacia dentro**: `app → data/ocr → domain`.
3. **El OCR es un detalle intercambiable** detrás de `OcrService`; la extracción de campos es lógica de dominio común a ambos motores.
4. **Captura rápida y revisión lenta están desacopladas** mediante una cola persistente (estado de la `BusinessCard` + WorkManager).

## Módulos Gradle

```
:app      UI (Compose), navegación, ViewModels, DI (Hilt), WorkManager workers, CameraX
:domain   Kotlin puro: modelos, casos de uso, extractor de campos, duplicados, interfaces (repos, OcrService)
:data     Room + FTS4, repositorios, almacenamiento de imágenes, backup/restore, VCF/CSV/XLSX, escritura en contactos
:ocr      Implementaciones de OcrService por flavour (src/play → ML Kit, src/foss → Tesseract) + preprocesado de imagen
```

- Flavour dimension única `distribution` con `play` y `foss` en `:app` y `:ocr`. `:data` y `:domain` no tienen flavours.
- `:data` y `:domain` no declaran flavours, así que `:app` los consume sin configuración extra (`missingDimensionStrategy` solo haría falta si un módulo tuviera una dimensión que `:app` no tiene).
- Catálogo de versiones en `gradle/libs.versions.toml`.

## Paquetes (por módulo)

```
:domain  model/ · repository/ · usecase/ · extract/ (CardFieldExtractor, dictionaries) · dedupe/ · ocr/ (OcrService, OcrResult)
:data    db/ (entities, daos, migrations, fts) · repo/ · storage/ (ImageStore) · backup/ · export/ (vcf, csv, xlsx) · contacts/ (PhoneContactsWriter)
:ocr     processing/ (CardDetector, ImagePreprocessor) · play/MlKitOcrService · foss/TesseractOcrService
:app     ui/<feature>/ · navigation/ · work/ · di/ · settings/
```

## Flujo de datos de una captura

```
CameraX capture ─► ImageStore.saveOriginal ─► CardDetector (recorte+enderezado)
                                         └──► ImageStore.saveProcessed
   BusinessCard(status = PENDING) insertada
        │ (WorkManager: OcrWorker, una cola, un trabajo por tarjeta)
        ▼
   OcrService.recognize(processed) ─► OcrResult(blocks, lines, confidences)
        ▼
   CardFieldExtractor.extract(OcrResult) ─► ExtractedFields(+confianza por campo)
        ▼
   BusinessCard.rawOcrText + extractedJson, status = PENDING_REVIEW
        ▼
   Pantalla de revisión ─► SaveReviewedCardUseCase ─► Contact/Company/ContactEvent
```

En el flujo normal («Añadir tarjeta») el OCR se lanza en primer plano en cuanto se captura. En **Escaneo rápido** solo se guarda la imagen y se encola.

## Inyección de dependencias (Hilt)

- `DomainModule`: casos de uso.
- `DataModule`: Room, DAOs, repositorios, `ImageStore`.
- `OcrModule`: **uno por flavour** (`src/play` y `src/foss`) que enlaza `OcrService`.
- `DispatchersModule`: `@IoDispatcher`, `@DefaultDispatcher`.

## Dependencias aprobadas

| Dependencia | Uso | Flavour |
|---|---|---|
| AndroidX Core/Lifecycle/Activity/Navigation-Compose | base | ambos |
| Compose BOM + Material 3 | UI | ambos |
| Room (+ KSP) | BD, FTS4 | ambos |
| CameraX (core, camera2, lifecycle, view) | captura | ambos |
| WorkManager | cola OCR | ambos |
| Hilt (+ hilt-work, navigation-compose) | DI | ambos |
| Coil | miniaturas/visor | ambos |
| kotlinx-serialization-json | `database.json` del backup | ambos |
| zip4j | ZIP con AES-256 (backup cifrado) | ambos |
| androidx.biometric | bloqueo opcional | ambos |
| `com.google.mlkit:text-recognition` (modelo **empaquetado**, no el de Play Services) | OCR | `play` |
| Tesseract4Android | OCR | `foss` |
| JUnit, Truth/Kotest, Turbine, MockK, Robolectric, AndroidX Test | tests | ambos |
| `hilt-android-testing`, Compose `ui-test-junit4`/`ui-test-manifest` | tests (smoke de Hilt, navegación con Robolectric) | ambos |
| `compose.material:material-icons-core` | iconos de la barra inferior (solo el set pequeño; no `-extended`) | ambos |
| kotlinx-coroutines-android | `Dispatchers` inyectados, `Flow` | ambos |

### Herramientas de build (no van en el APK)

| Herramienta | Uso |
|---|---|
| Android Gradle Plugin, Kotlin (+ plugin Compose), KSP, plugin de Hilt | compilación |
| `org.jlleitschuh.gradle.ktlint` (ktlint) | estilo (`./gradlew ktlintCheck`), pedido por la fase 0 |

Cualquier otra requiere justificación aquí antes de añadirla. Excluidas por diseño: Firebase, Retrofit/OkHttp, Apache POI, OpenCV (salvo decisión en fase 2), libphonenumber (salvo decisión en fase 3).

## Hilos y rendimiento

- Un solo `OcrWorker` encadenado (`ExistingWorkPolicy.APPEND_OR_REPLACE`): evita saturar memoria con Tesseract.
- Miniaturas pregeneradas (≈400 px) para listas y tarjetero; el visor ampliado carga la imagen procesada.
- Sin Paging 3 de entrada: `LazyColumn` + `Flow` con consultas indexadas. Si en la fase 10 la medición con 5 000 contactos sintéticos lo exige, se añade Paging (anotándolo en *Desviaciones*).
