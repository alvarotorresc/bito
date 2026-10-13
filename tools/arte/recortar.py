"""Quita el fondo magenta de una hoja y separa cada pieza en un PNG.

Uso: python3 tools/arte/recortar.py <hoja> <dir_salida> <prefijo> [--area-min 400] [--esperadas N]

Códigos de salida: 0 bien; 2 el número de piezas no es el esperado (la generación cuenta mal:
repetir la imagen o ajustar --area-min); 3 alguna pieza conserva halo magenta.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import comun  # noqa: E402

MARGEN = 6


def recortar(ruta, salida, prefijo, area_min=400):
    rgb = comun.cargar_rgb(ruta)
    rgba = comun.quitar_magenta(rgb)
    cajas = comun.componentes(rgba[..., 3] > 127, paso=4, area_min=area_min)
    # Por filas de 150 px y, dentro de cada fila, de izquierda a derecha: el orden de la hoja.
    cajas.sort(key=lambda c: (c[0] // 150, c[1]))
    os.makedirs(salida, exist_ok=True)
    completa = Image.fromarray(rgba, "RGBA")
    rutas = []
    for i, (y0, x0, y1, x1, _) in enumerate(cajas):
        caja = (
            max(x0 - MARGEN, 0),
            max(y0 - MARGEN, 0),
            min(x1 + MARGEN, rgb.shape[1]),
            min(y1 + MARGEN, rgb.shape[0]),
        )
        nombre = os.path.join(salida, f"{prefijo}{i:02d}.png")
        completa.crop(caja).save(nombre)
        rutas.append(nombre)
        print(os.path.basename(nombre), "x", caja[0], "y", caja[1], "w", caja[2] - caja[0], "h", caja[3] - caja[1])
    return rutas


def main(argv=None):
    p = argparse.ArgumentParser(description="Separa las piezas de una hoja con fondo magenta.")
    p.add_argument("hoja")
    p.add_argument("salida")
    p.add_argument("prefijo")
    p.add_argument("--area-min", type=int, default=400)
    p.add_argument("--esperadas", type=int, default=None)
    args = p.parse_args(argv)
    rutas = recortar(args.hoja, args.salida, args.prefijo, args.area_min)
    sucias = [r for r in rutas if comun.halo(np.asarray(Image.open(r))) > comun.UMBRAL_HALO]
    for r in sucias:
        print("HALO MAGENTA:", r)
    if args.esperadas is not None and len(rutas) != args.esperadas:
        print(f"RECUENTO: {len(rutas)} piezas, se esperaban {args.esperadas}.")
        return 2
    return 3 if sucias else 0


if __name__ == "__main__":
    sys.exit(main())
