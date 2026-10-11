# Privacy policy

*Español más abajo.*

UltimateVisitCards is a business card holder that works **entirely on your phone**. It has no servers, no accounts, no ads, no analytics and no telemetry. It does not even have permission to use the Internet: Android itself prevents it from sending anything anywhere.

## What data goes where

- **Your contacts, companies, events, sectors and card photos stay on the device**, in the app's private storage. Uninstalling the app deletes them.
- **Text recognition runs on the device.** The `play` version uses Google ML Kit with the model bundled in the app; the `foss` version uses Tesseract. Neither sends the photos anywhere.
- **Android's cloud backup is disabled**, so none of this is copied to Google or any other cloud by the system.
- **Backups you export** are `.zip` files saved where you choose. They contain all your data and, optionally, are encrypted with a password you choose (AES-256). Keep them somewhere safe.
- **Exports** (`.vcf`, `.csv`, `.xlsx`) and **sharing a contact** create files only when you ask, and only go to the app or folder you pick.
- **"Add to contacts"** writes the contact into your phone's address book only when you tap it. From there, your phone's contact sync (if you use one) works as usual.

## Permissions and why

| Permission | Why |
|---|---|
| Camera (`CAMERA`) | To photograph cards. Asked the first time you capture one. |
| Contacts, write only (`WRITE_CONTACTS`) | "Add to contacts". Asked only when you use it; if you refuse, you can export a `.vcf` instead. The app never reads your address book. |
| Biometrics (`USE_BIOMETRIC`) | The optional app lock (fingerprint, face or screen lock). Granted at install; it gives no access to data. |
| Run in the background (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`) | Declared by Android's WorkManager so the queue of cards waiting to be read finishes even if you leave the app or restart the phone. Granted at install; no access to data. |

No Internet, location, storage or phone permissions are requested. Files are opened and saved through the system picker.

## Contact

Questions or concerns: open an issue in the [project repository](https://github.com/qtekfun/ultimatevisitcards/issues).

---

# Política de privacidad

UltimateVisitCards es un tarjetero que funciona **entero en tu teléfono**. No tiene servidores, ni cuentas, ni anuncios, ni analítica, ni telemetría. Ni siquiera tiene permiso para usar Internet: el propio Android le impide enviar nada a ningún sitio.

## Qué datos van adónde

- **Tus contactos, empresas, eventos, sectores y fotos de tarjetas se quedan en el dispositivo**, en el almacenamiento privado de la app. Al desinstalarla se borran.
- **El reconocimiento de texto se hace en el dispositivo.** La versión `play` usa Google ML Kit con el modelo incluido en la app; la versión `foss` usa Tesseract. Ninguna envía las fotos a ningún sitio.
- **La copia en la nube de Android está desactivada**: el sistema no copia nada de esto a Google ni a ninguna otra nube.
- **Las copias de seguridad que exportas** son archivos `.zip` que se guardan donde tú eliges. Contienen todos tus datos y, si quieres, van cifradas con una contraseña tuya (AES-256). Guárdalas en un lugar seguro.
- **Las exportaciones** (`.vcf`, `.csv`, `.xlsx`) y **compartir un contacto** crean archivos solo cuando lo pides, y solo van a la app o carpeta que eliges.
- **«Añadir a contactos»** escribe el contacto en la agenda del teléfono solo cuando lo pulsas. A partir de ahí, la sincronización de contactos de tu teléfono (si usas una) funciona como siempre.

## Permisos y para qué

| Permiso | Para qué |
|---|---|
| Cámara (`CAMERA`) | Fotografiar tarjetas. Se pide la primera vez que capturas una. |
| Contactos, solo escritura (`WRITE_CONTACTS`) | «Añadir a contactos». Se pide solo al usarlo; si lo rechazas, puedes exportar un `.vcf`. La app nunca lee tu agenda. |
| Biometría (`USE_BIOMETRIC`) | El bloqueo opcional de la app (huella, cara o bloqueo de pantalla). Se concede al instalar y no da acceso a datos. |
| Trabajo en segundo plano (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`) | Los declara WorkManager de Android para que la cola de tarjetas pendientes de leer termine aunque salgas de la app o reinicies el teléfono. Se conceden al instalar y no dan acceso a datos. |

No se piden permisos de Internet, ubicación, almacenamiento ni teléfono. Los archivos se abren y se guardan con el selector del sistema.

## Contacto

Dudas o problemas: abre una incidencia en el [repositorio del proyecto](https://github.com/qtekfun/ultimatevisitcards/issues).
