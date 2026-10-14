#!/usr/bin/env python3
"""Compone 30 frases de muestra de un borrador TSV para revisarlas a ojo."""
import argparse
import os
import random
import sys

import comprobar
import comun

NOMBRE_LARGO = "Estiramientos largos"

# Cada escenario es un contexto de prueba; `sin_nombre` quita el hueco {nombre}.
ESCENARIOS = [
    {"fase": "manana", "etiquetas": {"lunes", "invierno", "manana"}, "nombre": NOMBRE_LARGO,
     "habito": NOMBRE_LARGO, "tarea": "Llamar al dentista", "n": 1},
    {"fase": "noche", "etiquetas": {"lunes", "invierno", "noche"}, "nombre": "Ana",
     "habito": "Agua 2 L", "tarea": "Sacar la basura", "n": 3},
    {"fase": "tarde", "etiquetas": {"sabado", "finde", "verano", "tarde"}, "nombre": None,
     "habito": "Flexiones", "tarea": "Pagar el alquiler", "n": 2},
    {"fase": "mediodia", "etiquetas": {"miercoles", "primavera", "mediodia"}, "nombre": None,
     "habito": "Leer", "tarea": "Comprar pan y leche", "n": 1},
    {"fase": "noche", "etiquetas": {"domingo", "finde", "invierno", "noche", "despierta"}, "nombre": NOMBRE_LARGO,
     "habito": "Meditación de la tarde", "tarea": "Preparar la maleta", "n": 5},
    {"fase": "manana", "etiquetas": {"jueves", "otono", "manana", "esperando"}, "nombre": "Ana",
     "habito": "Estiramientos largos", "tarea": "Renovar el carné", "n": 4},
]


def _valores(esc, idioma):
    v = {
        "habito": esc["habito"], "tarea": esc["tarea"], "gordo": [esc["tarea"], "Llamar a mamá"],
        "primer_paso": "abrir el correo", "hora": "18:30" if idioma == "es" else "6:30 PM",
        "minutos": 25, "n": esc["n"], "dias_fuera": 3, "racha": 12, "faltan": 2, "bitos": 40,
        "dia_semana": "lunes" if idioma == "es" else "Monday", "hechos": 3, "total": 5,
        "mejor": 21, "pct": 80, "objetivo": 30, "fecha": "3 de noviembre" if idioma == "es" else "November 3",
        "lista": [esc["tarea"], "Regar las plantas", "Llamar a mamá"],
    }
    if esc["nombre"]:
        v["nombre"] = esc["nombre"]
    return v


def _elegible(fila, esc, bolsa):
    hu = set(comun.huecos_de(fila["es"]))
    disponibles = set(_valores(esc, "es"))
    if not hu <= disponibles:
        return False
    return True


def _pesos(filas, esc):
    return [2 if set(comun.etiquetas_de(f["etiquetas"])) & esc["etiquetas"] else 1 for f in filas]


def _elegir(rng, filas, esc, bolsas):
    ok = []
    for f in filas:
        et = set(comun.etiquetas_de(f["etiquetas"]))
        if _elegible(f, esc, bolsas[f["bolsa"]]) and et <= esc["etiquetas"]:
            ok.append(f)
    if not ok:
        return None
    return rng.choices(ok, weights=_pesos(ok, esc))[0]


def _plan(ruta, n, semilla):
    """Lista de listas de filas por frase; independiente del idioma."""
    bolsas = comun.cargar_bolsas()
    filas, _ = comun.leer_tsv(ruta)
    por = {}
    for f in filas:
        por.setdefault(f["bolsa"], []).append(f)
    tipo = lambda b: bolsas[b]["tipo"]
    nucleos = [b for b in por if tipo(b) in ("NUCLEO", "SUELTA")]
    aisladas = not nucleos
    if aisladas:
        nucleos = list(por)
    rng = random.Random(semilla)
    plan, i, intentos = [], 0, 0
    while len(plan) < n and intentos < n * 50:
        intentos += 1
        esc = ESCENARIOS[(intentos - 1) % len(ESCENARIOS)]
        bolsa = nucleos[len(plan) % len(nucleos)]
        nucleo = _elegir(rng, por[bolsa], esc, bolsas)
        if nucleo is None:
            continue
        trozos = []
        aperturas = [b for b in por if tipo(b) == "APERTURA"]
        if not aisladas and aperturas and rng.random() < 0.5:
            fase = [b for b in aperturas if b == "ap_" + esc["fase"]] or aperturas
            ap = _elegir(rng, por[rng.choice(fase)], esc, bolsas)
            if ap:
                trozos.append(ap)
        trozos.append(nucleo)
        if not aisladas and rng.random() < 0.4:
            recuerdos = [b for b in por if tipo(b) == "RECUERDO"]
            cierres = [b for b in por if tipo(b) == "CIERRE"]
            pool = recuerdos if recuerdos and rng.random() < 0.3 else cierres
            if pool:
                c = _elegir(rng, por[rng.choice(pool)], esc, bolsas)
                if c:
                    trozos.append(c)
        plan.append((esc, trozos))
    return plan


def componer(ruta, n=30, semilla=1, idioma="es"):
    return [" ".join(comun.rellenar(t[idioma], _valores(esc, idioma), idioma) for t in trozos)
            for esc, trozos in _plan(ruta, n, semilla)]


def escribir(ruta, salida=None, n=30, semilla=1, minimos=True):
    fallos = comprobar.comprobar([ruta], minimos=minimos)
    if fallos:
        for f in fallos:
            print(f, file=sys.stderr)
        raise SystemExit(1)
    salida = salida or os.path.join(comun.AQUI, "muestras")
    nombre = os.path.basename(ruta)
    nombre = (nombre[:-4] if nombre.endswith(".tsv") else nombre) + ".md"
    es = componer(ruta, n, semilla, "es")
    en = componer(ruta, n, semilla, "en")
    lineas = [f"# Muestra {nombre[:-3]}", ""]
    for i, (a, b) in enumerate(zip(es, en), start=1):
        lineas += [f"{i}. ES: {a}", f"   EN: {b}", ""]
    destino = os.path.join(salida, nombre)
    os.makedirs(salida, exist_ok=True)
    with open(destino, "w", encoding="utf-8") as f:
        f.write("\n".join(lineas))
    return destino


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("tsv")
    p.add_argument("--salida")
    p.add_argument("--n", type=int, default=30)
    p.add_argument("--semilla", type=int, default=1)
    p.add_argument("--sin-minimos", action="store_true")
    a = p.parse_args(argv)
    print(escribir(a.tsv, a.salida, a.n, a.semilla, minimos=not a.sin_minimos))
    return 0


if __name__ == "__main__":
    sys.exit(main())
