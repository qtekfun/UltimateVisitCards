# 08 · Versiones y publicación

## Flujo de cada cambio

1. Rama por cambio (p. ej. `fase-1-datos`), commits pequeños.
2. PR contra `main` con `gh pr create`.
3. El workflow **CI** (`.github/workflows/ci.yml`) ejecuta `./gradlew ci` y un build de release sin firmar de `play` y `foss`. No se fusiona nada en rojo.
4. Merge en `main` (merge commit, conservando los commits de la rama).
5. Nueva versión: se sube `appVersion` en `gradle.properties` dentro del propio PR y, tras el merge, se crea el tag `vX.Y.Z` sobre `main`.
6. El workflow **Release** (`.github/workflows/release.yml`) comprueba que el tag coincide con `appVersion`, compila y firma los APK de release y publica la GitHub Release con `UltimateVisitCards-X.Y.Z-play.apk`, `UltimateVisitCards-X.Y.Z-foss.apk`, el App Bundle para Google Play `UltimateVisitCards-X.Y.Z-play.aab` y `SHA256SUMS`. La publicación en las tiendas está en [09-publicacion.md](09-publicacion.md).

## Esquema de versiones

- SemVer `X.Y.Z`, opcionalmente `X.Y.Z-rc.N` (se publica como *pre-release*).
- Mientras no haya v1: cada fase cerrada sube el **minor** (fase 0 → `0.1.0`, fase 1 → `0.2.0`…); los arreglos sueltos suben el **patch**.
- `versionCode = (X*10000 + Y*100 + Z) * 100 + N` (`N` = 99 en la versión final), derivado en `app/build.gradle.kts`. Ambos flavours comparten `applicationId` y `versionCode`: se distribuyen por canales distintos (Google Play / F-Droid y GitHub).

## Clave de firma

Una única clave de release (PKCS12) guardada fuera del repo en `~/keys/`; `*.jks` y `*.keystore` están en `.gitignore`. La contraseña **no** se guarda en ningún fichero del repo.

Crear la clave:

```
mkdir -p ~/keys && chmod 700 ~/keys
keytool -genkeypair -v -storetype PKCS12 \
  -keystore ~/keys/ultimatevisitcards-release.jks \
  -alias ultimatevisitcards -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=UltimateVisitCards"
chmod 600 ~/keys/ultimatevisitcards-release.jks
```

En PKCS12 la contraseña de la clave es la misma que la del almacén.

Subirla como secretos del repositorio (los valores se piden por teclado y no quedan en el historial de la shell):

```
base64 -w0 ~/keys/ultimatevisitcards-release.jks | gh secret set UVC_KEYSTORE_BASE64 -R qtekfun/ultimatevisitcards
gh secret set UVC_KEYSTORE_PASSWORD -R qtekfun/ultimatevisitcards
gh secret set UVC_KEY_PASSWORD -R qtekfun/ultimatevisitcards
gh secret set UVC_KEY_ALIAS -R qtekfun/ultimatevisitcards --body ultimatevisitcards
```

**Haz copia de seguridad de `~/keys/ultimatevisitcards-release.jks` y de su contraseña** (gestor de contraseñas + copia offline): sin ellas no se pueden publicar actualizaciones de la app ya instalada.

Firmar en local (opcional):

```
UVC_KEYSTORE_FILE=~/keys/ultimatevisitcards-release.jks UVC_KEY_ALIAS=ultimatevisitcards \
UVC_KEYSTORE_PASSWORD=… UVC_KEY_PASSWORD=… ./gradlew assemblePlayRelease assembleFossRelease
```

Sin `UVC_KEYSTORE_FILE` los APK de release salen sin firmar; el workflow de Release falla si faltan los secretos (nunca se publica con la clave de debug).
