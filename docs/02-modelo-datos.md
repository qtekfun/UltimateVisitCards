# 02 · Modelo de datos

Room sobre SQLite. Todos los IDs son **UUID v4 en `TEXT`**. Fechas en epoch millis UTC. `schemaVersion = 1` con `exportSchema = true` y esquemas versionados en el repo.

## Diagrama

```
Sector 1───N Company 1───N Contact N───N Event   (puente: ContactEvent)
                              │
                              1
                              │
                              N
                         BusinessCard N───0..1 Event   (dónde/cuándo se obtuvo)
```

## Entidades

### Sector
| Campo | Tipo | Notas |
|---|---|---|
| id | TEXT PK | |
| name | TEXT | editable |
| normalizedName | TEXT | `name` plegado (minúsculas, sin acentos). **Índice único**: así «Electrónica» y «ELECTRONICA» chocan (NOCASE de SQLite solo pliega ASCII) |
| sortOrder | INTEGER | |
| createdAt | INTEGER | |

Siembra inicial en el primer arranque (localizada es/en, `RoomDatabase.Callback.onCreate`: una sola vez; si el usuario los borra no vuelven) con la lista de la spec: Astillero, Armador, Ship Management, Fabricante de motores, Equipamiento naval, Electrónica, Automatización, Ingeniería, Clasificación, Servicios marítimos, Logística, Distribuidor, Proveedor, Otro. Se pueden crear, renombrar y borrar. Al borrar un sector, `Company.sectorId` pasa a `NULL` (`ON DELETE SET NULL`).

### Company
| Campo | Tipo | Notas |
|---|---|---|
| id | TEXT PK | |
| name | TEXT | |
| normalizedName | TEXT | minúsculas, sin acentos, sin sufijo legal (GmbH, S.L., Ltd…). **Índice único** |
| sectorId | TEXT? FK → Sector | |
| country | TEXT? | nombre normalizado en inglés (p. ej. `South Korea`) |
| website | TEXT? | |
| notes | TEXT? | |
| createdAt / updatedAt | INTEGER | |

### Contact
| Campo | Tipo | Notas |
|---|---|---|
| id | TEXT PK | |
| firstName / lastName | TEXT | |
| jobTitle | TEXT? | |
| companyId | TEXT? FK → Company | `ON DELETE SET NULL` |
| country | TEXT? | del contacto; si falta, se hereda de la empresa en la UI |
| phone / mobile | TEXT? | |
| email | TEXT? | |
| website / address | TEXT? | |
| notes | TEXT? | participa en la búsqueda |
| exportedToPhoneAt | INTEGER? | marca de «añadido a contactos» (evita duplicar sin pedir `READ_CONTACTS`) |
| createdAt / updatedAt | INTEGER | |

Índices: `companyId`, `email`, `(lastName, firstName)`, `createdAt`.

### Event (Grupo / Feria)
| Campo | Tipo | Notas |
|---|---|---|
| id | TEXT PK | |
| name | TEXT | |
| location | TEXT? | |
| startDate / endDate | INTEGER? | |
| notes | TEXT? | |
| createdAt | INTEGER | |

El contador «127 contactos» se calcula con `COUNT` sobre `ContactEvent` (no se almacena).

### ContactEvent (N:M)
`contactId` FK, `eventId` FK, PK compuesta, `ON DELETE CASCADE` en ambas. Índice en `eventId`.

### BusinessCard
| Campo | Tipo | Notas |
|---|---|---|
| id | TEXT PK | |
| contactId | TEXT? FK → Contact | **`NULL` mientras está pendiente de revisión**; `ON DELETE CASCADE` |
| eventId | TEXT? FK → Event | dónde se obtuvo esta tarjeta (`ON DELETE SET NULL`) |
| frontOriginal / frontProcessed | TEXT | rutas relativas en el almacenamiento privado |
| backOriginal / backProcessed | TEXT? | |
| frontQuad / backQuad | TEXT? | esquinas detectadas (JSON) para poder reencuadrar desde el original |
| rawOcrText | TEXT | texto de ambas caras (indexado en FTS) |
| extractedJson | TEXT? | campos extraídos + confianza (para re-revisar sin repetir OCR) |
| ocrEngine | TEXT? | `mlkit` / `tesseract` (útil para depurar) |
| ocrStatus | TEXT | `PENDING`, `PROCESSING`, `PENDING_REVIEW`, `REVIEWED`, `OCR_ERROR` |
| isPrimary | INTEGER | tarjeta mostrada por defecto en la ficha (la más reciente) |
| capturedAt | INTEGER | fecha de captura (base del histórico) |
| createdAt | INTEGER | |

Índices: `contactId`, `ocrStatus`, `eventId`.

Regla clave: una tarjeta existe **antes** que su contacto. «Pendientes de revisar: 37» = `COUNT(ocrStatus IN (PENDING_REVIEW, OCR_ERROR, PENDING, PROCESSING))` sin `contactId`. La foto original nunca se pierde aunque el OCR falle.

## Búsqueda (FTS4)

Tabla virtual **independiente** `contact_fts` (no `contentEntity`, porque mezcla datos de varias tablas):

```
contact_fts(contactId UNINDEXED, name, firstName, lastName, company, jobTitle, country, sector,
            email, phone, notes, ocrText, events,
            tokenize=unicode61)
```

- El texto se **pliega en la app** (`TextNormalizer`: minúsculas, sin acentos, `ß→ss`, `ø→o`…) antes de indexar y antes de consultar. No se usa `remove_diacritics=2` porque exige SQLite 3.27 y Android 10 (minSdk 29) trae 3.22.
- Teléfonos: se indexan los grupos impresos, todos los dígitos y el número nacional (sin prefijo de 1–3 dígitos), para encontrar `+34 600 12 34 56` con `600123456`.
- `ocrStatus` se guarda como el nombre del enum (`TEXT`); un valor desconocido se lee como `PENDING`.

- `SearchIndexer` en `:data` reconstruye la fila de un contacto cuando cambia él, su empresa, su sector, sus eventos o sus tarjetas, dentro de la misma transacción.
- Consulta (`SearchQuery`): cada término plegado → `term*` (prefijo), separados por espacio (AND implícito). Ej.: `hyundai* ship*`. Solo sobreviven letras y dígitos: no se puede inyectar sintaxis FTS.
- Reconstrucción completa tras un restore y desde Ajustes («Reindexar»).

### Búsqueda dentro de un grupo y filtros combinables
Consulta construida dinámicamente (`ContactQueryBuilder` → `@RawQuery` con `observedEntities`, parámetros siempre enlazados). Nombre, apellidos y cargo se filtran con filtros de columna FTS (`firstName:ana* lastName:garcia*`), combinados con el texto libre en una sola expresión `MATCH`:

```
SELECT c.* FROM Contact c
 [JOIN contact_fts f ON f.contactId = c.id AND contact_fts MATCH :q]
 [JOIN ContactEvent ce ON ce.contactId = c.id AND ce.eventId = :eventId]
 [LEFT JOIN Company co ON co.id = c.companyId]
WHERE (:country IS NULL OR COALESCE(c.country, co.country) = :country)
  AND (:sectorId IS NULL OR co.sectorId = :sectorId)
  AND (:companyId IS NULL OR c.companyId = :companyId)
  AND (c.createdAt BETWEEN :from AND :to)
ORDER BY …
```

Filtros: nombre, apellidos, empresa, cargo, país, sector, evento, fecha. Se combinan con `AND`. El `eventId` fijado por la pantalla de un evento convierte la búsqueda global en búsqueda dentro del grupo; quitar ese chip vuelve a la global.

## Migraciones
Toda migración tiene test (`MigrationTestHelper`). El backup lleva su propia `backup_version`, independiente de la versión de Room.

| Versión | Cambio |
|---|---|
| 1 | Esquema inicial |
| 2 | `contact_fts` añade `firstName` y `lastName` (filtros de nombre). La tabla FTS se recrea vacía y `SearchIndexHealth` la reconstruye al arrancar (el índice es derivado; no se pierde nada). |

Al arrancar, `SearchIndexHealth` comprueba que el índice tiene una fila por contacto y, si no, lo reconstruye.
