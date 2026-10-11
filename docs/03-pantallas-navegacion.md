# 03 · Pantallas y navegación

Material 3, claro/oscuro (y colores dinámicos en Android 12+), responsive para móviles pequeños y grandes. Prioridades: velocidad, simplicidad, búsqueda, fiabilidad.

## Navegación inferior

`Tarjetas · Eventos · Empresas · Sectores · Ajustes`

Las rutas de captura, revisión y ficha se abren por encima de la barra inferior.

## Mapa de pantallas

```
Tarjetas (home)
 ├─ Buscador + chips de filtro ─► Filtros (hoja inferior)
 ├─ Vista tarjetero (rejilla con miniatura) | Vista lista compacta
 ├─ Banner «Pendientes de revisar: N» ─► Cola de revisión
 ├─ FAB «+»  ─► Captura
 └─ Ficha de contacto ─► Visor de imagen ampliada
                       └► Histórico de tarjetas
Captura (CameraX)
 ├─ Modo normal: anverso ─► ¿reverso? ─► Revisión ─► Guardar / Guardar y escanear siguiente
 └─ Modo Escaneo rápido: captura continua ─► contador ─► Terminar
Revisión (formulario con la foto arriba, campos dudosos resaltados, aviso de duplicado)
Eventos ─► Detalle de evento (con buscador propio) ─► Crear/editar evento
Empresas ─► Detalle de empresa (contactos) ─► Editar empresa
Sectores ─► Crear/renombrar/borrar sector
Ajustes
 ├─ Copia de seguridad (exportar, cifrado opcional)
 ├─ Restaurar copia (validar → resumen → confirmar)
 ├─ Exportar datos (VCF / CSV / XLSX; todo o filtrado)
 ├─ Seguridad (bloqueo biométrico/PIN)
 ├─ Reindexar búsqueda
 └─ Acerca de (licencias, flavour, motor OCR)
```

## Pantalla principal (Tarjetas)

- Buscador global siempre visible.
- Estadísticas sencillas: **Total contactos · Eventos · Empresas**.
- «Últimas añadidas» y accesos rápidos a eventos y sectores recientes.
- Selector de vista: tarjetero (visual) o lista compacta. Se recuerda la elección.
- Selección múltiple con pulsación larga → **Exportar / Añadir a contactos / Asignar evento**.

## Captura

- Vista previa con **guía rectangular** proporción 85×55 (apaisada; también acepta vertical).
- Detección automática del borde: se dibuja el cuadrilátero encontrado sobre la vista previa; el disparo (manual o automático al estabilizarse) recorta y endereza.
- Tras el anverso: **«Añadir reverso» / «Continuar sin reverso»**.
- Flash, enfoque por toque, indicador de desenfoque («Mueve el móvil, la imagen está borrosa»).
- **Escaneo rápido**: sin diálogos entre tarjetas. Disparo → guardado (original + enderezada) → siguiente. Interruptor «¿Tiene reverso?» por tarjeta. Contador visible. Se puede fijar un **evento activo** para toda la tanda.

## Revisión

Ruta `review/{cardId}`. En modo normal la captura lleva aquí al guardar la tarjeta; «Guardar y escanear siguiente» vuelve a la cámara; «Guardar» vuelve a donde se estaba. Mientras el OCR lee, el formulario ya se puede rellenar y al terminar solo se completan los campos que el usuario no ha tocado.

- Imagen de la tarjeta fija arriba (tocar para ampliar; alternar Anverso | Reverso).
- Campos: nombre, apellidos, cargo, empresa, país, teléfono, móvil, email, web, dirección, notas.
- Campos con confianza baja marcados visualmente; teclados adecuados (email, teléfono, URL).
- Sugerencias al escribir empresa (autocompletado desde empresas existentes) y país.
- Selector de **evento** y **sector** (con «crear nuevo» sin salir de la pantalla).
- Aviso de duplicado («Este contacto podría existir ya») con **Ver existente · Guardar como nuevo · Fusionar · Añadir como nueva tarjeta** (histórico). Fase 4: «Ver existente» (sus datos, sin salir de la revisión) y «Guardar como nuevo»; «Fusionar» y «Añadir como nueva tarjeta» llegan con la ficha y el histórico (fase 7).
- Acciones: **Guardar**, **Guardar y escanear siguiente**, **Guardar y revisar siguiente** (desde la cola).
- Si el OCR falló: formulario vacío con la foto visible y mensaje claro. No se pierde nada.

## Ficha de contacto

Secciones: Información personal · Empresa (sector, país) · Contacto · Procedencia (eventos, fecha) · Tarjeta (Anverso | Reverso, con pellizco para ampliar) · Notas.

Acciones rápidas, solo si hay dato: **llamar · email · abrir web · copiar email · copiar teléfono · compartir contacto (vCard) · añadir a contactos del teléfono**.

**Histórico**: si el contacto tiene varias tarjetas, carrusel con fecha y evento de cada una y «Añadir nueva tarjeta». Se puede marcar cuál es la principal y borrar una tarjeta antigua (con confirmación).

## Eventos y empresas

- Evento: nombre, fechas, lugar, notas, «N contactos». Su detalle incluye lista, buscador limitado al evento (chip «Solo en este evento» que se quita para volver a global), filtros y exportación del evento.
- Empresa: nombre, sector, país, web, notas y lista de sus contactos.

## Estados de UI (obligatorio en cada pantalla con datos)

`Loading` (esqueleto) · `Empty` (texto + acción, p. ej. «Aún no hay tarjetas. Escanea la primera») · `Error` (mensaje + reintentar) · `Content`. Todos probados con tests de UI/ViewModel.

## Accesibilidad y detalles

- `contentDescription` en iconos y miniaturas; objetivos táctiles ≥ 48 dp; contraste AA.
- Gestos con alternativa en botón.
- Opción en Seguridad: ocultar contenido en la vista de recientes (`FLAG_SECURE`).
