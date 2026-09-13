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

Para anadir otra app con clave propia: crear sus cuatro secrets `..._<FLAVOR>` y anadir el flavor
al `case` del paso "Restaurar keystores" del workflow.

## Publicar en Play

La subida NO ocurre en los push: solo cuando se lanza el workflow a mano
(*Actions > Android > Run workflow*) marcando **subir_a_play**, eligiendo flavor y canal
(`internal` por defecto).

Usa la cuenta de servicio `play-publisher-ci@evenpadel-4ea6b.iam.gserviceaccount.com`, invitada en
Play Console con permiso sobre la app. Su clave JSON esta en el secret `PLAY_SERVICE_ACCOUNT_JSON`.

| Flavor | applicationId |
|---|---|
| evenpadel | es.stape.evenpadel |
| wiplaypadel | es.stape.wiplaypadel |
| totpadel | totpadel.cat.totpadel |
| summapadel | es.stape.easypadel.summapadel |
| indoorpadel7 | es.stape.indoorpadel7 |
| pickleball | es.stape.easypadel.pickleball |

Para publicar en otro canal hay que dar tambien ese permiso a la cuenta de servicio en Play Console.

## Antes de cada release

Subir `versionCode` del flavor correspondiente en `app/build.gradle`. Play rechaza un AAB con un
`versionCode` que ya exista en la consola.
