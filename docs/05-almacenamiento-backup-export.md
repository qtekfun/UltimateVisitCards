# 05 · Almacenamiento, backup, restauración y exportaciones

## 1. Almacenamiento local de imágenes

```
filesDir/
  cards/<cardId>/front_original.jpg
  cards/<cardId>/front.jpg          (enderezada)
  cards/<cardId>/back_original.jpg  (opcional)
  cards/<cardId>/back.jpg           (opcional)
cacheDir/thumbs/<cardId>_front.jpg  (≈400 px; regenerables)
```

- Rutas **relativas** en BD (`cards/<id>/front.jpg`); la raíz sale de `Context.filesDir`.
- Todo en almacenamiento privado de la app. Nada en `MediaStore` ni en carpetas públicas.
- JPEG sin metadatos de ubicación. Escritura atómica (archivo temporal + `rename`).
- `ImageStore` es la única clase que toca el sistema de archivos; limpia huérfanos (archivos sin fila y filas sin archivo) en una tarea de mantenimiento.
- Borrar una tarjeta/contacto borra sus archivos. Las miniaturas se regeneran si faltan.
- Estimación de espacio con 500 tarjetas a dos caras: unas 1 000 imágenes × (≈0,5 MB original + ≈0,4 MB enderezada) ≈ 0,9 GB. Ajuste en Ajustes: «Conservar originales sin recortar» (activado por defecto; si se desactiva se borran y ya no se puede reencuadrar).

## 2. Copia de seguridad

**Exportar**: Ajustes → Copia de seguridad → *Exportar copia completa*. Se elige destino con SAF (`CreateDocument`), sin permisos de almacenamiento.

Nombre: `ultimatevisitcards_backup_YYYY-MM-DD.zip`

```
/manifest.json          { "backup_version": 1, "app_version": "...", "created_at": ..., "counts": {...}, "includes_originals": true, "encrypted": false }
/database.json          { contacts[], companies[], sectors[], events[], contactEvents[], businessCards[] }
/images/<cardId>/front.jpg, back.jpg, front_original.jpg, back_original.jpg
/settings.json          (preferencias no sensibles: vista, idioma, umbrales)
```

- JSON con `kotlinx-serialization`, **IDs UUID tal cual** (restaurar nunca regenera IDs).
- Las rutas en `database.json` son las mismas relativas del almacenamiento.
- Opción **«Incluir originales sin recortar»** (por defecto sí) para controlar el tamaño.
- **Cifrado opcional**: contraseña → ZIP con **AES-256** (zip4j), abrible con 7-Zip. La contraseña nunca se guarda. Aviso claro: «Si la pierdes, la copia no se puede recuperar».
- Escritura en streaming (no cargar todo en memoria), con barra de progreso y cancelación.
- Se verifica al terminar: el ZIP se vuelve a abrir y se comprueban recuento y tamaños.

## 3. Restauración

Ajustes → Restaurar copia (`OpenDocument`).

1. **Validar**: es un ZIP, existe `manifest.json`, esquema correcto, todas las imágenes referenciadas existen. Si está cifrado, pedir contraseña (contraseña incorrecta ≠ archivo corrupto: mensajes distintos).
2. **Comprobar versión**: `backup_version` mayor que la soportada → se rechaza con mensaje claro; menor → migrador en cadena (`v1→v2…`).
3. **Mostrar contenido**: «843 contactos · 376 empresas · 12 eventos · 14 sectores · 1 910 imágenes».
4. **Confirmar** y elegir modo:
   - **Combinar (por defecto)**: añade lo que no existe y **actualiza solo si el backup es más reciente** (`updatedAt`). No genera duplicados porque los IDs coinciden.
   - **Reemplazar todo**: borra la BD actual (con confirmación fuerte).
5. **Importar** en una transacción por lotes: sectores → empresas → eventos → contactos → relaciones → tarjetas; después copiar imágenes y regenerar miniaturas.
6. **Reconstruir** el índice FTS y recontar.
7. Si algo falla a medias: **rollback** de la BD y limpieza de archivos copiados. Informe final con resumen (añadidos, actualizados, omitidos).

Evitar duplicados accidentales: además del UUID, empresas por `normalizedName` y sectores por nombre (sin distinguir mayúsculas) se **enlazan** en lugar de duplicarse (se remapea el ID).

## 4. Exportación VCF (vCard 3.0)

- `FN`, `N` (apellidos;nombre), `ORG` (empresa), `TITLE`, `TEL;TYPE=WORK,VOICE` (teléfono), `TEL;TYPE=CELL` (móvil), `EMAIL;TYPE=WORK`, `URL`, `ADR;TYPE=WORK` (dirección + país), `NOTE`.
- Una sola vCard o un `.vcf` con varias. Escapado correcto de `,` `;` `\n`, plegado de líneas a 75 octetos, UTF-8, saltos `CRLF`.
- La foto de la tarjeta **no** se incrusta por defecto (tamaño y privacidad). Opción «Incluir miniatura de la tarjeta» desactivada por defecto.
- Selección: un contacto, varios, todos, por evento, por empresa o por sector (reutiliza los mismos filtros de la lista).
- Salida por SAF (`CreateDocument`) o compartir con `FileProvider` si el usuario lo pide.

## 5. Escritura directa en contactos del teléfono

- Botón «Añadir a contactos» en la ficha y en la selección múltiple.
- `PhoneContactsWriter` usa `ContentProviderOperation` en lote (`RawContacts`, `StructuredName`, `Phone`, `Email`, `Organization`, `Website`, `StructuredPostal`, `Note`).
- Permiso `WRITE_CONTACTS` **solo cuando el usuario lo usa**; si lo deniega, se ofrece el `.vcf` como alternativa.
- No se pide `READ_CONTACTS`: no se consulta la agenda. Para no duplicar, se guarda `exportedToPhoneAt` y se avisa «Ya lo añadiste el 12/10/2026. ¿Añadir otra vez?».
- Se crea en la cuenta local del teléfono (sin cuenta) o en la cuenta por defecto que decida el sistema.

## 6. Exportación CSV y XLSX

Ámbito: todos los contactos **o** el resultado filtrado/seleccionado.

Columnas (en este orden, cabecera localizada): `Nombre, Apellidos, Cargo, Empresa, Sector, País, Teléfono, Móvil, Email, Web, Dirección, Evento, Fecha, Notas`.

- **CSV**: UTF-8 **con BOM** (para que Excel abra bien los acentos), separador `,`, comillas RFC 4180; protección frente a inyección de fórmulas (celdas que empiezan por `=`, `+`, `-`, `@` se prefijan con `'`).
- **XLSX**: escritor mínimo propio (ZIP con `[Content_Types].xml`, `_rels`, `xl/workbook.xml`, `xl/worksheets/sheet1.xml`, `xl/styles.xml`). Cadenas como `inlineStr`, cabecera en negrita, fila congelada, anchos de columna razonables. Mismas protecciones. Validado abriéndolo con una librería independiente en tests.
- Si un contacto está en varios eventos, `Evento` los une con `; `.

## 6 bis. Pruebas obligatorias

- Round-trip: backup → restore en BD vacía → comparación total (entidades e imágenes por hash).
- Restaurar dos veces el mismo backup no cambia nada (idempotencia).
- Backup corrupto, versión futura, contraseña incorrecta, ZIP con ruta maliciosa (`../`, *zip slip*).
- VCF: validado contra parser independiente; casos con acentos, comas, saltos de línea y caracteres no latinos.
- CSV/XLSX: apertura de vuelta y comparación de celdas; casos con comillas, saltos de línea y fórmulas.
