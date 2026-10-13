"""Pasa las piezas a WebP recortado y genera el atlas de Habi, sus tintes, su tabla de caras y el jardín en Kotlin.

Uso, desde la raíz del repo:
  python3 tools/arte/exportar.py
  python3 tools/arte/exportar.py --definitivo --estado ~/Pictures/bito-habi/galeria/estado.json

Lee tools/arte/habi.json, jardin.json, tintes.json y caras.json. Borra y vuelve a escribir los
app/src/main/res/drawable-nodpi/{habi,costume,garden}_*.webp y genera HabiAtlas.kt,
HabiTintTable.kt, HabiFaceTable.kt (ui/habi), GardenArt.kt y GardenLookTable.kt (ui/garden).
Códigos: 0 bien; 5 los WebP pasan de 6 MB (bajar --calidad); 6 hay piezas provisionales o sin
aprobar en --definitivo (no se escribe nada).

Disfraces: la cara se pinta desplazada (dx, dy) y recortada por el hueco del disfraz, como en el
prototipo vivo. La máscara del hueco (`costume_<x>_hueco`) es la piel verde del disfraz, con sus
huecos interiores rellenos, dilatada 1 px y suavizada; no se mueve con la cara. El desplazamiento
aprobado va en el manifiesto (`"cara": [dx, dy]`); si falta, se calcula como el centro del hueco
menos REFERENCIA_CARA.

Sección `jardin` de tintes.json (la genera GardenLookTable.kt y la lee la galería):
  {"mustio_planta": {"saturacion", "sepia", "brillo", "encoge", "inclina"},   por nivel de mustio
   "mustio_cesped": {"saturacion", "sepia", "brillo"},                       por nivel de mustio
   "estaciones": {"SPRING" | "SUMMER" | "AUTUMN" | "WINTER": tinte},         la que falta no tiñe
   "luz": {"MANANA" | "DIA" | "TARDE" | "NOCHE": ["#RRGGBB arriba", "#RRGGBB abajo"]},
   "hundido_tierra": fracción}
"""
import argparse
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np  # noqa: E402
from PIL import Image, ImageFilter  # noqa: E402

import comun  # noqa: E402

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, "..", ".."))
RES = os.path.join(RAIZ, "app", "src", "main", "res", "drawable-nodpi")
KT_HABI = os.path.join(RAIZ, "app", "src", "main", "java", "com", "alvarotc", "bito", "ui", "habi")
KT_JARDIN = os.path.join(RAIZ, "app", "src", "main", "java", "com", "alvarotc", "bito", "ui", "garden")
PREFIJOS = ("habi_", "costume_", "garden_")
LIMITE_BYTES = 6 * 1024 * 1024
UMBRAL_ALFA = 8
CABECERA = "// Generado por tools/arte/exportar.py. No editar a mano.\n"
MAX_LINEA = 120
# Centro de la cara en coordenadas del cuerpo: un hueco centrado aquí no desplaza la cara.
REFERENCIA_CARA = (619, 510)
# Un «hueco» de más de esta fracción del rectángulo del cuerpo no es un hueco de cara (por ejemplo,
# un disfraz provisional que reutiliza cuerpo.png): ese disfraz no mueve ni recorta la cara.
HUECO_MAX = 0.4


def cargar_json(ruta):
    with open(os.path.expanduser(ruta), encoding="utf-8") as f:
        return json.load(f)


def ruta_fuente(manifiesto, ruta_manifiesto, archivo):
    archivo = os.path.expanduser(archivo)
    if os.path.isabs(archivo):
        return archivo
    base = os.path.expanduser(manifiesto.get("fuentes", "."))
    if not os.path.isabs(base):
        base = os.path.join(os.path.dirname(os.path.abspath(ruta_manifiesto)), base)
    return os.path.join(base, archivo)


def nombre_recurso(clave, prefijo):
    if clave.startswith("costume-"):
        return "costume_" + clave[len("costume-") :].replace("-", "_")
    return prefijo + clave.replace("-", "_")


def caja_alfa(img):
    """Caja (x0, y0, x1, y1) del contenido, ignorando el ruido de alfa casi nulo; None si está vacía."""
    alfa = np.asarray(img)[..., 3]
    ys, xs = np.nonzero(alfa > UMBRAL_ALFA)
    if len(xs) == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def mascara_iris(img):
    """El iris (marrón saturado) de un ojo, sin el brillo blanco ni el contorno oscuro."""
    arr = np.asarray(img).astype(np.float32)
    h, s, v = comun.hsv(arr[..., :3])
    iris = (h < 50) & (s > 0.45) & (v > 0.12) & (arr[..., 3] > 127)
    salida = arr.copy()
    salida[..., 3] = np.where(iris, arr[..., 3], 0)
    return Image.fromarray(salida.astype(np.uint8), "RGBA"), bool(iris.any())


def alfa_hueco(original, area_max):
    """Alfa (0..255, al tamaño de `original`) del hueco de cara de un disfraz, o None si no lo hay.

    `area_max` en píxeles de la imagen: un hueco mayor no es de cara.
    """
    arr = np.asarray(original).astype(np.float32)
    verde = comun.mascara_verde(arr[..., :3]) & (arr[..., 3] > 127)
    if not verde.any():
        return None
    hueco = comun.rellenar_huecos(comun.componentes_grandes(verde, paso=1, fraccion=1.0))
    if not hueco.any() or hueco.sum() > area_max:
        return None
    img = Image.fromarray((hueco * 255).astype(np.uint8), "L")
    img = img.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(1))
    return np.asarray(img)


def sombra(ancho, alto):
    """La sombra del prototipo: elipse difusa rgba(60, 40, 20, .32) en degradado radial."""
    yy, xx = np.mgrid[0:alto, 0:ancho]
    d = np.sqrt(((xx + 0.5 - ancho / 2) / (ancho / 2)) ** 2 + ((yy + 0.5 - alto / 2) / (alto / 2)) ** 2)
    arr = np.zeros((alto, ancho, 4), np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2] = 60, 40, 20
    arr[..., 3] = (np.clip(1 - d, 0, 1) * 0.32 * 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def guardar_webp(img, ruta, calidad):
    img.save(ruta, "WEBP", quality=calidad, alpha_quality=100, method=6)


def _rect(x0, y0, e, caja):
    """Rectángulo (left, top, ancho, alto) en coordenadas del cuerpo de la `caja` de una imagen pegada en (x0, y0) a escala e."""
    return float(x0 + caja[0] * e), float(y0 + caja[1] * e), float((caja[2] - caja[0]) * e), float((caja[3] - caja[1]) * e)


def _recortar(original, caja, rect, escala_salida):
    tamano = (max(1, round(rect[2] * escala_salida)), max(1, round(rect[3] * escala_salida)))
    return original.crop(caja).resize(tamano, Image.LANCZOS)


def _posicion(pieza, caras):
    """(origen o centro, escala) de una pieza: la suya o, en las de cara, la de caras.json."""
    if "origen" in pieza or "centro" in pieza:
        return pieza, float(pieza.get("escala", 1))
    sitio = (caras or {}).get("piezas", {}).get(pieza["clave"])
    if sitio is None:
        raise SystemExit(f"{pieza['clave']}: sin origen ni centro, y no está en caras.json")
    return sitio, float(sitio.get("escala", 1))


def _cara_disfraz(pieza, original, x0, y0, e, cuerpo, escala_salida, recurso, dir_res, calidad):
    """Desplazamiento de cara y máscara del hueco de un disfraz, o None si no tiene hueco de cara."""
    area_max = HUECO_MAX * cuerpo["ancho"] * cuerpo["alto"] / (e * e)
    alfa = alfa_hueco(original, area_max)
    if alfa is None:
        print(f"AVISO {pieza['clave']}: sin hueco de cara; con este disfraz la cara no se mueve ni se recorta")
        return None
    rgba = np.zeros(alfa.shape + (4,), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = alfa
    mascara = Image.fromarray(rgba, "RGBA")
    caja = caja_alfa(mascara)
    rect = _rect(x0, y0, e, caja)
    nombre = recurso + "_hueco"
    guardar_webp(_recortar(mascara, caja, rect, escala_salida), os.path.join(dir_res, nombre + ".webp"), calidad)
    if "cara" in pieza:
        dx, dy = (float(v) for v in pieza["cara"])
    else:
        # Centro por índice de píxel, como se midió REFERENCIA_CARA.
        peso = alfa.astype(np.float64)
        yy, xx = np.mgrid[0 : alfa.shape[0], 0 : alfa.shape[1]]
        cx = x0 + (xx * peso).sum() / peso.sum() * e
        cy = y0 + (yy * peso).sum() / peso.sum() * e
        dx, dy = round(float(cx - REFERENCIA_CARA[0]), 1), round(float(cy - REFERENCIA_CARA[1]), 1)
        print(f"AVISO {pieza['clave']}: desplazamiento de cara calculado ({dx}, {dy}); el aprobado va en \"cara\"")
    left, top, ancho, alto = rect
    return {"dx": dx, "dy": dy, "mascara": {"recurso": nombre, "left": left, "top": top, "ancho": ancho, "alto": alto}}


def exportar_habi(manifiesto, ruta_manifiesto, dir_res, calidad, caras=None):
    """Exporta las piezas de Habi. Devuelve (entradas, lienzo (left, top, ancho, alto), fuente_px).

    El lienzo es simétrico en horizontal respecto al centro del cuerpo: `rigFit` centra el lienzo,
    y una cola que solo sale por un lado descentraría a Habi. Las piezas de cara sin posición la
    toman de `caras` (caras.json). Los disfraces llevan en "cara" su desplazamiento y su máscara.
    """
    cuerpo = manifiesto["cuerpo"]
    escala_salida = manifiesto["alto_salida"] / cuerpo["alto"]
    entradas = []
    for pieza in manifiesto["piezas"]:
        clave = pieza["clave"]
        recurso = nombre_recurso(clave, "habi_")
        iris = None
        cara = None
        if pieza.get("generada") == "sombra":
            left, top, ancho, alto = (float(v) for v in pieza["caja"])
            img = sombra(max(1, round(ancho * escala_salida)), max(1, round(alto * escala_salida)))
        else:
            original = Image.open(ruta_fuente(manifiesto, ruta_manifiesto, pieza["archivo"])).convert("RGBA")
            caja = caja_alfa(original)
            if caja is None:
                raise SystemExit(f"{clave}: la pieza está vacía")
            sitio, e = _posicion(pieza, caras)
            if "centro" in sitio:
                x0, y0 = sitio["centro"][0] - original.width * e / 2, sitio["centro"][1] - original.height * e / 2
            else:
                x0, y0 = sitio["origen"]
            left, top, ancho, alto = _rect(x0, y0, e, caja)
            img = _recortar(original, caja, (left, top, ancho, alto), escala_salida)
            if pieza.get("iris"):
                mascara, hay = mascara_iris(img)
                if hay:
                    iris = recurso + "_iris"
                    guardar_webp(mascara, os.path.join(dir_res, iris + ".webp"), calidad)
                else:
                    print(f"AVISO {clave}: máscara de iris vacía, el color de ojos no la teñirá")
            if clave.startswith("costume-"):
                cara = _cara_disfraz(pieza, original, x0, y0, e, cuerpo, escala_salida, recurso, dir_res, calidad)
        guardar_webp(img, os.path.join(dir_res, recurso + ".webp"), calidad)
        entrada = {
            "clave": clave,
            "recurso": recurso,
            "left": left,
            "top": top,
            "ancho": ancho,
            "alto": alto,
            "iris": iris,
            "provisional": bool(pieza.get("provisional")),
        }
        if clave.startswith("costume-"):
            entrada["cara"] = cara
        entradas.append(entrada)
    top = min(e["top"] for e in entradas)
    abajo = max(e["top"] + e["alto"] for e in entradas)
    margen = max(
        0.0,
        -min(e["left"] for e in entradas),
        max(e["left"] + e["ancho"] for e in entradas) - cuerpo["ancho"],
    )
    lienzo = (-margen, top, cuerpo["ancho"] + 2 * margen, abajo - top)
    return entradas, lienzo, lienzo[3] * escala_salida


def _pieza_jardin(manifiesto, ruta_manifiesto, archivo, recurso, dir_res, escala, calidad):
    original = Image.open(ruta_fuente(manifiesto, ruta_manifiesto, archivo)).convert("RGBA")
    caja = caja_alfa(original)
    if caja is None:
        raise SystemExit(f"{archivo}: la pieza está vacía")
    img = original.crop(caja)
    alto_fuente = img.height
    img = img.resize((max(1, round(img.width * escala)), max(1, round(img.height * escala))), Image.LANCZOS)
    guardar_webp(img, os.path.join(dir_res, recurso + ".webp"), calidad)
    return {"recurso": recurso, "aspecto": img.width / img.height, "alto_fuente": alto_fuente}


def exportar_jardin(manifiesto, ruta_manifiesto, dir_res, calidad):
    escala = float(manifiesto["escala_piezas"])
    fondo = Image.open(ruta_fuente(manifiesto, ruta_manifiesto, manifiesto["fondo"]["archivo"])).convert("RGB")
    ancho = int(manifiesto["ancho_fondo"])
    fondo = fondo.resize((ancho, round(fondo.height * ancho / fondo.width)), Image.LANCZOS)
    fondo.save(os.path.join(dir_res, "garden_fondo.webp"), "WEBP", quality=calidad, method=6)
    parcelas = []
    for i, p in enumerate(manifiesto["parcelas"]):
        d = _pieza_jardin(manifiesto, ruta_manifiesto, p["archivo"], f"garden_parcela_{i + 1}", dir_res, escala, calidad)
        d["ratio"] = float(manifiesto["alto_parcela"])
        parcelas.append(d)
    especies = []
    for especie in manifiesto["especies"]:
        etapas = [
            _pieza_jardin(
                manifiesto,
                ruta_manifiesto,
                archivo,
                nombre_recurso(f"{especie['clave']}-{n + 1}", "garden_"),
                dir_res,
                escala,
                calidad,
            )
            for n, archivo in enumerate(especie["etapas"])
        ]
        arbol = etapas[-1]["alto_fuente"]
        for etapa in etapas:
            etapa["ratio"] = etapa["alto_fuente"] / arbol
        especies.append(etapas)
    especiales = {}
    for e in manifiesto["especiales"]:
        d = _pieza_jardin(manifiesto, ruta_manifiesto, e["archivo"], nombre_recurso(e["clave"], "garden_"), dir_res, escala, calidad)
        d["ratio"] = 1.0
        especiales[e["clave"]] = d
    return {
        "aspecto": fondo.width / fondo.height,
        "parcelas": parcelas,
        "especies": especies,
        "claves_especies": [e["clave"] for e in manifiesto["especies"]],
        "especiales": especiales,
        "escena": manifiesto["escena"],
    }


def _f(v, decimales=1):
    return f"{float(v):.{decimales}f}f"


def _num(v):
    """Un Float de Kotlin con hasta 6 decimales y sin ceros de más (0.2f, 1.0f, 0.033333f)."""
    return f"{round(float(v), 6)!r}f"


def _cadena(s):
    return "null" if s is None else f'"{s}"'


def _llamada(nombre, args):
    return f"{nombre}({', '.join(args)})"


def _entrada_mapa(clave, nombre, args, sangria=12):
    """Una entrada `"clave" to Nombre(args),` partida como ktlint la dejaría si no cabe en MAX_LINEA."""
    valor = _llamada(nombre, args)
    linea = " " * sangria + f'"{clave}" to {valor},'
    if len(linea) <= MAX_LINEA:
        return linea + "\n"
    dentro = " " * (sangria + 4)
    if len(dentro + valor + ",") <= MAX_LINEA:
        return " " * sangria + f'"{clave}" to\n' + dentro + valor + ",\n"
    argumentos = "".join(" " * (sangria + 8) + a + ",\n" for a in args)
    return " " * sangria + f'"{clave}" to\n' + dentro + f"{nombre}(\n" + argumentos + dentro + "),\n"


def _mapa(cabecera, entradas, sangria=4):
    """`cabecera =` seguido de mapOf(...) con las entradas ya formateadas, o `= emptyMap()`."""
    if not entradas:
        return " " * sangria + f"{cabecera} = emptyMap()\n"
    return (
        " " * sangria
        + f"{cabecera} =\n"
        + " " * (sangria + 4)
        + "mapOf(\n"
        + "".join(entradas)
        + " " * (sangria + 4)
        + ")\n"
    )


def _lista_cadenas(cabecera, valores, sangria=4):
    """`cabecera = listOf("a", "b")` en una línea si cabe; si no, un valor por línea."""
    linea = " " * sangria + f"{cabecera} = {_llamada('listOf', [_cadena(v) for v in valores])}"
    if len(linea) <= MAX_LINEA:
        return linea + "\n"
    cuerpo = "".join(" " * (sangria + 8) + _cadena(v) + ",\n" for v in valores)
    return " " * sangria + f"{cabecera} =\n" + " " * (sangria + 4) + "listOf(\n" + cuerpo + " " * (sangria + 4) + ")\n"


def kotlin_atlas(entradas, lienzo, cuerpo, fuente_px):
    left, top, ancho, alto = lienzo
    partes = [
        CABECERA,
        "package com.alvarotc.bito.ui.habi\n\n",
        "import com.alvarotc.bito.R\n\n",
        "/** Las piezas de Habi con su rectángulo en coordenadas del cuerpo. */\n",
        "internal object HabiAtlas {\n",
        f"    const val BODY_WIDTH = {_f(cuerpo['ancho'])}\n",
        f"    const val BODY_HEIGHT = {_f(cuerpo['alto'])}\n\n",
        "    /** Alto del lienzo a la resolución exportada: la referencia del submuestreo. */\n",
        f"    const val SOURCE_CANVAS_PX = {_f(fuente_px)}\n\n",
        "    /**\n",
        "     * El cuerpo ocupa (0, 0)-(BODY_WIDTH, BODY_HEIGHT); el resto es el margen calculado, simétrico en\n",
        "     * horizontal para que centrar el lienzo centre a Habi.\n",
        "     */\n",
        f"    val canvas = RigCanvas(left = {_f(left)}, top = {_f(top)}, width = {_f(ancho)}, height = {_f(alto)})\n\n",
    ]
    piezas = []
    for e in entradas:
        args = [f"R.drawable.{e['recurso']}", _f(e["left"]), _f(e["top"]), _f(e["ancho"]), _f(e["alto"])]
        if e["iris"]:
            args.append(f"iris = R.drawable.{e['iris']}")
        piezas.append(_entrada_mapa(e["clave"], "AtlasPiece", args))
    partes.append(_mapa("val pieces: Map<String, AtlasPiece>", piezas))
    caras = []
    for e in entradas:
        cara = e.get("cara")
        if not cara:
            continue
        m = cara["mascara"]
        mascara = _llamada("AtlasPiece", [f"R.drawable.{m['recurso']}", _f(m["left"]), _f(m["top"]), _f(m["ancho"]), _f(m["alto"])])
        caras.append(_entrada_mapa(e["clave"], "CostumeFace", [_f(cara["dx"]), _f(cara["dy"]), mascara]))
    partes.append(
        "\n    /**\n"
        "     * Con disfraz, la cara se desplaza (dx, dy) en coordenadas del cuerpo y se recorta con la\n"
        "     * máscara del hueco, que no se mueve. Un disfraz sin entrada deja la cara como está.\n"
        "     */\n",
    )
    partes.append(_mapa("val costumeFaces: Map<String, CostumeFace>", caras))
    partes.append("}\n")
    return "".join(partes)


def _tinte_args(t):
    return [
        f"hueDegrees = {_f(t.get('hue', 0))}",
        f"saturation = {_num(t.get('saturacion', 1))}",
        f"brightness = {_num(t.get('brillo', 1))}",
        f"sepia = {_num(t.get('sepia', 0))}",
    ]


def kotlin_tintes(tintes):
    partes = [
        CABECERA,
        "package com.alvarotc.bito.ui.habi\n\n",
        "/** Tintes del catálogo, afinados en la galería (tools/arte/tintes.json). */\n",
        "internal object HabiTintTable {\n",
    ]
    for nombre, clave in (("body", "cuerpo"), ("eyes", "ojos")):
        entradas = [_entrada_mapa(id_, "ColorTint", _tinte_args(t)) for id_, t in tintes[clave].items()]
        partes.append(_mapa(f"val {nombre}: Map<String, ColorTint>", entradas) + "\n")
    partes.append(f"    val muted = {_llamada('ColorTint', _tinte_args(tintes['apagado']))}\n}}\n")
    return "".join(partes)


def _color_argb(hexa):
    return f"0xFF{hexa.lstrip('#').upper()}L"


def kotlin_aspecto_jardin(jardin):
    """GardenLookTable.kt desde la sección `jardin` de tintes.json (lo que afina la galería)."""
    planta, cesped = jardin["mustio_planta"], jardin["mustio_cesped"]
    estaciones = [_entrada_mapa(k, "ColorTint", _tinte_args(t)) for k, t in jardin.get("estaciones", {}).items()]
    luces = [
        " " * 12 + f'"{k}" to ({_color_argb(arriba)} to {_color_argb(abajo)}),\n'
        for k, (arriba, abajo) in jardin["luz"].items()
    ]
    partes = [
        CABECERA,
        "package com.alvarotc.bito.ui.garden\n\n",
        "import com.alvarotc.bito.ui.habi.ColorTint\n\n",
        "/** Aspecto del jardín según su estado, afinado en la galería (tools/arte/tintes.json, sección jardin). */\n",
        "internal object GardenLookTable {\n",
        "    /** Lo que pierden las plantas por cada nivel de mustio. */\n",
        f"    const val PLANT_SATURATION_LOSS = {_num(planta['saturacion'])}\n",
        f"    const val PLANT_SEPIA = {_num(planta['sepia'])}\n",
        f"    const val PLANT_DIM = {_num(planta['brillo'])}\n",
        f"    const val PLANT_SHRINK = {_num(planta['encoge'])}\n",
        f"    const val PLANT_LEAN_DEGREES = {_num(planta['inclina'])}\n\n",
        "    /** Lo que pierde el césped (el fondo) por cada nivel de mustio. */\n",
        f"    const val GRASS_SATURATION_LOSS = {_num(cesped['saturacion'])}\n",
        f"    const val GRASS_SEPIA = {_num(cesped['sepia'])}\n",
        f"    const val GRASS_DIM = {_num(cesped['brillo'])}\n\n",
        "    /** Fracción del alto de la tierra que se hunde bajo el punto de la parcela. */\n",
        f"    const val SOIL_SINK = {_num(jardin['hundido_tierra'])}\n\n",
        "    /** Tinte de cada estación, por nombre de GardenSeason. La que no está no tiñe. */\n",
        _mapa("val seasons: Map<String, ColorTint>", estaciones),
        "\n    /** Arriba y abajo del degradado de luz (ARGB), por nombre de LightPhase. */\n",
        _mapa("val light: Map<String, Pair<Long, Long>>", luces),
        "}\n",
    ]
    return "".join(partes)


def kotlin_caras(caras):
    """HabiFaceTable.kt desde caras.json: la tabla de ánimo por personalidad y las marcas."""
    filas = []
    for animo, fila in caras["tabla"].items():
        celdas = "".join(_entrada_mapa(pers, "Triple", [_cadena(v) for v in celda], sangria=20) for pers, celda in fila.items())
        filas.append(" " * 12 + f'"{animo}" to\n' + " " * 16 + "mapOf(\n" + celdas + " " * 16 + "),\n")
    marcas = []
    for pers, lista in caras["marcas"].items():
        valor = _llamada("listOf", [_cadena(m) for m in lista]) if lista else "emptyList()"
        marcas.append(" " * 12 + f'"{pers}" to {valor},\n')
    partes = [
        CABECERA,
        "package com.alvarotc.bito.ui.habi\n\n",
        "/** La tabla de caras aprobada (spec §5.2), desde tools/arte/caras.json. */\n",
        "internal object HabiFaceTable {\n",
        "    /**\n",
        "     * Ojos, boca y cejas por nombre de Mood y de Personality. Son claves de pieza sin el lado\n",
        "     * (-i, -d); sin cejas, null.\n",
        "     */\n",
        _mapa("val faces: Map<String, Map<String, Triple<String, String, String?>>>", filas),
        "\n    /** Las marcas que lleva siempre cada personalidad, por nombre de Personality. */\n",
        _mapa("val marks: Map<String, List<String>>", marcas),
        "}\n",
    ]
    return "".join(partes)


def _pieza_args(d):
    return [f"R.drawable.{d['recurso']}", f"aspect = {d['aspecto']:.4f}f", f"heightRatio = {d['ratio']:.4f}f"]


def _pieza_kt(d):
    return _llamada("GardenPiece", _pieza_args(d))


def _sitio_args(s):
    return [_f(s[0]), _f(s[1]), _f(s[2])]


def _sitio_kt(s):
    return _llamada("GardenSpot", _sitio_args(s))


def kotlin_jardin(datos):
    escena = datos["escena"]
    partes = [
        CABECERA,
        "package com.alvarotc.bito.ui.garden\n\n",
        "import com.alvarotc.bito.R\n\n",
        "/** Recursos y posiciones del jardín. Los sitios van en % de la escena, anclados abajo en el centro. */\n",
        "internal object GardenArt {\n",
        f"    const val SCENE_ASPECT = {datos['aspecto']:.4f}f\n\n",
        "    val background: Int = R.drawable.garden_fondo\n\n",
        "    /** Tierra de cada parcela, en orden de apertura. */\n",
        "    val plots: List<GardenPiece> =\n        listOf(\n",
    ]
    partes += [f"            {_pieza_kt(p)},\n" for p in datos["parcelas"]]
    partes.append("        )\n\n    /** Clave de cada especie de [plants], en orden: debe coincidir con GardenSpecies. */\n")
    partes.append(_lista_cadenas("val plantKeys: List<String>", datos.get("claves_especies", [])))
    partes.append("\n    /** [especie][etapa], en el orden de GardenSpecies y GrowthStage. */\n")
    partes.append("    val plants: List<List<GardenPiece>> =\n        listOf(\n")
    for etapas in datos["especies"]:
        partes.append("            listOf(\n")
        partes += [f"                {_pieza_kt(e)},\n" for e in etapas]
        partes.append("            ),\n")
    partes.append("        )\n\n")
    especiales = [_entrada_mapa(clave, "GardenPiece", _pieza_args(d)) for clave, d in datos["especiales"].items()]
    partes.append(_mapa("val specials: Map<String, GardenPiece>", especiales))
    partes.append(f"\n    val habiSpot = {_sitio_kt(escena['habi'])}\n\n")
    partes.append("    val plotSpots: List<GardenSpot> =\n        listOf(\n")
    partes += [f"            {_sitio_kt(s)},\n" for s in escena["parcelas"]]
    partes.append("        )\n\n")
    sitios = [_entrada_mapa(clave, "GardenSpot", _sitio_args(s)) for clave, s in escena["especiales"].items()]
    partes.append(_mapa("val specialSpots: Map<String, GardenSpot>", sitios))
    partes.append("}\n")
    return "".join(partes)


def claves_jardin(jardin):
    claves = [("fondo", bool(jardin.get("fondo", {}).get("provisional")))] if "fondo" in jardin else []
    claves += [(f"parcela-{i + 1}", bool(p.get("provisional"))) for i, p in enumerate(jardin["parcelas"])]
    for especie in jardin["especies"]:
        claves += [(f"{especie['clave']}-{n + 1}", bool(especie.get("provisional"))) for n in range(len(especie["etapas"]))]
    claves += [(e["clave"], bool(e.get("provisional"))) for e in jardin["especiales"]]
    return claves


def comprobar_definitivo(habi, jardin, estado):
    """Problemas que impiden la exportación definitiva. La sombra y las máscaras (iris, hueco) se derivan
    de piezas aprobadas y no se aprueban aparte."""
    piezas = estado.get("piezas", {})
    problemas = []
    claves = [(p["clave"], bool(p.get("provisional"))) for p in habi["piezas"] if p.get("generada") != "sombra"]
    for clave, provisional in claves + claves_jardin(jardin):
        if provisional:
            problemas.append(f"{clave}: provisional")
        elif piezas.get(clave) != "aprobada":
            problemas.append(f"{clave}: {piezas.get(clave, 'pendiente')}")
    return problemas


def escribir(ruta, texto):
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    with open(ruta, "w", encoding="utf-8") as f:
        f.write(texto)


def main(argv=None):
    p = argparse.ArgumentParser(description="Exporta el arte de Habi y del jardín al proyecto Android.")
    p.add_argument("--habi", default=os.path.join(AQUI, "habi.json"))
    p.add_argument("--jardin", default=os.path.join(AQUI, "jardin.json"))
    p.add_argument("--tintes", default=os.path.join(AQUI, "tintes.json"))
    p.add_argument("--caras", default=os.path.join(AQUI, "caras.json"))
    p.add_argument("--res", default=RES)
    p.add_argument("--calidad", type=int, default=90)
    p.add_argument("--definitivo", action="store_true")
    p.add_argument("--estado", default=None)
    args = p.parse_args(argv)
    habi, jardin, tintes = cargar_json(args.habi), cargar_json(args.jardin), cargar_json(args.tintes)
    caras = cargar_json(args.caras)
    if "jardin" not in tintes:
        print("tintes.json no tiene la sección «jardin» (ver el formato al principio de exportar.py).")
        return 2
    if args.definitivo:
        problemas = comprobar_definitivo(habi, jardin, cargar_json(args.estado) if args.estado else {})
        if problemas:
            print("NO SE EXPORTA. Piezas sin aprobar o provisionales:")
            for problema in problemas:
                print("  " + problema)
            return 6
    os.makedirs(args.res, exist_ok=True)
    for prefijo in PREFIJOS:
        for viejo in glob.glob(os.path.join(args.res, prefijo + "*.webp")):
            os.remove(viejo)
    entradas, lienzo, fuente_px = exportar_habi(habi, args.habi, args.res, args.calidad, caras)
    datos = exportar_jardin(jardin, args.jardin, args.res, args.calidad)
    escribir(os.path.join(KT_HABI, "HabiAtlas.kt"), kotlin_atlas(entradas, lienzo, habi["cuerpo"], fuente_px))
    escribir(os.path.join(KT_HABI, "HabiTintTable.kt"), kotlin_tintes(tintes))
    escribir(os.path.join(KT_HABI, "HabiFaceTable.kt"), kotlin_caras(caras))
    escribir(os.path.join(KT_JARDIN, "GardenArt.kt"), kotlin_jardin(datos))
    escribir(os.path.join(KT_JARDIN, "GardenLookTable.kt"), kotlin_aspecto_jardin(tintes["jardin"]))
    total = sum(os.path.getsize(r) for pre in PREFIJOS for r in glob.glob(os.path.join(args.res, pre + "*.webp")))
    provisionales = [e["clave"] for e in entradas if e["provisional"]] + [c for c, prov in claves_jardin(jardin) if prov]
    print(f"{len(entradas)} piezas de Habi, {total / 1024 / 1024:.2f} MB de WebP")
    if provisionales:
        print(f"{len(provisionales)} provisionales: " + ", ".join(provisionales))
    if total > LIMITE_BYTES:
        print("PASA DE 6 MB: vuelve a exportar con --calidad más baja (spec §3.3).")
        return 5
    return 0


if __name__ == "__main__":
    sys.exit(main())
