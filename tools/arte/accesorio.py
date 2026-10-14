"""Extrae un accesorio o un disfraz pintado SOBRE Habi y lo alinea con la base.

Uso: python3 tools/arte/accesorio.py <base> <editada> <salida.png> [--modo accesorio|gafas|disfraz]

- accesorio: alinea la editada con la base buscando la escala y el desplazamiento que maximizan el
  solape de la piel (el contorno falla si el accesorio tapa un lado). El accesorio es lo que no es
  fondo ni piel; si la edición no movió a Habi, es lo que cambió respecto a la base. Se conservan
  todas las piezas grandes (un zapato por pie). Base: cuerpo.jpg.
- gafas: igual, quitando ojos y boca (marrón oscuro saturado). Base: habi-principal.jpg.
- disfraz: todo lo que no es fondo, incluida la piel que asoma por el hueco de la cara. Se alinea
  por el centro (medido sobre los pies, no sobre la cola) y la línea de los pies, sin escalar, y se
  comprueba que el hueco cae sobre la cara (comun.CAJA_CARA).

La salida mide lo mismo que la base y está alineada con ella; exportar.py la recorta y calcula el
margen. Códigos: 0 bien; 3 desalineado más del 2 % (repetir la imagen); 4 hueco fuera de sitio.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np  # noqa: E402
from PIL import Image, ImageFilter  # noqa: E402

import comun  # noqa: E402

# Una edición cuenta como «no movida» si la mejor escala es 1 y el desplazamiento es 0.
CAMBIO_MINIMO = 40
# Con --eje cabeza, la franja de arriba de la piel (35 %) no cuenta para alinear: el gorro la tapa.
IGNORAR_CABEZA = 0.35


def _caja_cara_en_base():
    x0, y0, x1, y1 = comun.CAJA_CARA
    ox, oy = comun.ORIGEN_CUERPO
    return x0 + ox, y0 + oy, x1 + ox, y1 + oy


def _mediana(valores, tamano=5):
    im = Image.fromarray(np.clip(valores, 0, 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.MedianFilter(tamano)))


def _fraccion_hueco(editada_rgb, caja, dx, dy):
    x0, y0, x1, y1 = (int(round(v)) for v in (caja[0] - dx, caja[1] - dy, caja[2] - dx, caja[3] - dy))
    alto, ancho = editada_rgb.shape[:2]
    x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, ancho), min(y1, alto)
    if x1 <= x0 or y1 <= y0:
        return 0.0
    return comun.fraccion_verde(editada_rgb, (x0, y0, x1, y1))


def extraer(base_rgb, editada_rgb, modo="accesorio", caja_cara=None, ignorar_arriba=0.0):
    """Devuelve (capa RGBA del tamaño de la base, desviación, fracción de piel en el hueco o None)."""
    rgba_ed = comun.quitar_magenta(editada_rgb)
    alfa_ed = rgba_ed[..., 3].astype(np.float32) / 255.0
    solido = alfa_ed > 0.5
    piel_base = comun.mascara_verde(base_rgb)
    base = comun.contorno(piel_base)
    ancho = base[1] - base[0]
    hueco = None
    cambio = None
    if modo == "disfraz":
        altura = base[2] - float(np.percentile(np.nonzero(piel_base)[0], 0.2))
        pies_base = comun.pies(piel_base, altura)
        pies_ed = comun.pies(solido, altura)
        dev = comun.desviacion_disfraz(pies_base, pies_ed, ancho)
        escala = 1.0
        dx, dy = pies_base[0] - pies_ed[0], pies_base[1] - pies_ed[1]
        mascara = solido
        hueco = _fraccion_hueco(editada_rgb, caja_cara or _caja_cara_en_base(), dx, dy)
    else:
        piel_ed = comun.mascara_verde(editada_rgb)
        _, escala, dx, dy = comun.alinear(piel_base, piel_ed, ignorar_arriba=ignorar_arriba)
        dev = comun.desviacion_alineacion(escala, dx, dy, ancho)
        mascara = solido & ~piel_ed
        if abs(escala - 1) < 1e-3 and dx == 0 and dy == 0:
            # Habi no se movió: el accesorio es lo que CAMBIÓ respecto a la base, así no se cuelan
            # el contorno, la mancha de la cabeza ni las sombras del cuerpo.
            delta = np.abs(base_rgb - editada_rgb)
            mascara = solido & (_mediana(delta.max(-1)) > CAMBIO_MINIMO)
            cambio = delta.mean(-1)
        if modo == "gafas":
            # Ojos y boca son marrón oscuro saturado; la montura es casi negra y poco saturada.
            h, s, v = comun.hsv(editada_rgb)
            mascara &= ~((h < 50) & (s > 0.45) & (v > 0.12))
    mascara = comun.componentes_grandes(mascara, paso=2, cambio=cambio)
    alfa = Image.fromarray((mascara * alfa_ed * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))
    rgba = Image.fromarray(rgba_ed, "RGBA")
    rgba.putalpha(alfa)
    if abs(escala - 1) > 1e-6:
        alto_ed, ancho_ed = editada_rgb.shape[:2]
        rgba = rgba.resize((int(round(ancho_ed * escala)), int(round(alto_ed * escala))), Image.LANCZOS)
    capa = Image.new("RGBA", (base_rgb.shape[1], base_rgb.shape[0]), (0, 0, 0, 0))
    # paste sin máscara sobre un lienzo vacío copia también el alfa; admite desplazamientos negativos.
    capa.paste(rgba, (int(round(dx)), int(round(dy))))
    return capa, dev, hueco


def main(argv=None):
    p = argparse.ArgumentParser(description="Extrae un accesorio o disfraz alineado con la base.")
    p.add_argument("base")
    p.add_argument("editada")
    p.add_argument("salida")
    p.add_argument("--modo", choices=["accesorio", "gafas", "disfraz"], default="accesorio")
    p.add_argument("--umbral", type=float, default=comun.UMBRAL_ALINEACION)
    p.add_argument("--eje", choices=["cabeza", "cara", "cuello", "pies"], default=None,
                   help="Con 'cabeza', la franja de arriba (35 %%) no cuenta para alinear: el gorro la tapa.")
    args = p.parse_args(argv)
    capa, dev, hueco = extraer(comun.cargar_rgb(args.base), comun.cargar_rgb(args.editada), args.modo,
                               ignorar_arriba=IGNORAR_CABEZA if args.eje == "cabeza" else 0.0)
    print(f"desviación {dev:.4f} (umbral {args.umbral})")
    if dev > args.umbral:
        print("DESALINEADO: repite la imagen en una conversación nueva.")
        return 3
    if hueco is not None:
        print(f"piel en el hueco de la cara {hueco:.2f} (mínimo {comun.MINIMO_HUECO})")
        if hueco < comun.MINIMO_HUECO:
            print("HUECO FUERA DE SITIO: ajusta el prompt o recorta el hueco.")
            return 4
    capa.save(args.salida)
    print("ok", args.salida)
    return 0


if __name__ == "__main__":
    sys.exit(main())
