# UltimateVisitCards

**Descargas:** APK firmados de cada versión en [Releases](https://github.com/qtekfun/ultimatevisitcards/releases):
- `play`: ML Kit.
- `foss`: Tesseract, sin dependencias privativas.

Comprueba la descarga con `SHA256SUMS`. Los APK de GitHub y los de Google Play no tienen la misma firma, así que no se puede actualizar uno con el otro.

Tarjetero digital Android, **100 % offline y local-first**, de la familia Ultimate.
Fotografías una tarjeta de visita, el OCR local extrae los campos, la revisas y se guarda en una base de datos que **sigue siendo tuya**: buscable, filtrable, exportable a la agenda del teléfono y recuperable con una copia de seguridad.

- Paquete: `com.qtekfun.ultimatevisitcards`
- minSdk 29 (Android 10) · targetSdk = última estable
- Stack: Kotlin, Jetpack Compose + Material 3, Room (+FTS4), CameraX, Hilt, Coroutines/Flow
- Idiomas de la interfaz: español e inglés (recursos desde el día 1)
- Sin backend, sin cuentas, sin sincronización cloud, **sin permiso INTERNET**

## Escenario que debe resolver de forma excelente

> Tres días en una feria, 100-150 tarjetas. Fotografiarlas rápido, clasificarlas por feria y encontrar meses después a alguien recordando solo parte del nombre, empresa, país, cargo o sector.
> Ej.: *Evento = SMM 2026 + Sector = Astillero + País = South Korea.*

Todo el diseño se mide contra este escenario: captura rápida (modo **Escaneo rápido**), revisión diferida (**pendientes de revisar: N**) y búsqueda/filtros combinables sobre miles de contactos.

## Decisiones cerradas en la entrevista

| Tema | Decisión |
|---|---|
| Nombre / ID | UltimateVisitCards · `com.qtekfun.ultimatevisitcards` |
| Distribución | Dos *flavours*: **`play`** (Google Play) y **`foss`** (F-Droid / GitHub) |
| OCR `play` | ML Kit Text Recognition (Latin), modelo **empaquetado** en la app (offline real) |
| OCR `foss` | Tesseract4Android con `traineddata` empaquetados: `eng`, `spa`, `fra`, `deu`, `ita` |
| Alfabetos | Solo latino. La arquitectura (`OcrService`) permite añadir otros después |
| Extracción de campos | Heurísticas + regex + diccionarios (determinista y testeable). Sin LLM |
| Captura | CameraX + guía rectangular + **recorte y enderezado automático** (con recorte manual de respaldo) |
| Agenda del teléfono | **Escritura directa** (`ContactsContract`, permiso `WRITE_CONTACTS` pedido bajo demanda) **+** exportación `.vcf` para lotes |
| Seguridad | Bloqueo biométrico/PIN **opcional** + backup **cifrado opcional** (ZIP AES-256). Ambos desactivados por defecto |
| Eventos | Many-to-many Contacto↔Evento desde ya (`ContactEvent`) |
| Empresas | Entidad propia; se crea/enlaza automáticamente desde el nombre de empresa del OCR |
| Histórico de tarjetas | **UI completa en la v1**: un contacto tiene N tarjetas, cada una con fecha y evento |
| Sectores | Configurables; se precarga una lista inicial editable en el primer arranque |
| XLSX | Escritor mínimo propio (ZIP + XML), sin Apache POI |
| Alcance | Spec completa del punto 32, dividida en fases siempre compilables |
| Entregable | Este paquete SDD para trabajar con Claude Code |

## Mapa del paquete

| Documento | Contenido |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Reglas de trabajo para Claude Code en este repo |
| [`docs/01-arquitectura.md`](docs/01-arquitectura.md) | Módulos, capas, flavours, DI, dependencias |
| [`docs/02-modelo-datos.md`](docs/02-modelo-datos.md) | Entidades Room, relaciones, FTS, estados |
| [`docs/03-pantallas-navegacion.md`](docs/03-pantallas-navegacion.md) | Pantallas, navegación y estados UI |
| [`docs/04-ocr-extraccion.md`](docs/04-ocr-extraccion.md) | Pipeline de captura, `OcrService` y extractor de campos |
| [`docs/05-almacenamiento-backup-export.md`](docs/05-almacenamiento-backup-export.md) | Imágenes, backup/restore, VCF, CSV, XLSX, contactos |
| [`docs/06-duplicados-historico.md`](docs/06-duplicados-historico.md) | Detección de duplicados, fusión e histórico |
| [`docs/07-fases.md`](docs/07-fases.md) | Plan de desarrollo por fases, tests y criterios de cierre |
| [`docs/decisions.md`](docs/decisions.md) | Decisiones tomadas durante el desarrollo y su motivo |
| [`docs/08-versiones-y-publicacion.md`](docs/08-versiones-y-publicacion.md) | Flujo PR → CI → merge → versión, versionado y clave de firma |
| [`docs/09-publicacion.md`](docs/09-publicacion.md) | Google Play y F-Droid: qué hay preparado y pasos pendientes |
| [`PRIVACY.md`](PRIVACY.md) · [`CHANGELOG.md`](CHANGELOG.md) | Política de privacidad y cambios por versión |

## Puntos abiertos (decidir en el spike de cada fase)

1. **Detector de bordes de la tarjeta**: implementación propia en Kotlin puro frente a OpenCV (más pesado). Decidir en la fase 2 con fotos reales.
2. **Teléfonos**: regex + tabla propia de prefijos móviles (ES/DE/FR/IT/UK) en la v1; `libphonenumber` solo si el corpus de pruebas lo justifica.
3. **Calidad Tesseract vs ML Kit**: medir ambos sobre el mismo corpus en la fase 3 y documentar la diferencia entre flavours.
4. **Originales**: se conserva el original reducido (≤2400 px) además de la imagen enderezada. Revisar el consumo de disco con 500+ tarjetas.
5. **Sin permiso INTERNET**: la spec admite un OCR online como último recurso; se descarta porque ambos motores funcionan offline. Si algún día se añade, será otro flavour.

## Licencia

UltimateVisitCards es software libre: [GNU GPL versión 3 o posterior](LICENSE). Las licencias de terceros se ven en la app, en Ajustes → Acerca de.
