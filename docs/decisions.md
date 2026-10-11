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
