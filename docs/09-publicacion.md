# 09 · Publicación (Google Play y F-Droid)

Lo que hay en el repo y los pasos que faltan, que exigen las cuentas del autor y no se pueden automatizar desde aquí.

## Qué hay preparado

| Pieza | Dónde |
|---|---|
| Licencia GPL-3.0-or-later | `LICENSE`; también en «Acerca de» |
| Política de privacidad (en/es) | `PRIVACY.md`. URL pública: `https://github.com/qtekfun/ultimatevisitcards/blob/main/PRIVACY.md` |
| Changelog | `CHANGELOG.md` |
| Textos de tienda en/es | `fastlane/metadata/android/{en-US,es-ES}/` (título, descripción corta ≤ 80, descripción larga, `changelogs/<versionCode>.txt`, icono 512 px) |
| Capturas en/es (8 por idioma, Pixel 8, 1080×2400) | `fastlane/metadata/android/{en-US,es-ES}/images/phoneScreenshots/` |
| Receta de F-Droid | `fdroid/com.qtekfun.ultimatevisitcards.yml` |
| APK firmados (play y foss), AAB de play y `SHA256SUMS` | Cada GitHub Release (workflow Release) |

Certificado de firma (SHA-256): `bc0cacafb9b3fe33b95710f5fac4ec544156c5cc1de876b4c2ad2a3cffab7d51`.

Cada versión nueva añade `fastlane/metadata/android/*/changelogs/<versionCode>.txt` y su sección en `CHANGELOG.md`. El `versionCode` se calcula como `(X*10000 + Y*100 + Z) * 100 + 99`; por ejemplo, 0.12.0 → `120099`.

## F-Droid (flavour `foss`)

**Comprobado en local con fdroidserver 2.x:**
- `fdroid scanner` sobre los APK publicados:
  - `foss`: sin problemas.
  - `play`: 28 avisos. Es lo esperado, porque son las clases de ML Kit.
- `scanner.scan_source` sobre el árbol con el `prebuild` de la receta aplicado: **0 problemas**. Solo retira el wrapper de Gradle, como hace con todas las apps.
- **Reproducibilidad.** El APK `foss` sin firmar, compilado con el `prebuild` en otra carpeta, es **idéntico byte a byte** al del árbol normal (mismo SHA-256). Por eso la receta usa `Binaries` + `AllowedAPKSigningKeys`: F-Droid compila, compara con nuestro APK y, si coinciden, distribuye el nuestro con nuestra firma. **Sin comprobar todavía:** la compilación en el servidor de F-Droid (Debian, OpenJDK 21). Si allí no coincidiera, basta quitar `Binaries` y `AllowedAPKSigningKeys` para que F-Droid firme con su propia clave.

**Notas:**
- JitPack, de donde viene Tesseract4Android, está en la lista de repositorios admitidos del escáner.
- Los `traineddata` están versionados en `ocr/src/foss/assets/tessdata`, así que la compilación no descarga nada.
- ML Kit solo se declara para `play`. El `prebuild` borra esas líneas para que la receta no declare ninguna dependencia privativa.

**Pasos para publicar:**
1. Fork de `gitlab.com/fdroid/fdroiddata` (hay una copia local en `~/repos/fdroiddata`), rama `com.qtekfun.ultimatevisitcards`.
2. Copiar `fdroid/com.qtekfun.ultimatevisitcards.yml` a `metadata/`. Cambiar `commit: v0.12.0` por el hash completo del commit del tag (`git rev-parse v0.12.0^{commit}`).
3. `fdroid readmeta && fdroid lint com.qtekfun.ultimatevisitcards && fdroid build -v -l com.qtekfun.ultimatevisitcards`. Esta última necesita el entorno de compilación de F-Droid; se puede omitir y dejar que lo haga la CI del MR.
4. Abrir el MR y responder a los revisores.

## Google Play (flavour `play`)

**Pasos en Play Console** (exigen la cuenta de desarrollador del autor):
1. **Crear la app:** nombre «UltimateVisitCards», idioma predeterminado español (España) e inglés (EE. UU.) como traducción, gratuita, aplicación (no juego).
2. **Firma de apps de Play.** Subir `UltimateVisitCards-X.Y.Z-play.aab` de la release. La clave de `~/keys` queda como **clave de subida** y Google genera la de distribución. Desde ese momento, los APK de GitHub y los de Play tienen firmas distintas: no se puede actualizar uno con el otro sin desinstalar. Hay que avisarlo en el README.
3. **Ficha:** textos de `fastlane/metadata/android/*`, icono `images/icon.png` y capturas `images/phoneScreenshots/`. Falta la **imagen destacada** de 1024×500.
4. **Política de privacidad:** la URL de `PRIVACY.md`.
5. **Seguridad de los datos.** La app no recoge ni comparte datos: no hay permiso `INTERNET` y nada sale del dispositivo. Por eso se responde «No» a recogida y a compartición. El cifrado en tránsito no aplica (no hay transmisión). Para el borrado de datos: desinstalar la app los borra.
6. **Clasificación de contenido:** cuestionario IARC, categoría «Utilidad / Productividad», sin contenido sensible → PEGI 3 / Everyone.
7. **Público objetivo:** adultos (18+); no está dirigida a menores.
8. **Permisos sensibles:** ninguno de los restringidos por Play. `WRITE_CONTACTS` se pide en tiempo de ejecución y su uso está explicado en la ficha y en la política.
9. **Anuncios:** no. **Acceso a la app:** sin restricciones (no hay cuentas).
10. **Pista:** empezar en «Prueba interna» con el AAB y promover a producción tras probarla en teléfonos reales.

El AAB solo incluye las bibliotecas ARM (`arm64-v8a`, `armeabi-v7a`), igual que los APK de release. Los Chromebook x86 ejecutan apps ARM mediante traducción.

## Pendiente manual (no se puede hacer desde aquí)

- Imagen destacada de 1024×500 para Play.
- Cuenta de Play Console y ficha.
- MR en fdroiddata.
- Prueba en teléfono real antes de producción. Ver la lista de pendientes en docs/07.

## Cómo se hicieron las capturas

Los datos son **sintéticos**: empresas y personas inventadas, teléfonos ficticios y dominios `.example`. Se cargan en la app por el camino normal, restaurando una copia de seguridad. Pasos:

1. Generar las copias:
   ```
   UVC_SCREENSHOT_DIR=/tmp/shots ./gradlew :data:testDebugUnitTest --tests '*ScreenshotBackupGenerator'
   ```
   Sin esa variable, la prueba se omite. Salen dos copias, `screenshots-es.zip` y `screenshots-en.zip`; los nombres de sector coinciden con los que la app siembra en cada idioma.
2. Instalar `play` debug y borrar los datos de la app. Para inglés, fijar el idioma de la app con `adb shell cmd locale set-app-locales com.qtekfun.ultimatevisitcards --locales en-US`.
3. Abrir la app (siembra los sectores en ese idioma). Ajustes → Restaurar copia → la copia del idioma → Combinar.
4. Barra de estado limpia con el modo demo de SystemUI (`sysui_demo_allowed`). En Android 17 no oculta los iconos de notificación.
5. Pantallas capturadas:
   1. Tarjetas;
   2. revisión con un campo dudoso;
   3. ficha;
   4. ficha con la tarjeta;
   5. búsqueda «naval»;
   6. detalle de feria;
   7. Ajustes;
   8. Acerca de.

