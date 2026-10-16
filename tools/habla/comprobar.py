#!/usr/bin/env python3
"""Comprueba borradores TSV con las reglas de spec 8.4. Sale con 1 si hay fallos."""
import os
import re
import sys

import comun

EMOJI = re.compile("[\U0001F000-\U0001FAFF☀-➿⭐⭕️‍←-⇿⌀-⏿]")
IDENTIFICADORES = re.compile(r"\b(review|stats|CHECK|COUNTER|DURATION|ABSTINENCE|habitId|null)\b")
CAMPEON = re.compile(r"campe[oó]n\w*|champ\w*", re.IGNORECASE)
GENERO_ES = re.compile(
    r"\b(estoy|me siento|me quedo|soy|ando|estaba|quedo)\s+"
    r"(content|cansad|dormid|list|orgullos|preocupad|agotad|emocionad|ilusionad|satisfech|tranquil|nervios)[oa]s?\b",
    re.IGNORECASE)
PRONOMBRE_ES = re.compile(r"\b(él|ella)\b", re.IGNORECASE)
GENERO_EN = re.compile(r"\b(he|she|him|her|his|hers)\b", re.IGNORECASE)
PERCENT_SUELTO = re.compile(r"(?<!\} )%")

TITULOS = {"av_manana_titulo", "av_manana_titulo_gordo", "av_noche_titulo", "av_aceptado_titulo"}
CUERPOS = {"av_recordatorio", "av_noche_cuerpo", "av_aceptado_cuerpo", "av_ausencia"}
LIBRES = {"habito", "tarea"}
# Globito y tarjeta del descanso (viven 2,2 a 2,6 s) y respuestas de «¿Cómo estás?» de la sesión.
GLOBITOS_SESION = {"se_entrar", "de_habito", "de_habito_largo", "de_sin_pendientes",
                   "re_fin_respirar", "se_hecha_tarea", "se_hecha_habito"}
RESPUESTAS_SESION = {"ce_mejor", "ce_igual", "ce_cuesta"}


def limite(bolsa):
    if bolsa["bolsa"] in GLOBITOS_SESION:
        return 90
    if bolsa["bolsa"] in RESPUESTAS_SESION:
        return 100
    if bolsa["bolsa"] in TITULOS:
        return 50
    if bolsa["bolsa"] in CUERPOS:
        return 120
    if bolsa["tipo"] == "NUCLEO":
        return 100
    return 150


def _huecos_ok(texto, huecos_validos):
    """Descompone en (nombre, partes) y devuelve (lista, problemas)."""
    problemas, nombres = [], []
    resto = comun.RE_HUECO.sub("", texto)
    if "{" in resto or "}" in resto:
        problemas.append(("llaves", "llaves sin equilibrar"))
    for m in comun.RE_HUECO.finditer(texto):
        partes = m.group(1).split("|")
        if not re.fullmatch(r"[a-z_]+", partes[0]):
            problemas.append(("llaves", f"hueco mal formado: {{{m.group(1)}}}"))
            continue
        if len(partes) not in (1, 3):
            problemas.append(("plural", f"debe ser {{n|singular|plural}}: {{{m.group(1)}}}"))
        nombres.append(partes[0])
    return nombres, problemas


def _libre_bien_puesto(texto, idioma):
    ab, ce = ("«", "»") if idioma == "es" else ("“", "”")
    malos = []
    for m in comun.RE_HUECO.finditer(texto):
        nombre = m.group(1).split("|")[0]
        if nombre not in LIBRES:
            continue
        ant, post = texto[:m.start()], texto[m.end():]
        if ant == "" or ant.endswith(": ") or (ant.endswith(ab) and post.startswith(ce)):
            continue
        malos.append(m.group(0))
    return malos


def _comprobar_texto(bolsa, voz, idioma, texto, fallo):
    nombres, problemas = _huecos_ok(texto, bolsa["huecos"])
    for regla, detalle in problemas:
        fallo(regla, f"[{idioma}] {detalle}")
    for h in dict.fromkeys(nombres):
        if h not in bolsa["huecos"]:
            fallo("hueco no permitido", f"[{idioma}] {{{h}}} en {bolsa['bolsa']}")
    if nombres.count("nombre") > 1:
        fallo("nombre repetido", f"[{idioma}] {{nombre}} más de una vez")
    for h in _libre_bien_puesto(texto, idioma):
        fallo("hueco libre", f"[{idioma}] {h} debe ir tras «: », entre comillas o al principio")
    if texto.lstrip().startswith("["):
        fallo("etiqueta", f"[{idioma}] las etiquetas van en su columna, no en el texto")
    # lista negra
    if EMOJI.search(texto):
        fallo("lista negra", f"[{idioma}] emoji")
    if "—" in texto:
        fallo("lista negra", f"[{idioma}] raya larga")
    if PERCENT_SUELTO.search(texto):
        fallo("lista negra", f"[{idioma}] % suelto (usa «{{pct}} %»)")
    ident = IDENTIFICADORES.search(texto)
    if ident:
        fallo("lista negra", f"[{idioma}] identificador «{ident.group(1)}»")
    if CAMPEON.search(texto):
        fallo("lista negra", f"[{idioma}] «{CAMPEON.search(texto).group(0)}»")
    if idioma == "es":
        g = GENERO_ES.search(texto) or PRONOMBRE_ES.search(texto)
    else:
        g = GENERO_EN.search(texto)
    if g:
        fallo("género", f"[{idioma}] «{g.group(0)}»")
    # voz
    if voz == "neutra" and "!" in texto:
        fallo("exclamación", f"[{idioma}] la Neutra no lleva «!»")
    if voz == "cheerleader":
        if texto.count("!") > 1:
            fallo("exclamación", f"[{idioma}] más de un «!»")
        if idioma == "es" and texto.count("¡") != texto.count("!"):
            fallo("exclamación", f"[{idioma}] «¡» sin cerrar")
    # longitud
    largo = len(comun.RE_HUECO.sub("x" * 20, texto))
    if largo > limite(bolsa):
        fallo("longitud", f"[{idioma}] {largo} > {limite(bolsa)} con huecos de 20")


def comprobar(rutas, minimos=True):
    bolsas = comun.cargar_bolsas()
    fallos = []
    for ruta in rutas:
        voz = comun.voz_de(ruta)
        filas, errores = comun.leer_tsv(ruta)
        for n, regla, detalle in errores:
            fallos.append(f"{ruta}:{n}: {regla}: {detalle}")
        por_bolsa = {}
        for fila in filas:
            def fallo(regla, detalle, fila=fila):
                fallos.append(f"{ruta}:{fila['linea']}: {regla}: {detalle}")
            b = bolsas.get(fila["bolsa"])
            if b is None:
                fallo("bolsa", f"bolsa desconocida «{fila['bolsa']}»")
                continue
            por_bolsa.setdefault(fila["bolsa"], []).append(fila)
            if voz not in comun.VOCES:
                fallo("voz", f"voz desconocida «{voz}» en el nombre del archivo")
            for e in comun.etiquetas_de(fila["etiquetas"]):
                if e not in comun.ETIQUETAS:
                    fallo("etiqueta", f"etiqueta desconocida «{e}»")
            if not fila["es"].strip() or not fila["en"].strip():
                fallo("formato", "falta el texto en ES o en EN")
                continue
            hes = sorted(set(comun.huecos_de(fila["es"])))
            hen = sorted(set(comun.huecos_de(fila["en"])))
            if hes != hen:
                fallo("huecos", f"huecos distintos entre idiomas: ES {hes} / EN {hen}")
            _comprobar_texto(b, voz, "es", fila["es"], fallo)
            _comprobar_texto(b, voz, "en", fila["en"], fallo)
        for nombre, fs in por_bolsa.items():
            indices = sorted(f["indice"] for f in fs)
            if indices != list(range(len(fs))):
                fallos.append(f"{ruta}:{fs[0]['linea']}: índices: {nombre} debe usar 0..{len(fs) - 1} sin huecos ni repetidos")
            if minimos and len(fs) < bolsas[nombre]["minimo"]:
                fallos.append(f"{ruta}:{fs[0]['linea']}: mínimo: {nombre} tiene {len(fs)} y necesita {bolsas[nombre]['minimo']}")
            b = bolsas[nombre]
            if b["grupo"] == "G" and b["nombre"]:
                con = sum(1 for f in fs if "nombre" in comun.huecos_de(f["es"]) or "nombre" in comun.huecos_de(f["en"]))
                sin = len(fs) - con
                if sin == 0 or sin < con:
                    fallos.append(f"{ruta}:{fs[0]['linea']}: hermano: {nombre} necesita al menos tantos items sin {{nombre}} como con él ({con} con, {sin} sin)")
        grupo = comun.grupo_de(ruta)
        if minimos and grupo:
            for nombre, b in bolsas.items():
                if b["grupo"] == grupo and nombre not in por_bolsa:
                    fallos.append(f"{ruta}:0: mínimo: falta la bolsa {nombre} (mínimo {b['minimo']})")
    return fallos


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    minimos = "--sin-minimos" not in argv
    rutas = [a for a in argv if not a.startswith("--")]
    if not rutas:
        print("uso: comprobar.py [--sin-minimos] <tsv>…", file=sys.stderr)
        return 2
    fallos = comprobar(rutas, minimos=minimos)
    for f in fallos:
        print(f)
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
