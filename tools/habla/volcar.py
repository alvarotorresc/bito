#!/usr/bin/env python3
"""Vuelca los borradores TSV a strings_habla_<archivo>.xml (EN en values/, ES en values-es/)."""
import argparse
import glob
import os
import sys

import comprobar
import comun

CABECERA = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generado por tools/habla/volcar.py; no editar a mano -->\n'
            '<resources>\n')


def escapar(texto):
    t = (texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
         .replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"'))
    if t.startswith(("@", "?")):
        t = "\\" + t
    return t


def _item(fila, idioma):
    et = comun.etiquetas_de(fila["etiquetas"])
    prefijo = "[" + ",".join(et) + "] " if et else ""
    return prefijo + fila[idioma]


def volcar(dir_borradores, dir_res, minimos=False):
    rutas = sorted(glob.glob(os.path.join(dir_borradores, "*.tsv")))
    fallos = comprobar.comprobar(rutas, minimos=minimos)
    if fallos:
        for f in fallos:
            print(f, file=sys.stderr)
        raise SystemExit(1)
    bolsas = comun.cargar_bolsas()
    orden = list(bolsas)
    datos = {}  # (archivo, bolsa, voz) -> [filas]
    for ruta in rutas:
        voz = comun.voz_de(ruta)
        filas, _ = comun.leer_tsv(ruta)
        for fila in filas:
            datos.setdefault((bolsas[fila["bolsa"]]["archivo"], fila["bolsa"], voz), []).append(fila)
    escritos = []
    for archivo in sorted({k[0] for k in datos}):
        claves = sorted((k for k in datos if k[0] == archivo),
                        key=lambda k: (orden.index(k[1]), comun.VOCES.index(k[2])))
        for carpeta, idioma in (("values", "en"), ("values-es", "es")):
            partes = [CABECERA]
            for _, bolsa, voz in claves:
                partes.append(f'    <string-array name="habla_{bolsa}_{voz}">\n')
                for fila in sorted(datos[(archivo, bolsa, voz)], key=lambda f: f["indice"]):
                    partes.append(f"        <item>{escapar(_item(fila, idioma))}</item>\n")
                partes.append("    </string-array>\n")
            partes.append("</resources>\n")
            destino = os.path.join(dir_res, carpeta, f"strings_habla_{archivo}.xml")
            os.makedirs(os.path.dirname(destino), exist_ok=True)
            with open(destino, "w", encoding="utf-8") as f:
                f.write("".join(partes))
            escritos.append(destino)
    return escritos


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--borradores", default=os.path.join(comun.AQUI, "borradores"))
    p.add_argument("--res", required=True)
    p.add_argument("--minimos", action="store_true", help="exigir también los mínimos por bolsa")
    a = p.parse_args(argv)
    for d in volcar(a.borradores, a.res, minimos=a.minimos):
        print(d)
    return 0


if __name__ == "__main__":
    sys.exit(main())
