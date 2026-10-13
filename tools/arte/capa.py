"""Extrae una capa (lo añadido) comparando a Habi «puesto» con la base, sobre la piel.

Sirve para el gorro de dormir y para cualquier prenda pintada sobre la piel. Pasos: diferencia con
la base, componentes grandes, rellenar huecos, quitar piel verde (y oliva), mayor componente,
apertura suave y alfa suavizado. Solo escribe --salida.

    python3 tools/arte/capa.py --base cuerpo.jpg --puesto puesto.jpeg --salida capa.png \
        [--recorte x0,y0,x1,y1] [--sin-oliva] [--apertura 9]
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np  # noqa: E402
from PIL import Image, ImageFilter  # noqa: E402

import accesorio  # noqa: E402
import comun  # noqa: E402


def _ensanchar(mascara, tamano=5):
    img = Image.fromarray((mascara * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(tamano))
    return np.asarray(img) > 0


def extraer_capa(base_rgb, puesto_rgb, sin_oliva=False, apertura=9):
    """La capa RGBA (del tamaño de la imagen puesta) con solo lo que se ha añadido sobre la base."""
    rgba = comun.quitar_magenta(puesto_rgb)
    alfa = rgba[..., 3] / 255.0
    solido = alfa > 0.5
    delta = np.abs(base_rgb - puesto_rgb)
    m = solido & (accesorio._mediana(delta.max(-1)) > accesorio.CAMBIO_MINIMO)
    m = comun.componentes_grandes(m, paso=2, cambio=delta.mean(-1))
    m = comun.rellenar_huecos(m)
    m = m & solido & ~comun.mascara_verde(puesto_rgb)
    h, s, _ = comun.hsv(puesto_rgb)
    m = comun.mayor_componente(m & ~_ensanchar((h > 60) & (h < 150) & (s > 0.08)), paso=2)
    if sin_oliva:
        r, g, b = puesto_rgb[..., 0], puesto_rgb[..., 1], puesto_rgb[..., 2]
        m = comun.mayor_componente(m & ~_ensanchar((g >= r - 3) & (g > b + 10)), paso=2)
    m = comun.rellenar_huecos(m)
    if apertura > 1:
        img = Image.fromarray((m * 255).astype(np.uint8))
        abierta = img.filter(ImageFilter.MinFilter(apertura)).filter(ImageFilter.MaxFilter(apertura))
        m = m & (np.asarray(abierta) > 0)
    a = Image.fromarray((m * alfa * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
    capa = Image.fromarray(rgba, "RGBA")
    capa.putalpha(a)
    return capa


def main(argv=None):
    p = argparse.ArgumentParser(description="Extrae lo añadido sobre Habi como una capa con alfa.")
    p.add_argument("--base", required=True)
    p.add_argument("--puesto", required=True)
    p.add_argument("--salida", required=True)
    p.add_argument("--recorte", default=None, help="x0,y0,x1,y1 en píxeles de la imagen puesta")
    p.add_argument("--sin-oliva", action="store_true", help="quita también el verde oliva de la sombra de la piel")
    p.add_argument("--apertura", type=int, default=9, help="lado impar del filtro que quita hilos finos (1 = sin apertura)")
    args = p.parse_args(argv)
    if args.apertura > 1 and args.apertura % 2 == 0:
        print("--apertura tiene que ser impar.")
        return 2
    base, puesto = comun.cargar_rgb(args.base), comun.cargar_rgb(args.puesto)
    if base.shape != puesto.shape:
        print(f"La base mide {base.shape[1]} x {base.shape[0]} y la puesta {puesto.shape[1]} x {puesto.shape[0]}: tienen que medir lo mismo.")
        return 2
    capa = extraer_capa(base, puesto, sin_oliva=args.sin_oliva, apertura=args.apertura)
    if args.recorte:
        capa = capa.crop(tuple(int(v) for v in args.recorte.split(",")))
    caja = capa.getchannel("A").getbbox()
    capa.save(args.salida)
    print("ok", args.salida, "caja con alfa:", caja)
    return 0


if __name__ == "__main__":
    sys.exit(main())
