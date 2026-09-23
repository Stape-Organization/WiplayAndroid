#!/usr/bin/env bash
# Da de alta una app nueva (un flavor) de principio a fin, en lugar de los pasos a mano de
# "New Android Framework - 2024" en Confluence:
#
#   1. Bloque del flavor en app/build.gradle (+ FLAVORS_CON_FIRMA)
#   2. Set completo de iconos, splash e icono de la ficha de Play (generar_iconos.py)
#   3. Keystore de subida propio, apuntado en keystore.properties
#   4. Secrets de firma en GitHub (configurar-secrets.sh)
#   5. Opcion en el desplegable del workflow de CI
#
#   Uso:
#     .github/scripts/nueva-app.sh \
#       --flavor basicfactory --nombre "Basic Factory" \
#       --package es.stape.basicfactory --url https://bfcastellet.wiplay.app \
#       --color1 "#000000" --color2 "#FABE68" \
#       --logo logo.png [--recorte x,y,ancho,alto] [--quitar-blanco] [--fondo-icono "#FFFFFF"] \
#       [--onesignal <app id>] [--sin-secrets]
#
# Lo unico que queda fuera es lo que Google no expone por API: crear la app en Play Console
# e invitar a la cuenta de servicio del CI (ver README-CI.md).

set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
GRADLE="$RAIZ/app/build.gradle"
WORKFLOW="$RAIZ/.github/workflows/android.yml"
PROPS="$RAIZ/keystore.properties"
KEYSTORES="${KEYSTORES:-$HOME/Documents/Keystores}"

FLAVOR= NOMBRE= PACKAGE= URL= COLOR1="#252525" COLOR2="#EAF02C" LOGO= RECORTE= QUITAR_BLANCO=
FONDO_ICONO="#FFFFFF" ONESIGNAL= SECRETS=true
while [ $# -gt 0 ]; do
  case "$1" in
    --flavor) FLAVOR="$2"; shift 2 ;;
    --nombre) NOMBRE="$2"; shift 2 ;;
    --package) PACKAGE="$2"; shift 2 ;;
    --url) URL="${2%/}"; shift 2 ;;
    --color1) COLOR1="$2"; shift 2 ;;
    --color2) COLOR2="$2"; shift 2 ;;
    --logo) LOGO="$2"; shift 2 ;;
    --recorte) RECORTE="$2"; shift 2 ;;
    --quitar-blanco) QUITAR_BLANCO=--quitar-blanco; shift ;;
    --fondo-icono) FONDO_ICONO="$2"; shift 2 ;;
    --onesignal) ONESIGNAL="$2"; shift 2 ;;
    --sin-secrets) SECRETS=false; shift ;;
    *) echo "Opcion desconocida: $1"; exit 1 ;;
  esac
done

for v in FLAVOR NOMBRE PACKAGE URL LOGO; do
  [ -n "${!v}" ] || { echo "Falta --$(echo "$v" | tr '[:upper:]' '[:lower:]')"; exit 1; }
done
[[ "$FLAVOR" =~ ^[a-z][a-z0-9]*$ ]] || { echo "El flavor debe ir en minusculas y sin simbolos"; exit 1; }
[ -f "$LOGO" ] || { echo "No encuentro el logo $LOGO"; exit 1; }
DOMINIO="$(echo "$URL" | sed -E 's#^https?://##; s#/.*##')"

paso() { printf '\n== %s\n' "$1"; }

# --- 1. build.gradle ---------------------------------------------------------------------
paso "Flavor $FLAVOR en build.gradle"
if grep -qE "^        $FLAVOR \{" "$GRADLE"; then
  echo "Ya existe, no lo toco"
else
  [ -n "$ONESIGNAL" ] || echo "AVISO: sin --onesignal, se deja un id vacio: las push no funcionaran hasta ponerlo"
  BLOQUE="$(cat <<EOF
        $FLAVOR {
            dimension "versionType"
            signingConfig signingConfigs.findByName("$FLAVOR")
            applicationId "$PACKAGE"
            versionCode 1
            versionName "1.0"
            resValue "string", "app_name", "$NOMBRE"
            resValue "string", "app_url", "$URL"
            resValue "string", "app_intent", "$DOMINIO"
            resValue "string", "app_onesignal_id", "$ONESIGNAL"
            resValue "string", "principal_color", "$COLOR1"
            resValue "string", "secondary_color", "$COLOR2"
        }

EOF
)"
  # Se inserta justo antes del flavor "local", que siempre va el ultimo
  BLOQUE="$BLOQUE" perl -0pi -e 's/^(        local \{)/$ENV{BLOQUE}\n$1/m' "$GRADLE"
  perl -pi -e "s/(ext\.FLAVORS_CON_FIRMA = \[.*?)(, 'local'\])/\$1, '$FLAVOR'\$2/" "$GRADLE"
  echo "Anadido (versionCode 1)"
fi

# --- 2. Iconos ---------------------------------------------------------------------------
paso "Iconos"
python3 "$RAIZ/.github/scripts/generar_iconos.py" "$FLAVOR" "$LOGO" "$FONDO_ICONO" \
  ${RECORTE:+--recorte "$RECORTE"} $QUITAR_BLANCO

# --- 3. Keystore -------------------------------------------------------------------------
paso "Keystore"
KS="$KEYSTORES/$FLAVOR.jks"
if grep -q "^$FLAVOR.storeFile=" "$PROPS" 2>/dev/null; then
  echo "keystore.properties ya tiene clave para $FLAVOR"
elif [ -f "$KS" ]; then
  echo "Ya existe $KS pero no esta en keystore.properties: anadelo a mano con su contrasena"; exit 1
else
  mkdir -p "$KEYSTORES"
  PASS="$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)"
  # PKCS12: la contrasena de la clave es la misma que la del almacen
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -alias "$FLAVOR" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=$NOMBRE, O=Stape, C=ES" >/dev/null 2>&1
  {
    echo ""
    echo "# $NOMBRE ($PACKAGE) - generado por nueva-app.sh el $(date +%F)"
    echo "$FLAVOR.storeFile=$KS"
    echo "$FLAVOR.storePassword=$PASS"
    echo "$FLAVOR.keyAlias=$FLAVOR"
    echo "$FLAVOR.keyPassword=$PASS"
  } >> "$PROPS"
  echo "Creado $KS (alias $FLAVOR). Contrasena en keystore.properties:"
  echo "GUARDALA TAMBIEN en el gestor de contrasenas: sin ella no se puede volver a subir la app."
  keytool -list -v -keystore "$KS" -storepass "$PASS" 2>/dev/null | grep -m1 'SHA1' || true
fi

# --- 4. Secrets de GitHub ----------------------------------------------------------------
if $SECRETS; then
  paso "Secrets de GitHub"
  "$RAIZ/.github/scripts/configurar-secrets.sh" "$FLAVOR"
fi

# --- 5. Workflow -------------------------------------------------------------------------
paso "Workflow de CI"
if grep -qE "^          - $FLAVOR$" "$WORKFLOW"; then
  echo "Ya esta en el desplegable"
else
  # Tras la ultima opcion de la lista de flavors (la que precede a "subir_a_play:")
  perl -0pi -e "s/((?:^          - \w+\n)+)(      subir_a_play:)/\$1          - $FLAVOR\n\$2/m" "$WORKFLOW"
  echo "Anadido al desplegable"
fi

cat <<EOF

== Hecho. Queda (Google no lo permite por API):
  1. Play Console > Crear app: "$NOMBRE", paquete $PACKAGE
  2. Play Console > Usuarios y permisos: dar acceso a la app a la cuenta de servicio del CI
  3. git add/commit/push y lanzar Actions > Android > Run workflow con flavor=$FLAVOR
     (la primera vez, con estado_release=draft: la app sigue en borrador en Play)
EOF
