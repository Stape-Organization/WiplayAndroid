#!/usr/bin/env bash
# Crea en GitHub los secrets de firma de un flavor leyendo keystore.properties.
#
# Los valores NUNCA se imprimen: van directos de tu disco a GitHub via gh.
#
#   Uso:  .github/scripts/configurar-secrets.sh evenpadel
#         .github/scripts/configurar-secrets.sh            (todos los que encuentre)
#
#   Requisitos: gh instalado (brew install gh) y autenticado (gh auth login).

set -euo pipefail

REPO="${REPO:-Stape-Organization/WiplayAndroid}"
PROPS="$(cd "$(dirname "$0")/../.." && pwd)/keystore.properties"

command -v gh >/dev/null || { echo "Falta gh. Instalalo con: brew install gh"; exit 1; }
[ -f "$PROPS" ] || { echo "No encuentro $PROPS"; exit 1; }

leer() { # leer <clave>  -> valor o vacio
  grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2- || true
}

configurar_flavor() {
  local flavor="$1"
  local sufijo; sufijo="$(echo "$flavor" | tr '[:lower:]' '[:upper:]')"

  local ks pass alias keypass
  ks="$(leer "${flavor}.storeFile")";        [ -n "$ks" ]      || ks="$(leer storeFile)"
  pass="$(leer "${flavor}.storePassword")";  [ -n "$pass" ]    || pass="$(leer storePassword)"
  alias="$(leer "${flavor}.keyAlias")";      [ -n "$alias" ]   || alias="$(leer keyAlias)"
  keypass="$(leer "${flavor}.keyPassword")"; [ -n "$keypass" ] || keypass="$(leer keyPassword)"

  if [ -z "$ks" ] || [ ! -f "$ks" ]; then
    echo "  $flavor: sin keystore ($ks), lo salto"
    return
  fi

  echo "  $flavor: subiendo secrets (keystore $(basename "$ks"), alias $alias)"
  base64 -i "$ks" | gh secret set "ANDROID_KEYSTORE_BASE64_${sufijo}" --repo "$REPO"
  printf '%s' "$pass"    | gh secret set "ANDROID_KEYSTORE_PASSWORD_${sufijo}" --repo "$REPO"
  printf '%s' "$alias"   | gh secret set "ANDROID_KEY_ALIAS_${sufijo}"         --repo "$REPO"
  printf '%s' "$keypass" | gh secret set "ANDROID_KEY_PASSWORD_${sufijo}"      --repo "$REPO"
}

echo "Repositorio: $REPO"
if [ $# -gt 0 ]; then
  for f in "$@"; do configurar_flavor "$f"; done
else
  for f in evenpadel wiplaypadel summapadel totpadel indoorpadel7 basicfactory; do configurar_flavor "$f"; done
fi
echo "Listo. Comprueba con: gh secret list --repo $REPO"
