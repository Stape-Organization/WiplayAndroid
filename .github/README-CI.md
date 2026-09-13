# CI de Android

El workflow `.github/workflows/android.yml` compila en cada push a `main` y en cada PR, y sube
los APK/AAB como artefactos. Tambien se puede lanzar a mano ("Run workflow") eligiendo flavor.

## Firma

Las credenciales YA NO estan en `app/build.gradle`. Se leen, por este orden:

1. `keystore.properties` en la raiz del proyecto (uso local, esta en `.gitignore`).
2. Variables de entorno `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (CI).

Si no hay ninguna de las dos, el build sigue adelante y genera un binario **sin firmar**
en vez de romperse.

### keystore.properties (local)

```properties
storeFile=/ruta/al/keystore
storePassword=...
keyAlias=...
keyPassword=...
```

### Secrets de GitHub (CI)

En *Settings > Secrets and variables > Actions*:

| Secret | Contenido |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | El keystore codificado: `base64 -i /ruta/al/keystore \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | Contrasena del almacen |
| `ANDROID_KEY_ALIAS` | Alias de la clave |
| `ANDROID_KEY_PASSWORD` | Contrasena de la clave |

Sin `ANDROID_KEYSTORE_BASE64` el workflow avisa y compila sin firmar; el resto de pasos siguen.

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
