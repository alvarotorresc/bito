#!/usr/bin/env python3
"""Compone 30 frases de muestra de un borrador TSV para revisarlas a ojo."""
import argparse
import os
import random
import sys

import comprobar
import comun

NOMBRE_LARGO = "Maria de los Angeles"  # 20 caracteres, para el peor caso de longitud

# Cada escenario es un contexto de prueba; con `nombre` None se compone «sin nombre».
ESCENARIOS = [
    {"fase": "manana", "etiquetas": {"lunes", "invierno", "manana"}, "nombre": NOMBRE_LARGO,
     "habito": "Estiramientos largos", "tarea": "Llamar al dentista", "n": 1},
    {"fase": "noche", "etiquetas": {"lunes", "invierno", "noche"}, "nombre": "Ana",
     "habito": "Agua 2 L", "tarea": "Sacar la basura", "n": 3},
    {"fase": "tarde", "etiquetas": {"sabado", "finde", "verano", "tarde"}, "nombre": None,
     "habito": "Flexiones", "tarea": "Pagar el alquiler", "n": 2},
    {"fase": "mediodia", "etiquetas": {"miercoles", "primavera", "mediodia"}, "nombre": "Luis",
     "habito": "Leer", "tarea": "Comprar pan y leche", "n": 1},
    {"fase": "noche", "etiquetas": {"domingo", "finde", "invierno", "noche", "despierta"}, "nombre": None,
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


def _elegible(fila, esc):
    return set(comun.huecos_de(fila["es"])) <= set(_valores(esc, "es")) \
        and set(comun.etiquetas_de(fila["etiquetas"])) <= esc["etiquetas"]


def _elegir(rng, filas, esc, usados):
    ok = [f for f in filas if _elegible(f, esc)]
    if not ok:
        return None
    nuevos = [f for f in ok if (f["bolsa"], f["indice"]) not in usados]
    ok = nuevos or ok
    pesos = [2 if set(comun.etiquetas_de(f["etiquetas"])) & esc["etiquetas"] else 1 for f in ok]
    return rng.choices(ok, weights=pesos)[0]


def _plan(ruta, n, semilla):
    """Lista de (indice de escenario, trozos); independiente del idioma y sin repetidos."""
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
    plan, vistos, usados, intentos = [], set(), set(), 0
    while len(plan) < n and intentos < n * 200:
        intentos += 1
        ie = (intentos - 1) % len(ESCENARIOS)
        esc = ESCENARIOS[ie]
        todos = [f for b in nucleos for f in por[b]]
        nucleo = _elegir(rng, todos, esc, usados)
        if nucleo is None:
            continue
        trozos = []
        aperturas = [b for b in por if tipo(b) == "APERTURA"]
        if not aisladas and aperturas and rng.random() < 0.5:
            fase = [b for b in aperturas if b == "ap_" + esc["fase"]] or aperturas
            ap = _elegir(rng, por[rng.choice(fase)], esc, usados)
            if ap:
                trozos.append(ap)
        trozos.append(nucleo)
        if not aisladas and rng.random() < 0.4:
            recuerdos = [b for b in por if tipo(b) == "RECUERDO"]
            cierres = [b for b in por if tipo(b) == "CIERRE"]
            pool = recuerdos if recuerdos and rng.random() < 0.3 else cierres
            if pool:
                c = _elegir(rng, por[rng.choice(pool)], esc, usados)
                if c:
                    trozos.append(c)
        clave = (ie, tuple((t["bolsa"], t["indice"]) for t in trozos))
        texto = (" ".join(comun.rellenar(t["es"], _valores(esc, "es"), "es") for t in trozos))
        if clave in vistos or texto in vistos:
            continue
        vistos.update((clave, texto))
        usados.update((t["bolsa"], t["indice"]) for t in trozos)
        plan.append((ie, trozos, nucleo))
    return plan


def componer_detalle(ruta, n=30, semilla=1):
    """Cada frase con su escenario: es, en, bolsas, huecos usados (con valor) y etiquetas."""
    salida = []
    for ie, trozos, nucleo in _plan(ruta, n, semilla):
        esc = ESCENARIOS[ie]
        vals = _valores(esc, "es")
        usados = []
        for t in trozos:
            for h in comun.huecos_de(t["es"]):
                if h not in usados:
                    usados.append(h)
        salida.append({
            "es": " ".join(comun.rellenar(t["es"], _valores(esc, "es"), "es") for t in trozos),
            "en": " ".join(comun.rellenar(t["en"], _valores(esc, "en"), "en") for t in trozos),
            "bolsas": [t["bolsa"] for t in trozos],
            "nucleo": (nucleo["bolsa"], nucleo["indice"]),
            "escenario": ie,
            "datos": {h: vals[h] for h in usados},
            "sin_nombre": esc["nombre"] is None,
            "etiquetas": sorted(esc["etiquetas"]),
        })
    return salida


def componer(ruta, n=30, semilla=1, idioma="es"):
    return [d[idioma] for d in componer_detalle(ruta, n, semilla)]


def _datos(d):
    v = ", ".join(f"{k}={' / '.join(x) if isinstance(x, list) else x}" for k, x in d["datos"].items())
    if d["sin_nombre"]:
        v = (v + ", " if v else "") + "(sin nombre)"
    return f"{v or 'sin huecos'} | etiquetas: {', '.join(d['etiquetas'])} | bolsas: {', '.join(d['bolsas'])}"


def escribir(ruta, salida=None, n=30, semilla=1, minimos=True):
    fallos = comprobar.comprobar([ruta], minimos=minimos)
    if fallos:
        for f in fallos:
            print(f, file=sys.stderr)
        raise SystemExit(1)
    detalle = componer_detalle(ruta, n, semilla)
    if len(detalle) < n:
        print(f"{ruta}: la muestra solo llega a {len(detalle)} de {n} frases distintas; "
              "faltan items o hay demasiados con huecos sin datos de prueba", file=sys.stderr)
        raise SystemExit(1)
    salida = salida or os.path.join(comun.AQUI, "muestras")
    nombre = os.path.basename(ruta)
    nombre = (nombre[:-4] if nombre.endswith(".tsv") else nombre) + ".md"
    lineas = [f"# Muestra {nombre[:-3]}", ""]
    for i, d in enumerate(detalle, start=1):
        lineas += [f"{i}. ES: {d['es']}", f"   EN: {d['en']}", f"   datos: {_datos(d)}", ""]
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
