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

El workflow **no publica**: genera el AAB y lo deja como artefacto. Para automatizar la subida
haria falta una cuenta de servicio de Google Play y anadir un paso con `r0adkll/upload-google-play`.

## Antes de cada release

Subir `versionCode` del flavor correspondiente en `app/build.gradle`. Play rechaza un AAB con un
`versionCode` que ya exista en la consola.
