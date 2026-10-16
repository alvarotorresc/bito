"""Utilidades comunes de las herramientas de textos de Habi."""
import json
import os
import re

AQUI = os.path.dirname(os.path.abspath(__file__))
RUTA_BOLSAS = os.path.join(AQUI, "bolsas.json")

VOCES = ["sargento", "cheerleader", "neutra"]
ETIQUETAS = {
    "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo",
    "finde", "primavera", "verano", "otono", "invierno",
    "madrugada", "manana", "mediodia", "tarde", "noche",
    "despierta", "esperando", "durmiendo",
}

RE_HUECO = re.compile(r"\{([^{}]*)\}")
RE_ETIQUETAS = re.compile(r"^\s*\[([^\]]*)\]\s*")


def cargar_bolsas(ruta=RUTA_BOLSAS):
    """Devuelve {bolsa: entrada} en el orden de bolsas.json."""
    with open(ruta, encoding="utf-8") as f:
        return {b["bolsa"]: b for b in json.load(f)}


def voz_de(ruta):
    """`C.neutra.tsv` -> `neutra`."""
    partes = os.path.basename(ruta).split(".")
    return partes[-2] if len(partes) >= 3 else ""


def grupo_de(ruta):
    partes = os.path.basename(ruta).split(".")
    if len(partes) >= 3 and re.fullmatch(r"[A-F]", partes[0]):
        return partes[0]
    return None


def leer_tsv(ruta):
    """Devuelve (filas, errores). Fila = dict(linea, bolsa, indice, etiquetas, es, en)."""
    filas, errores = [], []
    with open(ruta, encoding="utf-8") as f:
        for n, linea in enumerate(f.read().split("\n"), start=1):
            if n == 1 or linea.strip() == "":
                continue
            c = linea.split("\t")
            if len(c) != 5:
                errores.append((n, "formato", f"se esperaban 5 columnas y hay {len(c)}"))
                continue
            try:
                indice = int(c[1])
            except ValueError:
                errores.append((n, "formato", f"índice no numérico: {c[1]!r}"))
                continue
            filas.append({"linea": n, "bolsa": c[0].strip(), "indice": indice,
                          "etiquetas": c[2].strip(), "es": c[3], "en": c[4]})
    return filas, errores


def etiquetas_de(texto):
    """Etiquetas al principio de un texto (`[a,b] ...`) o en una columna (`[a,b]` / `a,b`)."""
    m = RE_ETIQUETAS.match(texto)
    crudo = m.group(1) if m else texto.strip()
    return [e.strip() for e in crudo.split(",") if e.strip()]


def quitar_etiquetas(texto):
    return RE_ETIQUETAS.sub("", texto, count=1)


def huecos_de(texto):
    """Nombres de hueco usados, en orden y con repeticiones (`{n|día|días}` cuenta como `n`)."""
    return [m.group(1).split("|")[0] for m in RE_HUECO.finditer(texto)]


def _unir(valores, idioma):
    y = " y " if idioma == "es" else " and "
    valores = [str(v) for v in valores]
    if len(valores) <= 1:
        return "".join(valores)
    return ", ".join(valores[:-1]) + y + valores[-1]


def rellenar(texto, valores, idioma="es"):
    """Rellena huecos (spec 8.2). Lanza KeyError si falta un valor."""
    texto = quitar_etiquetas(texto)

    def sustituir(m):
        partes = m.group(1).split("|")
        v = valores[partes[0]]
        if len(partes) == 3:
            n = int(v)
            return f"{n} {partes[1] if n == 1 else partes[2]}"
        if isinstance(v, (list, tuple)):
            return _unir(v, idioma)
        return str(v)

    return RE_HUECO.sub(sustituir, texto)
