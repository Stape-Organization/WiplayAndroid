#!/usr/bin/env python3
"""Genera todo el set de iconos de un flavor a partir de un unico logo.

Sustituye al "Image Asset" de Android Studio. Con el mismo criterio que Evenpadel:
fondo solido con esquinas redondeadas (circulo en la variante round) y el logo al
68%, centrado, para que no se vea pegado a los bordes.

    Uso: generar_iconos.py <flavor> <logo.png|jpg> <color_fondo> [--quitar-blanco]
                           [--recorte x,y,ancho,alto] [--escala 0.68]

  --quitar-blanco  el logo viene sobre fondo blanco (un JPEG, p. ej.): se convierte
                   el blanco en transparencia para el foreground del icono adaptativo
  --recorte        usar solo esa zona del logo (p. ej. el simbolo de un logotipo apaisado)

Genera en app/src/<flavor>/:
  res/mipmap-<densidad>/ic_launcher.png, ic_launcher_round.png, ic_launcher_foreground.png
  res/mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml
  res/values/ic_launcher_background.xml
  res/drawable/logo_splash.png   (512px, para la pantalla de carga)
  ic_launcher-playstore.png      (512x512 sin transparencia, para la ficha de Play)
"""
import argparse
import os

from PIL import Image, ImageChops, ImageDraw

DENSIDADES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))

ADAPTATIVO = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
'''


def quitar_blanco(img):
    """Blanco -> transparente, deshaciendo la mezcla para que los bordes no queden con halo."""
    img = img.convert('RGB')
    px = img.load()
    out = Image.new('RGBA', img.size)
    po = out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b = px[x, y]
            a = 255 - min(r, g, b)
            if a == 0:
                po[x, y] = (0, 0, 0, 0)
                continue
            f = 255 / a
            po[x, y] = tuple(max(0, min(255, round(255 - (255 - c) * f))) for c in (r, g, b)) + (a,)
    return out


def recortar_a_contenido(img):
    caja = img.getchannel('A').point(lambda v: 255 if v > 8 else 0).getbbox()
    return img.crop(caja) if caja else img


def centrar(logo, lado, escala, fondo=None):
    """Lienzo cuadrado de `lado` px con el logo ocupando `escala` del lado."""
    lienzo = Image.new('RGBA', (lado, lado), fondo or (0, 0, 0, 0))
    hueco = max(1, round(lado * escala))
    l = logo.copy()
    l.thumbnail((hueco, hueco), Image.LANCZOS)
    if l.width < hueco and l.height < hueco:  # thumbnail no amplia
        f = hueco / max(l.size)
        l = logo.resize((round(logo.width * f), round(logo.height * f)), Image.LANCZOS)
    lienzo.alpha_composite(l, ((lado - l.width) // 2, (lado - l.height) // 2))
    return lienzo


def mascara(lado, redondo):
    s = 4  # supermuestreo para suavizar el borde
    m = Image.new('L', (lado * s, lado * s), 0)
    d = ImageDraw.Draw(m)
    if redondo:
        d.ellipse((0, 0, lado * s - 1, lado * s - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, lado * s - 1, lado * s - 1), radius=round(lado * s * 0.18), fill=255)
    return m.resize((lado, lado), Image.LANCZOS)


def icono(logo, lado, fondo, escala, redondo):
    img = centrar(logo, lado, escala, fondo)
    img.putalpha(ImageChops.multiply(img.getchannel('A'), mascara(lado, redondo)))
    return img


def main():
    p = argparse.ArgumentParser()
    p.add_argument('flavor')
    p.add_argument('logo')
    p.add_argument('fondo', help='color de fondo del icono, p. ej. #FFFFFF')
    p.add_argument('--quitar-blanco', action='store_true')
    p.add_argument('--recorte')
    p.add_argument('--escala', type=float, default=0.68)
    a = p.parse_args()

    fondo_hex = a.fondo if a.fondo.startswith('#') else '#' + a.fondo
    fondo = tuple(int(fondo_hex[i:i + 2], 16) for i in (1, 3, 5)) + (255,)

    logo = Image.open(a.logo)
    if a.recorte:
        x, y, w, h = (int(v) for v in a.recorte.split(','))
        logo = logo.crop((x, y, x + w, y + h))
    logo = quitar_blanco(logo) if a.quitar_blanco else logo.convert('RGBA')
    logo = recortar_a_contenido(logo)

    base = os.path.join(RAIZ, 'app', 'src', a.flavor)
    res = os.path.join(base, 'res')

    for nombre, f in DENSIDADES.items():
        d = os.path.join(res, 'mipmap-' + nombre)
        os.makedirs(d, exist_ok=True)
        icono(logo, round(48 * f), fondo, a.escala, False).save(os.path.join(d, 'ic_launcher.png'))
        icono(logo, round(48 * f), fondo, a.escala, True).save(os.path.join(d, 'ic_launcher_round.png'))
        # Adaptativo: 108dp de lienzo, de los que solo se garantizan visibles los 66 centrales
        centrar(logo, round(108 * f), a.escala * 66 / 108).save(os.path.join(d, 'ic_launcher_foreground.png'))

    d = os.path.join(res, 'mipmap-anydpi-v26')
    os.makedirs(d, exist_ok=True)
    for n in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        with open(os.path.join(d, n), 'w') as fh:
            fh.write(ADAPTATIVO)

    d = os.path.join(res, 'values')
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, 'ic_launcher_background.xml'), 'w') as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                 f'    <color name="ic_launcher_background">{fondo_hex.upper()}</color>\n</resources>\n')

    d = os.path.join(res, 'drawable')
    os.makedirs(d, exist_ok=True)
    icono(logo, 512, fondo, a.escala, True).save(os.path.join(d, 'logo_splash.png'))

    # La ficha de Play no admite transparencia: Google aplica su propia mascara
    centrar(logo, 512, a.escala, fondo).convert('RGB').save(os.path.join(base, 'ic_launcher-playstore.png'))
    print(f'Iconos generados en {os.path.relpath(base, RAIZ)}')


if __name__ == '__main__':
    main()
