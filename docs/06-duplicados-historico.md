# 06 · Duplicados, fusión e histórico

## 1. Cuándo se comprueba
Al pulsar **Guardar** en la revisión (y también en importaciones de backup en modo combinar, solo para empresas/sectores). Antes de crear un contacto nuevo se buscan candidatos entre los existentes; no se bloquea nunca el guardado.

## 2. Reglas de coincidencia

Se normaliza antes de comparar: minúsculas, sin acentos, sin espacios dobles, sin puntuación; empresa sin sufijo legal; teléfono solo dígitos con prefijo de país opcional; email en minúsculas.

| Regla | Condición | Puntos |
|---|---|---|
| Email exacto | `email` igual | 100 |
| Teléfono/móvil | últimos 9 dígitos iguales (teléfono o móvil, cruzados) | 90 |
| Nombre + apellidos + empresa | los tres iguales | 95 |
| Nombre + apellidos | iguales, empresa distinta o ausente | 60 |
| Nombre + empresa | `firstName`+empresa iguales, apellido muy parecido (distancia de edición ≤ 1) | 70 |
| Apellido + inicial + empresa | p. ej. `J. Smith` vs `John Smith` en la misma empresa | 55 |

- Se toma el **máximo** por candidato. Se muestra aviso si hay algún candidato con ≥ 55.
- Se ordenan por puntuación y se muestran hasta 3.
- Nombres comunes sin ningún otro dato coincidente no pasan de 60 (aviso suave, no automático).

## 3. Interfaz del aviso

> **Este contacto podría existir ya.**  
> John Smith · ABC Marine · john@abcmarine.com (coincide el email)

Acciones:
1. **Ver contacto existente** (abre la ficha sin perder la revisión).
2. **Guardar como nuevo**.
3. **Fusionar**.
4. **Añadir como nueva tarjeta** (histórico): crea una `BusinessCard` nueva ligada al contacto existente y actualiza sus datos si el usuario lo elige.

## 4. Fusión

Pantalla comparativa campo a campo: izquierda = existente, derecha = nuevo.

- Por defecto se conserva el valor **no vacío**; si ambos tienen valor distinto, se elige el de la tarjeta **más reciente** y se ofrece el otro con un toque.
- Cargo: el más reciente pasa al campo principal; el anterior se anota en el histórico (cada `BusinessCard` conserva su `extractedJson`).
- Notas: se **concatenan** con separador y fecha, nunca se pierden.
- Eventos: unión de los de ambos.
- Tarjetas: todas las `BusinessCard` pasan al contacto resultante; se mantiene una sola `isPrimary` (la más reciente).
- Empresa: si difieren, se muestra la elección; si coinciden por `normalizedName`, se enlazan.
- El contacto descartado se elimina en la misma transacción que la fusión. Se ofrece «Deshacer» durante unos segundos: antes de fusionar se guarda una instantánea de ambos contactos en memoria para poder revertir.
- Se reindexa el FTS del contacto resultante.

También hay fusión manual desde la lista (seleccionar 2 contactos → «Fusionar») y una vista «Posibles duplicados» en Ajustes que usa las mismas reglas sobre toda la base.

## 5. Histórico de tarjetas (reencuentro con un contacto)

Un `Contact` tiene **N `BusinessCard`**, cada una con `capturedAt` y `eventId` («cuándo y dónde se obtuvo»).

Ejemplo:
```
John Smith — ABC Marine
 ├─ Tarjeta 2028 · Sales Director · SMM Hamburg 2028   ★ principal
 └─ Tarjeta 2026 · Sales Manager  · SMM Hamburg 2026
```

- La ficha muestra la tarjeta principal y un carrusel «Tarjetas (2)» con fecha y evento.
- «Añadir nueva tarjeta» abre la captura ligada a este contacto; tras el OCR la revisión **compara** los datos nuevos con los actuales y propone actualizar (cargo, teléfono, email…) sin obligar.
- Ninguna tarjeta antigua se elimina automáticamente. Borrar una requiere confirmación; no se puede borrar la única tarjeta de un contacto sin borrar el contacto.
- El contacto pertenece a los eventos de **todas** sus tarjetas (`ContactEvent` se actualiza al añadir tarjeta con evento).
- La búsqueda indexa el texto OCR de todas las tarjetas.
- El backup incluye todas las tarjetas e imágenes.

## 6. Pruebas

- Casos de la tabla de reglas (positivos, negativos y límites) en tests unitarios de `domain/dedupe`.
- Fusión: conserva todos los datos, concatena notas, une eventos y tarjetas, deja una sola principal.
- Histórico: añadir tarjeta a contacto existente actualiza `ContactEvent`, índice FTS y principal.
