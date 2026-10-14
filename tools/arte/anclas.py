"""Anclajes de las flechas de la lámina (pieza 3, §10.2): fruto o flor, follaje y tronco de cada planta.

Uso, desde la raíz del repo:  python3 tools/arte/anclas.py
Lee las fuentes de tools/arte/jardin.json, mide en el marco recortado al alfa (el del WebP de la app),
aplica las 6 medidas a mano del prototipo (mandan) y escribe F/ui/detail/AnclasPlanta.kt.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import exportar  # noqa: E402

AQUI = os.path.dirname(os.path.abspath(__file__))
KT = os.path.join(exportar.RAIZ, "app", "src", "main", "java", "com", "alvarotc", "bito", "ui", "detail", "AnclasPlanta.kt")
ESPECIES = ["hojas-redondas", "girasoles", "lavanda", "manzano", "helechos", "cerezo"]
UMBRAL = exportar.UMBRAL_ALFA

# 13-mezcla/index.html, const ANCLAS: fracciones del PNG entero. Mandan sobre el cálculo.
A_MANO = {
    "planta-4": [(0.86, 0.24), (0.80, 0.47), (0.50, 0.72)],
    "girasoles-4": [(0.74, 0.10), (0.83, 0.50), (0.43, 0.74)],
    "lavanda-3": [(0.63, 0.13), (0.77, 0.42), (0.50, 0.66)],
    "manzano-5": [(0.76, 0.23), (0.81, 0.39), (0.48, 0.66)],
    "helechos-5": [(0.83, 0.20), (0.87, 0.42), (0.51, 0.70)],
    "cerezo-2": [(0.73, 0.17), (0.62, 0.30), (0.51, 0.46)],
}


def clave_app(clave_prototipo):
    return clave_prototipo.replace("planta-", "hojas-redondas-", 1) if clave_prototipo.startswith("planta-") else clave_prototipo


def _recortada(img):
    caja = exportar.caja_alfa(img)
    return img.crop(caja), caja


def _mas_a_la_derecha(alfa, desde, hasta):
    alto, ancho = alfa.shape
    mejor = (0, desde)
    for y in range(int(alto * desde), max(int(alto * desde) + 1, int(alto * hasta))):
        xs = np.nonzero(alfa[y] > UMBRAL)[0]
        if xs.size and xs[-1] > mejor[0]:
            mejor = (xs[-1], y)
    return (mejor[0] + 0.5) / ancho, (mejor[1] + 0.5) / alto


def anclas_de(img):
    """Tres puntos en fracciones de la pieza recortada al alfa."""
    pieza, _ = _recortada(img.convert("RGBA"))
    alfa = np.array(pieza.getchannel("A"))
    alto, ancho = alfa.shape
    fruto = _mas_a_la_derecha(alfa, 0.10, 0.30)
    follaje = _mas_a_la_derecha(alfa, 0.35, 0.55)
    y = int(alto * 0.70)
    xs = np.nonzero(alfa[y] > UMBRAL)[0]
    tronco = (((xs.mean() + 0.5) / ancho) if xs.size else 0.5, 0.70)
    return [fruto, follaje, tronco]


def al_marco_recortado(punto, tamano, caja):
    (fx, fy), (w, h), (x0, y0, x1, y1) = punto, tamano, caja
    return ((fx * w - x0) / (x1 - x0), (fy * h - y0) / (y1 - y0))


def combinar(calculadas, a_mano):
    tabla = dict(calculadas)
    tabla.update(a_mano)
    return tabla


def kotlin(tabla):
    filas = []
    for clave in sorted(tabla):
        nums = ", ".join(f"{v:.3f}f" for p in tabla[clave] for v in p)
        filas.append(f'            "{clave}" to floatArrayOf({nums}),')
    return (
        exportar.CABECERA
        + "package com.alvarotc.bito.ui.detail\n\n"
        + "/** Fruto o flor, follaje y tronco de cada planta en fracciones de su pieza (pieza 3, §10.2). */\n"
        + "internal object AnclasPlanta {\n"
        + "    val tabla: Map<String, FloatArray> =\n"
        + "        mapOf(\n"
        + "\n".join(filas)
        + "\n        )\n"
        + "}\n"
    )


def main():
    jardin = exportar.cargar_json(os.path.join(AQUI, "jardin.json"))
    ruta_manifiesto = os.path.join(AQUI, "jardin.json")
    calculadas, a_mano = {}, {}
    for especie in jardin["especies"]:
        for n, archivo in enumerate(especie["etapas"], start=1):
            ruta = exportar.ruta_fuente(jardin, ruta_manifiesto, archivo)
            img = exportar.aplicar_recorte(Image.open(ruta).convert("RGBA"), jardin.get("recortes", {}).get(os.path.basename(archivo)))
            clave = f"{especie['clave']}-{n}"
            calculadas[clave] = anclas_de(img)
            proto = next((k for k in A_MANO if clave_app(k) == clave), None)
            if proto:
                caja = exportar.caja_alfa(img)
                a_mano[clave] = [al_marco_recortado(p, img.size, caja) for p in A_MANO[proto]]
    tabla = combinar(calculadas, a_mano)
    exportar.escribir(KT, kotlin(tabla))
    for clave, puntos in sorted(a_mano.items()):
        print(clave, "a mano", puntos, "calculado", calculadas[clave])


if __name__ == "__main__":
    main()
