# CI de Android

El workflow `.github/workflows/android.yml` compila en cada push a `main` y en cada PR, y sube
los APK/AAB como artefactos. Tambien se puede lanzar a mano ("Run workflow") eligiendo flavor.

## Firma: una clave POR APP

Cada app esta publicada en Play con su propio keystore. Si se firma con otro, Play rechaza el
bundle con "The Android App Bundle was signed with the wrong key".

| Flavor | Keystore | Alias | SHA1 del certificado |
|---|---|---|---|
| evenpadel | stapeKey | stape | 9F:41:39:86:08:85:35:E5:DD:75:89:50:BA:60:F7:FB:C8:66:BD:53 |
| resto | wiplay | wiplay | E9:FC:5B:AD:47:51:1B:A9:A6:9F:8B:A3:DE:89:32:2D:9C:CE:1B:9A |

`build.gradle` resuelve la firma de cada flavor en este orden:

1. `keystore.properties` -> `<flavor>.storeFile`, `<flavor>.storePassword`, ...
2. Entorno -> `<FLAVOR>_KEYSTORE_FILE`, `<FLAVOR>_KEYSTORE_PASSWORD`, `<FLAVOR>_KEY_ALIAS`, `<FLAVOR>_KEY_PASSWORD`
3. `keystore.properties` -> `storeFile`, `storePassword`, ... (fallback comun)
4. Entorno -> `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, ... (fallback comun)

Si un flavor no tiene clave, su debug usa la de depuracion por defecto y su release sale sin
firmar; el build no se rompe.

### keystore.properties (local)

```properties
evenpadel.storeFile=/ruta/a/stapeKey
evenpadel.storePassword=...
evenpadel.keyAlias=stape
evenpadel.keyPassword=...

storeFile=/ruta/a/wiplay
storePassword=...
keyAlias=wiplay
keyPassword=...
```

### Secrets de GitHub (CI)

En *Settings > Secrets and variables > Actions*:

| Secret | Contenido |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | keystore comun (`wiplay`) en base64: `base64 -i /ruta/al/keystore \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | Contrasena del almacen comun |
| `ANDROID_KEY_ALIAS` | Alias de la clave comun |
| `ANDROID_KEY_PASSWORD` | Contrasena de la clave comun |
| `ANDROID_KEYSTORE_BASE64_EVENPADEL` | keystore `stapeKey` en base64 |
| `ANDROID_KEYSTORE_PASSWORD_EVENPADEL` | Contrasena del almacen de Evenpadel |
| `ANDROID_KEY_ALIAS_EVENPADEL` | `stape` |
| `ANDROID_KEY_PASSWORD_EVENPADEL` | Contrasena de la clave de Evenpadel |

El workflow busca los secrets del flavor por su nombre, asi que una app con clave propia solo
necesita sus cuatro secrets `..._<FLAVOR>` (los crea `configurar-secrets.sh <flavor>`).

## Dar de alta una app nueva

`.github/scripts/nueva-app.sh` hace en un paso lo que antes eran los pasos a mano de Confluence
("New Android Framework - 2024"): bloque del flavor en `build.gradle`, iconos y splash a partir
de un logo, keystore de subida propio en `~/Documents/Keystores/<flavor>.jks` (apuntado en
`keystore.properties`), secrets de GitHub y opcion en el desplegable del workflow.

```bash
.github/scripts/nueva-app.sh --flavor basicfactory --nombre "Basic Factory" \
  --package es.stape.basicfactory --url https://bfcastellet.wiplay.app \
  --color1 "#000000" --color2 "#FABE68" \
  --logo logo.jpeg --recorte 280,90,430,430 --quitar-blanco --fondo-icono "#FFFFFF" \
  --onesignal <app id de OneSignal>
```

Los iconos se pueden regenerar sueltos con `generar_iconos.py`. Lo que Google no permite por API
y hay que hacer en Play Console: crear la app con ese paquete y dar acceso a la cuenta de servicio.

OneSignal ya no necesita un proyecto de Firebase por app: la app no lleva `google-services.json`
y OneSignal solo pide la clave JSON (FCM v1) de una cuenta de servicio, que puede ser la de un
proyecto Firebase existente.

## Publicar en Play

La subida NO ocurre en los push: solo cuando se lanza el workflow a mano
(*Actions > Android > Run workflow*) marcando **subir_a_play**, eligiendo flavor y canal
(`internal` por defecto).

Usa la cuenta de servicio `play-publisher-ci@evenpadel-4ea6b.iam.gserviceaccount.com`, invitada en
Play Console con permiso sobre la app. Su clave JSON esta en el secret `PLAY_SERVICE_ACCOUNT_JSON`.

El paquete de cada app se lee del `applicationId` de su flavor en `app/build.gradle`.

Mientras la app siga en borrador en Play (nunca publicada), la API solo admite releases en
estado `draft`: lanzar con **estado_release = draft** y terminar la release desde la consola.

Para publicar en otro canal hay que dar tambien ese permiso a la cuenta de servicio en Play Console.

## Antes de cada release

Subir `versionCode` del flavor correspondiente en `app/build.gradle`. Play rechaza un AAB con un
`versionCode` que ya exista en la consola.

## Icono de la ficha de Play

El icono que se ve en la tienda NO viaja dentro del AAB: es un recurso de la ficha y se sube
con la Play Developer API. Lo hace `.github/scripts/subir_icono_play.py`, que el workflow ejecuta
solo si marcas **subir_icono_ficha** al lanzarlo a mano.

Coge el fichero `app/src/<flavor>/ic_launcher-playstore.png` (512x512, PNG sin transparencia) y lo
pone en TODOS los idiomas que tenga la ficha.

Requisito: la cuenta de servicio necesita en Play Console, ademas de publicar, el permiso de
**gestionar la ficha de Play** ("Store presence"). Sin el, la API responde 403.

Cada cambio de ficha pasa por revision de Google antes de verse en la tienda.
