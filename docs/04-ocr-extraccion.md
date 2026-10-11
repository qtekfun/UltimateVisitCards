# 04 · Captura, OCR y extracción de campos

Objetivo: reconocer tarjetas con diseños arbitrarios en inglés, español, francés, alemán e italiano, **sin red**, y proponer los campos con una confianza que la UI pueda mostrar.

## 1. Captura y preparación de imagen

1. `ImageCapture` de CameraX (modo calidad, sin flash por defecto).
2. Se guarda el **original** reducido a ≤ 2400 px de lado largo (JPEG q88, sin EXIF de ubicación).
3. `CardDetector` (en `:ocr/processing`, Kotlin puro sobre arrays de luminancia; `Bitmaps.kt` convierte en los bordes):
   - reducción a ~640 px → desenfoque gaussiano 5×5 → umbral de Otsu probado en **ambas polaridades** (tarjeta clara sobre mesa oscura y al revés) → apertura/cierre morfológicos → componentes conexas;
   - por componente grande (6–97 % del encuadre, sin tocar 3 o más bordes de la imagen, que sería la mesa): envolvente convexa → simplificación a 4 vértices (Visvalingam) → cuadrilátero convexo con proporción de tarjeta (≈1,25–2,1 en cualquier orientación), que cubra la envolvente (≥ 92 %) y sea sólido (≥ 72 %); gana la mejor puntuación;
   - esquinas en sentido horario empezando por la del borde superior más horizontal: la tarjeta conserva la orientación que tiene en la foto;
   - transformación de perspectiva (homografía + muestreo bilineal);
   - si no encuentra un cuadrilátero fiable, **recorta por la guía rectangular** (la misma que se dibuja, centrada, 85×55, 88 % del encuadre) y avisa para que se reencuadre a mano. El recorte manual (cuatro esquinas arrastrables y «Usar la foto completa») está en la pantalla de captura y reutilizable en la revisión.
   - En vivo, el mismo detector y un medidor de nitidez (varianza del laplaciano, media de 5 fotogramas) se ejecutan sobre el plano Y de `ImageAnalysis`: se dibuja el cuadrilátero, se avisa del desenfoque y, con «Auto» activado, se dispara cuando la tarjeta lleva 0,7 s quieta y nítida.
4. Se guarda la **imagen enderezada** (≤ 1600 px, JPEG q90) y las esquinas (`quad`) para reencuadrar sin repetir la captura.
5. `ImagePreprocessor` genera la entrada del OCR: reescalado a ~300 dpi equivalentes, mejora de contraste, binarización adaptativa (solo para Tesseract; ML Kit recibe color/gris).

> Punto abierto (fase 2): si el detector propio no es suficientemente robusto, evaluar OpenCV. Se mide con un conjunto de fotos reales (no versionadas) con fondos y luces variados.

## 2. Contrato `OcrService`

```kotlin
// :domain
interface OcrService {
    val engineId: String                       // "mlkit" | "tesseract"
    suspend fun recognize(image: OcrImage, hints: OcrHints = OcrHints()): OcrResult
}

data class OcrResult(val text: String, val blocks: List<OcrBlock>)
data class OcrBlock(val text: String, val lines: List<OcrLine>, val box: NormRect)
data class OcrLine(val text: String, val box: NormRect, val confidence: Float?, val heightPx: Float)
```

- Coordenadas normalizadas (0–1) para que el extractor no dependa del motor.
- `confidence` puede ser `null` (el extractor lo trata como «desconocida»).
- El motor se enlaza por flavour en Hilt. **El extractor y los tests de extracción no conocen ningún motor.**

### `play` — ML Kit
`com.google.mlkit:text-recognition` (reconocedor latino **empaquetado**, no el descargable vía Play Services, para que funcione offline desde la primera ejecución).

### `foss` — Tesseract4Android
- `traineddata` (variante *fast*) de `eng`, `spa`, `fra`, `deu`, `ita` empaquetados en `assets`, copiados al almacenamiento privado en el primer uso.
- Ejecución con `eng+spa+fra+deu+ita` a la vez (ajustable). `PageSegMode` = `SPARSE_TEXT` (las tarjetas no son páginas de texto continuo); reintento con `AUTO` si el resultado es pobre.
- Una sola instancia reutilizada dentro del `OcrWorker`; se libera al terminar la cola.

### Añadir idiomas después
Latin: añadir `traineddata` (foss) y, si hace falta, un diccionario en `extract/`. Otros alfabetos (CJK, cirílico…): nuevo reconocedor + diccionarios + tests; no requiere cambios en `domain`.

## 3. `CardFieldExtractor` (dominio, determinista)

Entrada: `OcrResult` de anverso (+ reverso). Salida: `ExtractedFields` con **valor + confianza (0–1) + origen (línea)** por campo.

### Pasos
1. **Normalizar**: Unicode NFC, espacios y guiones raros, separar líneas por bloque; unir las dos caras (el reverso suele traer la versión en otro idioma; no duplicar).
2. **Detectores por patrón** (consumen la línea y la marcan como usada):
   - **Email**: regex tolerante a `@` leída como `(at)`/`©` y a espacios espurios; se valida con `lower().trim()`.
   - **Web**: `www.`, `https?://`, dominios sin esquema; se descarta el dominio de email público (gmail, outlook…).
   - **Teléfonos**: secuencias con `+`, paréntesis, espacios, guiones y puntos; etiquetas por idioma (`T`, `Tel`, `Phone`, `Tél`, `Telefon`, `Mobil`, `M`, `Cell`, `Móvil`, `Cellulare`, `Fax`).
     - **Móvil vs fijo**: primero la etiqueta; si no hay, tabla corta de prefijos móviles (ES 6/7, DE 015/016/017, FR 06/07, IT 3, UK 07).
     - **Fax**: se descarta del campo teléfono (queda en `rawOcrText`).
     - Se normaliza a formato internacional si aparece prefijo de país.
   - **Dirección**: líneas con código postal (patrones por país) o palabras de vía (`Str.`, `Straße`, `Calle`, `Avda.`, `Rue`, `Via`, `Road`, `Ave`, `Plaza`, `Platz`…); se agrupan líneas contiguas.
   - **País**: diccionario de nombres en 5 idiomas + ISO-2/3 → nombre normalizado en inglés (`Korea, Republic of` → `South Korea`); pista adicional por prefijo telefónico y código postal.
3. **Cargo**: diccionario multilingüe (Director, Manager, Gerente, Geschäftsführer, Directeur, Responsabile, CEO, Sales, Ingeniero, Prokurist…) con coincidencia por palabras; puede ocupar 1–2 líneas.
4. **Empresa**: puntuación por
   - sufijo legal (GmbH, AG, S.L., S.A., SLU, Ltd, LLC, Inc, Co., SAS, SARL, S.p.A., S.r.l., B.V., N.V., A/S, Oy…),
   - coincidencia con el dominio del email/web,
   - tamaño de fuente (alto de línea) y posición (línea grande y aislada, a menudo arriba),
   - presencia de logotipo (zona sin texto reconocible) — solo como señal débil.
5. **Nombre**: líneas restantes, 2–4 palabras capitalizadas, sin dígitos, no coincidentes con cargo ni empresa; bonus por tamaño de letra y proximidad al cargo. Se separa en `firstName`/`lastName` con reglas por idioma (partículas `de`, `van`, `von`, `di`, `da`, `del`, apellidos compuestos hispanos). Prefijos `Dr.`, `Prof.`, `Ing.`, `Dipl.-Ing.` se quitan del nombre (se pueden conservar en una nota).
6. **Confianza por campo** = mezcla de: patrón fuerte (email/URL → alta), confianza del motor en esa línea (si existe) y márgenes entre candidatos (si hay empate, baja). Por debajo de un umbral configurable el campo aparece **resaltado** en la revisión.
7. Resto de líneas no asignadas → se sugieren como candidatas a nota/otro campo, nunca se pierden (siguen en `rawOcrText`).

### Principios
- **No asumir posiciones fijas** de los campos; la posición es solo una señal.
- **Nunca inventar**: ante la duda, dejar el campo vacío con confianza 0 antes que rellenarlo mal.
- Diccionarios en recursos de datos (`extract/dictionaries/*.txt`/`.json`), versionados y testeados; no embebidos en código.

## 4. Cola y estados (WorkManager)

```
PENDING ─► PROCESSING ─► PENDING_REVIEW ─► REVIEWED
                 └──────► OCR_ERROR ──(reintentar)──► PENDING
```

- `OcrWorker` procesa de una en una. Idempotente: si la app muere en `PROCESSING`, al arrancar se devuelve a `PENDING`.
- Tras 2 fallos → `OCR_ERROR` (la imagen se conserva; el usuario rellena a mano).
- La UI observa los conteos por estado en `Flow` (banner «Pendientes de revisar: N»).

## 5. Pruebas

| Qué | Cómo |
|---|---|
| Extractor | Corpus **sintético** de `OcrResult` (JSON) por idioma y por diseño (≥ 60 casos): esperado vs obtenido, con métricas por campo |
| Móvil/fijo, país, dirección | Tests parametrizados por idioma |
| `CardDetector` | Imágenes sintéticas generadas en test (rotadas, con fondo) |
| `OcrService` | Test instrumentado de humo en cada flavour con una imagen generada con texto conocido |
| Regresión | Cada bug de extracción añade un caso al corpus antes de arreglarse |

Medición comparativa `play` vs `foss` sobre el mismo corpus de imágenes, documentada en `docs/07-fases.md` (fase 3).
