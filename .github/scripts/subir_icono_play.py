#!/usr/bin/env python3
"""Sube el icono 512x512 a la ficha de Play de una app, en todos sus idiomas.

El icono de la ficha NO viaja dentro del AAB: es un recurso de la ficha de tienda
y se sube con la Play Developer API.

    subir_icono_play.py <packageName> <ruta_icono.png>

Necesita la variable PLAY_SERVICE_ACCOUNT_JSON con la clave de la cuenta de
servicio, y que esa cuenta tenga en Play Console el permiso de gestionar la
ficha ("Store presence"), ademas del de publicar.
"""
import json
import os
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com"
ALCANCE = ["https://www.googleapis.com/auth/androidpublisher"]


def token():
    bruto = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if not bruto:
        sys.exit("Falta PLAY_SERVICE_ACCOUNT_JSON")
    cred = service_account.Credentials.from_service_account_info(
        json.loads(bruto), scopes=ALCANCE
    )
    cred.refresh(Request())
    return cred.token


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    paquete, ruta = sys.argv[1], sys.argv[2]
    if not os.path.isfile(ruta):
        sys.exit(f"No existe el icono: {ruta}")

    cabeceras = {"Authorization": f"Bearer {token()}"}
    base = f"{API}/androidpublisher/v3/applications/{paquete}"

    r = requests.post(f"{base}/edits", headers=cabeceras, timeout=60)
    r.raise_for_status()
    edit = r.json()["id"]
    print(f"Edit {edit} creado para {paquete}")

    r = requests.get(f"{base}/edits/{edit}/listings", headers=cabeceras, timeout=60)
    r.raise_for_status()
    idiomas = [l["language"] for l in r.json().get("listings", [])]
    if not idiomas:
        sys.exit("La app no tiene ninguna ficha de tienda creada")
    print("Idiomas de la ficha:", ", ".join(idiomas))

    with open(ruta, "rb") as f:
        imagen = f.read()

    for idioma in idiomas:
        destino = f"{base}/edits/{edit}/listings/{idioma}/icon"
        requests.delete(destino, headers=cabeceras, timeout=60).raise_for_status()
        subida = (
            f"{API}/upload/androidpublisher/v3/applications/{paquete}"
            f"/edits/{edit}/listings/{idioma}/icon?uploadType=media"
        )
        r = requests.post(
            subida,
            headers={**cabeceras, "Content-Type": "image/png"},
            data=imagen,
            timeout=120,
        )
        r.raise_for_status()
        print(f"  {idioma}: icono sustituido")

    r = requests.post(f"{base}/edits/{edit}:commit", headers=cabeceras, timeout=60)
    r.raise_for_status()
    print("Ficha enviada. Google la revisara antes de publicarla.")


if __name__ == "__main__":
    main()
