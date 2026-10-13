"""Genera la galería de revisión del arte de Habi y del jardín.

Uso, desde la raíz del repo:
  python3 tools/arte/galeria.py [--salida ~/Pictures/bito-habi/galeria]
  python3 tools/arte/galeria.py --sembrar ~/Pictures/bito-habi/estado.json

Evoluciona el prototipo vivo (~/Pictures/bito-habi/vivo/index.html): mismas posiciones, mismos
tintes (los de tintes.json, como filtros CSS en el orden de ColorTint), la cara de los disfraces
desplazada y recortada por su hueco (mask-image) y el jardín con la tierra hundida. Nada de lo que
pinta está copiado a mano: la tabla de caras sale de caras.json y el aspecto del jardín de la
sección `jardin` de tintes.json, los mismos ficheros de los que exportar.py genera el Kotlin.

Cada pieza tiene su estado (pendiente, aprobada, rechazada), guardado en el navegador y exportable
a estado.json ({"piezas": {clave: estado}}), que lee exportar.py --definitivo. La fuente única del
estado es `<salida>/estado.json`; --sembrar lo rellena con lo ya aprobado en el índice humano
(estado.json de ~/Pictures/bito-habi, solo lectura) sin pisar lo que ya se decidiera en él.

Un index.html abierto con file:// no puede leer JSON con fetch ni usar imágenes como máscara de
CSS: por eso los datos y las máscaras de los huecos van incrustados.
"""
import argparse
import base64
import hashlib
import io
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

import exportar  # noqa: E402

AQUI = os.path.dirname(os.path.abspath(__file__))
SALIDA = os.path.expanduser("~/Pictures/bito-habi/galeria")
ESTADOS = ("pendiente", "aprobada", "rechazada")
LADO_MINIATURA = 320
LADO_MASCARA = 1024

CARA_LOTE_A = ("ojo-", "boca-", "pintura-", "mofletes-", "ceja-")

# Qué claves aprueba cada ítem aprobado del índice humano (estado.json): (sección, ítem) -> claves.
# Un ítem aprobado que no esté aquí ni en IGNORADOS se avisa y no siembra nada.
SIEMBRA = {
    ("Habi: base", "Cuerpo sin cara"): ["cuerpo"],
    ("Lote A · Caras", "Tabla personalidad × ánimo (12 caras)"): "caras",
    ("Lote B · Disfraces (temporada)", "Calabaza · Halloween"): ["costume-calabaza"],
    ("Lote B · Disfraces (temporada)", "Reno · Navidad"): ["costume-reno"],
    ("Lote B · Disfraces (temporada)", "Dinosaurio · Carnaval"): ["costume-dinosaurio"],
    ("Accesorios ya hechos (prueba)", "Gorro de lana · Cabeza"): ["head-gorro-lana"],
    ("Accesorios ya hechos (prueba)", "Gafas redondas · Cara"): ["face-gafas-redondas"],
    ("Accesorios ya hechos (prueba)", "Bufanda · Cuello"): ["neck-bufanda"],
    ("Lote C · Accesorios (13)", "Boina · Cabeza"): ["head-boina"],
    ("Lote C · Accesorios (13)", "Sombrero de paja · Cabeza"): ["head-paja"],
    ("Lote C · Accesorios (13)", "Auriculares · Cabeza"): ["head-auriculares"],
    ("Lote C · Accesorios (13)", "Sombrero de copa · Cabeza"): ["head-copa"],
    ("Lote C · Accesorios (13)", "Gafas de sol · Cara"): ["face-gafas-sol"],
    ("Lote C · Accesorios (13)", "Pañuelo · Cuello"): ["neck-panuelo"],
    ("Lote C · Accesorios (13)", "Collar de flores · Cuello"): ["neck-collar-flores"],
    ("Lote C · Accesorios (13)", "Calcetines · Pies"): ["feet-calcetines"],
    ("Lote C · Accesorios (13)", "Botas de agua · Pies"): ["feet-botas-agua"],
    ("Lote C · Accesorios (13)", "Zapatillas · Pies"): ["feet-zapatillas"],
    ("Lote C · Accesorios (13)", "Flor · exclusivo racha 7"): ["head-flor"],
    ("Lote C · Accesorios (13)", "Pañuelo brasa · exclusivo racha 30"): ["neck-panuelo-brasa"],
    ("Lote C · Accesorios (13)", "Corona · exclusivo racha 100"): ["head-corona"],
    ("Jardín: ya hecho (prueba)", "Especie 1 · hojas redondas (5 etapas)"): [f"hojas-redondas-{n}" for n in range(1, 6)],
    ("Jardín: ya hecho (prueba)", "Farolillo · especial «Primera llama»"): ["farolillo"],
    ("Jardín: ya hecho (prueba)", "Setas · especial «Resurrección»"): ["setas"],
    ("Lote D · Jardín (9)", "Fondo limpio, sin parcelas"): ["fondo"],
    ("Lote D · Jardín (9)", "Hoja de parcelas de tierra"): [f"parcela-{n}" for n in range(1, 7)],
    ("Lote D · Jardín (9)", "Especie 2 · girasoles (5 etapas)"): [f"girasoles-{n}" for n in range(1, 6)],
    ("Lote D · Jardín (9)", "Especie 4 · manzano (5 etapas)"): [f"manzano-{n}" for n in range(1, 6)],
    ("Lote D · Jardín (9)", "Especie 5 · helechos (5 etapas)"): [f"helechos-{n}" for n in range(1, 6)],
}
# Aprobados del índice que no son una pieza de la galería (referencias, comprobaciones, logros, vídeo).
IGNORADOS = {
    ("Habi: base", "Habi A (imagen principal)"),
    ("Lote B · Disfraces (temporada)", "Disfraces con caras"),
    ("Logros (14)", "Rachas: Primera llama, Fuego lento, Brasa viva, Vuelta al sol"),
    ("Logros (14)", "Constancia: Día redondo … Mes redondo (6)"),
    ("Logros (14)", "Momentos: Primer paso, Semana entera, Resurrección, Bajo cero"),
    ("Más adelante", "Vídeo de Habi respirando"),
}


def sembrar(origen, claves):
    """Estado por pieza a partir del índice humano. Devuelve ({clave: 'aprobada'}, avisos).

    Solo siembra los ítems aprobados; el resto (revisar, rehacer, por-generar, código, futuro) queda
    pendiente. Una clave de la tabla que no exista en la galería se avisa y se salta.
    """
    claves = list(claves)
    piezas, avisos = {}, []
    for seccion in origen.get("secciones", []):
        for item in seccion.get("items", []):
            if item.get("estado") != "aprobada":
                continue
            id_item = (seccion["titulo"], item["nombre"])
            if id_item in IGNORADOS:
                continue
            destino = SIEMBRA.get(id_item)
            if destino is None:
                avisos.append(f"sin clave para «{seccion['titulo']} / {item['nombre']}»")
                continue
            if destino == "caras":
                destino = [c for c in claves if c.startswith(CARA_LOTE_A)]
            for clave in destino:
                if clave in claves:
                    piezas[clave] = "aprobada"
                else:
                    avisos.append(f"{clave}: no es una pieza de la galería («{item['nombre']}»)")
    return piezas, avisos


def combinar(existente, semilla):
    """Lo ya decidido en la galería manda; la semilla solo rellena lo que sigue pendiente o sin tocar."""
    out = dict(existente)
    for clave, estado in semilla.items():
        if out.get(clave, "pendiente") == "pendiente":
            out[clave] = estado
    return out


def resumen(claves, piezas):
    cuenta = {e: 0 for e in ESTADOS}
    for clave in claves:
        cuenta[piezas.get(clave, "pendiente")] += 1
    return cuenta


def _uri(ruta):
    return Path(os.path.abspath(ruta)).as_uri()


def _png_datos(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")


def _miniatura(img, caja, destino):
    mini = img.crop(caja)
    mini.thumbnail((LADO_MINIATURA, LADO_MINIATURA), Image.LANCZOS)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    mini.save(destino)


def _mascara_hueco(original, cuerpo, e):
    """El hueco de cara del disfraz como imagen con alfa (misma forma que el que exporta exportar.py)."""
    area_max = exportar.HUECO_MAX * cuerpo["ancho"] * cuerpo["alto"] / (e * e)
    alfa = exportar.alfa_hueco(original, area_max)
    if alfa is None:
        return None
    mascara = Image.new("RGBA", (alfa.shape[1], alfa.shape[0]), (255, 255, 255, 0))
    mascara.putalpha(Image.fromarray(alfa, "L"))
    mascara.thumbnail((LADO_MASCARA, LADO_MASCARA), Image.LANCZOS)
    return _png_datos(mascara)


def _pieza_habi(habi, ruta_habi, caras, pieza, salida):
    clave = pieza["clave"]
    ruta = exportar.ruta_fuente(habi, ruta_habi, pieza["archivo"])
    img = Image.open(ruta).convert("RGBA")
    caja = exportar.caja_alfa(img)
    if caja is None:
        raise SystemExit(f"{clave}: la pieza está vacía")
    sitio, e = exportar._posicion(pieza, caras)
    if "centro" in sitio:
        x0, y0 = sitio["centro"][0] - img.width * e / 2, sitio["centro"][1] - img.height * e / 2
    else:
        x0, y0 = sitio["origen"]
    datos = {
        "src": _uri(ruta),
        "rect": [x0, y0, img.width * e, img.height * e],
        "alfa": list(exportar._rect(x0, y0, e, caja)),
        "mini": "min/" + clave + ".png",
        "provisional": bool(pieza.get("provisional")),
    }
    _miniatura(img, caja, os.path.join(salida, datos["mini"]))
    if pieza.get("iris"):
        mascara, hay = exportar.mascara_iris(img)
        if hay:
            os.makedirs(os.path.join(salida, "iris"), exist_ok=True)
            mascara.save(os.path.join(salida, "iris", clave + ".png"))
            datos["iris"] = "iris/" + clave + ".png"
    if clave.startswith("costume-"):
        if "cara" in pieza:
            datos["cara"] = [float(v) for v in pieza["cara"]]
        hueco = _mascara_hueco(img, habi["cuerpo"], e)
        if hueco:
            datos["hueco"] = hueco
            datos.setdefault("cara", [0.0, 0.0])
    return datos


def _alto_util(ruta):
    caja = exportar.caja_alfa(Image.open(ruta).convert("RGBA"))
    return (caja[3] - caja[1]) if caja else 1


def _lienzo(habi, piezas):
    """Lienzo simétrico respecto al centro del cuerpo, como el de exportar.py."""
    cajas = [p["alfa"] for p in piezas.values()]
    top = min(c[1] for c in cajas)
    abajo = max(c[1] + c[3] for c in cajas)
    ancho = habi["cuerpo"]["ancho"]
    margen = max(0.0, -min(c[0] for c in cajas), max(c[0] + c[2] for c in cajas) - ancho)
    return [-margen, top, ancho + 2 * margen, abajo - top]


def generar(habi, ruta_habi, jardin, ruta_jardin, tintes, salida, caras=None, semilla=None):
    """Escribe `<salida>/index.html` (con los manifiestos incrustados) y sus miniaturas; devuelve su ruta.

    `caras`: contenido de caras.json (tabla de caras y posiciones de las piezas de cara).
    `semilla`: {clave: estado} con el que arranca la galería si el navegador aún no guarda nada.
    """
    os.makedirs(salida, exist_ok=True)
    piezas = {}
    for pieza in habi["piezas"]:
        if pieza.get("generada") == "sombra":
            caja = [float(v) for v in pieza["caja"]]
            piezas["sombra"] = {"sombra": True, "rect": caja, "alfa": caja}
        else:
            piezas[pieza["clave"]] = _pieza_habi(habi, ruta_habi, caras, pieza, salida)
    lienzo = _lienzo(habi, piezas)

    def fuente(archivo):
        return exportar.ruta_fuente(jardin, ruta_jardin, archivo)

    srcs, minis = {}, {}

    def registrar(clave, ruta):
        srcs[clave] = _uri(ruta)
        img = Image.open(ruta).convert("RGBA")
        caja = exportar.caja_alfa(img) or (0, 0, img.width, img.height)
        minis[clave] = "min/" + clave + ".png"
        _miniatura(img, caja, os.path.join(salida, minis[clave]))

    ruta_fondo = fuente(jardin["fondo"]["archivo"])
    registrar("fondo", ruta_fondo)
    for i, p in enumerate(jardin["parcelas"]):
        registrar(f"parcela-{i + 1}", fuente(p["archivo"]))
    especies = []
    for especie in jardin["especies"]:
        rutas = [fuente(a) for a in especie["etapas"]]
        arbol = _alto_util(rutas[-1])
        for n, r in enumerate(rutas):
            registrar(f"{especie['clave']}-{n + 1}", r)
        especies.append({
            "clave": especie["clave"],
            "etapas": [{"src": _uri(r), "ratio": _alto_util(r) / arbol} for r in rutas],
        })
    for e in jardin["especiales"]:
        registrar(e["clave"], fuente(e["archivo"]))
    with Image.open(ruta_fondo) as f:
        aspecto = f.width / f.height

    claves_habi = [p["clave"] for p in habi["piezas"] if p.get("generada") != "sombra"]
    claves = claves_habi + [c for c, _ in exportar.claves_jardin(jardin)]
    provisionales = [p["clave"] for p in habi["piezas"] if p.get("provisional")]
    provisionales += [c for c, prov in exportar.claves_jardin(jardin) if prov]
    semilla = {c: e for c, e in (semilla or {}).items() if c in claves and e in ESTADOS}
    for clave in claves_habi:
        minis[clave] = piezas[clave]["mini"]
    datos = {
        "cuerpo": habi["cuerpo"],
        "lienzo": lienzo,
        "piezas": piezas,
        "caras": {"tabla": (caras or {}).get("tabla", {}), "marcas": (caras or {}).get("marcas", {})},
        "tintes": tintes,
        "jardin": {
            "especies": especies,
            "parcelas": [srcs[f"parcela-{i + 1}"] for i in range(len(jardin["parcelas"]))],
            "alto_parcela": jardin["alto_parcela"],
            "especiales": {e["clave"]: srcs[e["clave"]] for e in jardin["especiales"]},
            "fondo": srcs["fondo"],
            "aspecto": aspecto,
            "escena": jardin["escena"],
        },
        "claves": claves,
        "provisionales": provisionales,
        "minis": minis,
        "semilla": semilla,
        "id_semilla": hashlib.sha1(json.dumps(semilla, sort_keys=True).encode()).hexdigest()[:10],
    }
    ruta = os.path.join(salida, "index.html")
    texto = json.dumps(datos, ensure_ascii=False).replace("</", "<\\/")
    with open(ruta, "w", encoding="utf-8") as f:
        f.write(PLANTILLA.replace("/*DATOS*/null", texto))
    return ruta


PLANTILLA = r"""<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Galería de Habi</title>
<style>
  :root { --bg:#efe8dc; --ink:#2b2724; --muted:#7a716a; --card:#fbf7f1; --line:#d8cfc2; --ok:#4f8a4b; --ko:#b5523b; --vista:#fbf7f1; }
  body[data-fondo="oscuro"] { --vista:#1d1b1a; }
  body[data-fondo="damero"] { --vista:conic-gradient(#d9d3c9 25%, #fbf7f1 0 50%, #d9d3c9 0 75%, #fbf7f1 0) 0 0/16px 16px; }
  * { box-sizing:border-box; }
  body { margin:0; background:var(--bg); color:var(--ink); font:14px/1.4 system-ui, sans-serif; padding:16px; }
  h1 { font-size:20px; margin:0 0 4px; } h2 { font-size:16px; margin:28px 0 8px; }
  .bar { position:sticky; top:0; background:var(--bg); padding:8px 0; display:flex; gap:8px; flex-wrap:wrap; align-items:center; z-index:50; }
  button { font:inherit; border:1.5px solid var(--ink); background:transparent; color:var(--ink); border-radius:999px; padding:4px 10px; cursor:pointer; }
  button.on { background:var(--ink); color:var(--card); }
  .grid { display:flex; flex-wrap:wrap; gap:10px; }
  .cell { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:8px; text-align:center; font-size:12px; }
  .cell.aprobada { border:2px solid var(--ok); } .cell.rechazada { border:2px solid var(--ko); }
  .vista { background:var(--vista); border-radius:8px; margin:0 auto 4px; display:flex; align-items:center; justify-content:center; }
  .rig { position:relative; overflow:hidden; margin:0 auto; }
  .rig img, .rig .sombra, .rig .cara { position:absolute; }
  .rig .cara { left:0; top:0; width:100%; height:100%; -webkit-mask-repeat:no-repeat; mask-repeat:no-repeat; }
  .sombra { border-radius:50%; background:radial-gradient(closest-side, rgba(60,40,20,.32), rgba(60,40,20,0)); }
  .scene { position:relative; width:360px; border-radius:16px; overflow:hidden; }
  .scene > img.bg { width:100%; display:block; }
  .scene img.item { position:absolute; transform:translateX(-50%); transform-origin:50% 100%; }
  .light { position:absolute; inset:0; mix-blend-mode:multiply; pointer-events:none; }
  .glow { position:absolute; inset:0; mix-blend-mode:screen; pointer-events:none; }
  .estado button { padding:2px 8px; font-size:11px; margin:2px; }
  .estado button.on { background:var(--ink); }
  .prov { color:var(--ko); font-weight:600; }
</style>
</head>
<body data-fondo="claro">
<h1>Galería de Habi</h1>
<p>Cada pieza está <b>pendiente</b>, <b>aprobada</b> o <b>rechazada</b>. El estado se guarda en este navegador; «Exportar estado» descarga <code>estado.json</code>, que va en <code>galeria/estado.json</code> para <code>exportar.py --definitivo</code>.</p>
<div class="bar">
  <button id="exportar">Exportar estado</button>
  <label><input type="file" id="importar" accept=".json"> Importar estado</label>
  <span>Fondo:</span><span id="fondos"></span>
  <span id="resumen"></span>
</div>
<div id="app"></div>
<script>
const D = /*DATOS*/null;
const CLAVE_ESTADO = 'bito-galeria-estado';
const FONDOS = ['claro', 'oscuro', 'damero'];
let estado = Object.assign({}, D.semilla);
try {
  const guardado = JSON.parse(localStorage.getItem(CLAVE_ESTADO) || 'null');
  if (guardado && guardado.semilla === D.id_semilla) estado = guardado.piezas || estado;
} catch (e) {}
const guardar = () => { try { localStorage.setItem(CLAVE_ESTADO, JSON.stringify({semilla: D.id_semilla, piezas: estado})); } catch (e) {} pintarResumen(); };
const estadoDe = k => estado[k] || 'pendiente';

// Mismas fórmulas y mismo orden que ColorTint: hue-rotate, saturate, sepia, brightness.
const filtro = t => !t ? '' : `hue-rotate(${t.hue || 0}deg) saturate(${t.saturacion ?? 1}) sepia(${t.sepia || 0}) brightness(${t.brillo ?? 1})`;
const unir = (...fs) => fs.filter(Boolean).join(' ') || 'none';
const J = D.tintes.jardin || {};

// La tabla de caras es la de caras.json: ánimo -> personalidad -> [ojos, boca, cejas].
const ANIMOS = Object.keys(D.caras.tabla);
const PERSONALIDADES = Object.keys(D.caras.marcas);
// Por nombre, no por posición en caras.json.
const ANIMO_NORMAL = 'NORMAL', PERSONALIDAD_BASE = 'NEUTRA';
const lado = (clave, s) => `${clave}-${s}`;

// Como habiLayers: [clave, tinte, esIris] en orden de pintado. Con disfraz no hay accesorios ni color de cuerpo.
function capas({pers, animo, cuerpo = null, ojos = null, head = null, face = null, neck = null, feet = null, costume = null, cerrados = false, feliz = false}) {
  const fila = (D.caras.tabla[animo] || {})[pers];
  let [o, b, c] = fila || [null, null, null];
  if (feliz) { o = 'ojo-feliz'; b = 'boca-feliz'; }
  if (cerrados) { o = 'ojo-cerrado'; c = null; }
  const tOjos = ojos ? D.tintes.ojos[ojos] : null;
  const out = [['sombra']];
  if (costume) out.push([costume]);
  else {
    out.push(['cuerpo', cuerpo === 'apagado' ? D.tintes.apagado : (cuerpo ? D.tintes.cuerpo[cuerpo] : null)]);
    if (feet) out.push([feet]);
    if (neck) out.push([neck]);
  }
  out.push(['#cara']);
  const cara = [];
  if (o) for (const s of ['i', 'd']) { cara.push([lado(o, s)]); if (tOjos) cara.push([lado(o, s), tOjos, true]); }
  if (b) cara.push([b]);
  (D.caras.marcas[pers] || []).forEach(m => cara.push([m]));
  if (c) for (const s of ['i', 'd']) cara.push([lado(c, s)]);
  out.push(...cara.map(x => ['>' + x[0], x[1], x[2]]));
  if (!costume) { if (face) out.push([face]); if (head) out.push([head]); }
  return out;
}

function habi(opciones, alto) {
  const [l, t, w, h] = D.lienzo, k = alto / h;
  const rig = document.createElement('div'); rig.className = 'rig';
  rig.style.width = (w * k) + 'px'; rig.style.height = alto + 'px';
  const costume = opciones.costume ? D.piezas[opciones.costume] : null;
  let grupo = null;
  const poner = (padre, clave, tinte, iris, dx, dy) => {
    const p = D.piezas[clave]; if (!p) return;
    const [x, y, pw, ph] = p.rect;
    const el = p.sombra ? Object.assign(document.createElement('div'), {className: 'sombra'}) : document.createElement('img');
    if (!p.sombra) { if (iris && !p.iris) return; el.src = iris ? p.iris : p.src; }
    Object.assign(el.style, {left: ((x + dx - l) * k) + 'px', top: ((y + dy - t) * k) + 'px', width: (pw * k) + 'px', height: (ph * k) + 'px', filter: filtro(tinte) || 'none'});
    padre.appendChild(el);
  };
  for (const [clave, tinte, iris] of capas(opciones)) {
    if (clave === '#cara') {
      grupo = document.createElement('div'); grupo.className = 'cara'; rig.appendChild(grupo);
      if (costume && costume.hueco) {
        // La cara solo se ve dentro del hueco del disfraz; la máscara no se mueve con la cara.
        const [x, y, pw, ph] = costume.rect, m = `url(${costume.hueco})`;
        Object.assign(grupo.style, {webkitMaskImage: m, maskImage: m, webkitMaskSize: `${pw * k}px ${ph * k}px`, maskSize: `${pw * k}px ${ph * k}px`,
          webkitMaskPosition: `${(x - l) * k}px ${(y - t) * k}px`, maskPosition: `${(x - l) * k}px ${(y - t) * k}px`});
      }
    } else if (clave.startsWith('>')) {
      const [dx, dy] = costume && costume.cara ? costume.cara : [0, 0];
      poner(grupo, clave.slice(1), tinte, iris, dx, dy);
    } else poner(rig, clave, tinte, iris, 0, 0);
  }
  return rig;
}

function vista(nodo) { const v = document.createElement('div'); v.className = 'vista'; v.appendChild(nodo); return v; }

function celda(titulo, nodo, clave, conVista = true) {
  const c = document.createElement('div'); c.className = 'cell';
  c.appendChild(conVista ? vista(nodo) : nodo);
  const t = document.createElement('div'); t.textContent = titulo; c.appendChild(t);
  if (clave) {
    if (D.provisionales.includes(clave)) { const p = document.createElement('div'); p.className = 'prov'; p.textContent = 'provisional'; c.appendChild(p); }
    const e = document.createElement('div'); e.className = 'estado';
    const pintar = () => { c.className = 'cell ' + estadoDe(clave); e.querySelectorAll('button').forEach(b => b.classList.toggle('on', b.textContent === estadoDe(clave))); };
    for (const v of ['pendiente', 'aprobada', 'rechazada']) {
      const b = document.createElement('button'); b.textContent = v;
      b.onclick = () => { estado[clave] = v; pintar(); guardar(); };
      e.appendChild(b);
    }
    c.appendChild(e); pintar();
  }
  return c;
}

function seccion(titulo, celdas) {
  const app = document.getElementById('app');
  const h = document.createElement('h2'); h.textContent = titulo; app.appendChild(h);
  const g = document.createElement('div'); g.className = 'grid'; celdas.forEach(x => g.appendChild(x)); app.appendChild(g);
}

const SOLO_APROBADOS = ['farolillo', 'setas'];
function escena({etapa = 5, abiertas = 6, mustio = 0, luz = 'DIA', estacion = null, especiales = SOLO_APROBADOS}) {
  const E = D.jardin.escena, mp = J.mustio_planta, mc = J.mustio_cesped;
  const s = document.createElement('div'); s.className = 'scene'; s.style.aspectRatio = D.jardin.aspecto;
  const bg = Object.assign(document.createElement('img'), {className: 'bg', src: D.jardin.fondo});
  const tEstacion = filtro((J.estaciones || {})[estacion]);
  const cesped = mustio && mc ? `saturate(${1 - mc.saturacion * mustio}) sepia(${mc.sepia * mustio}) brightness(${1 - mc.brillo * mustio})` : '';
  bg.style.filter = unir(cesped, tEstacion);
  s.appendChild(bg);
  const hundido = J.hundido_tierra ?? 0, items = [];
  E.parcelas.slice(0, abiertas).forEach(([x, y, alto], i) => {
    const e = i < abiertas - 1 ? 5 : etapa, sp = D.jardin.especies[i].etapas[e - 1], tierra = alto * D.jardin.alto_parcela;
    // La tierra se hunde una fracción de su altura bajo el pie de la planta (SOIL_SINK).
    items.push({src: D.jardin.parcelas[i], x, y: y + tierra * hundido, alto: tierra, planta: false, orden: y - 0.5});
    items.push({src: sp.src, x, y, alto: alto * sp.ratio, planta: true, orden: y});
  });
  especiales.forEach(k => { const p = E.especiales[k]; if (p) items.push({src: D.jardin.especiales[k], x: p[0], y: p[1], alto: p[2], planta: false, orden: p[1]}); });
  items.sort((a, b) => a.orden - b.orden).forEach(it => {
    const img = Object.assign(document.createElement('img'), {className: 'item', src: it.src});
    Object.assign(img.style, {left: it.x + '%', bottom: (100 - it.y) + '%', height: it.alto + '%', zIndex: it.orden > E.habi[1] ? 3 : 1});
    if (it.planta && (mustio || tEstacion)) {
      const m = mustio && mp ? `saturate(${1 - mp.saturacion * mustio}) sepia(${mp.sepia * mustio}) brightness(${1 - mp.brillo * mustio})` : '';
      img.style.filter = unir(m, tEstacion);
      if (mustio && mp) img.style.transform = `translateX(-50%) scaleY(${1 - mp.encoge * mustio}) skewX(${-mp.inclina * mustio}deg)`;
    }
    s.appendChild(img);
  });
  const hb = E.habi, base = {pers: PERSONALIDAD_BASE, animo: ANIMO_NORMAL}, previo = habi(base, 1);
  s.appendChild(previo);
  requestAnimationFrame(() => {
    // El pie del cuerpo (centro, abajo) va en (x %, y %); su alto es hb[2] % de la escena.
    const alto = s.clientHeight * hb[2] / 100 * D.lienzo[3] / D.cuerpo.alto, k = alto / D.lienzo[3];
    const rig = habi(base, alto);
    Object.assign(rig.style, {position: 'absolute', zIndex: 2, left: `calc(${hb[0]}% + ${(D.lienzo[0] - D.cuerpo.ancho / 2) * k}px)`, top: `calc(${hb[1]}% + ${(D.lienzo[1] - D.cuerpo.alto) * k}px)`});
    s.replaceChild(rig, previo);
  });
  const colores = (J.luz || {})[luz] || ['#FFFFFF', '#FFFFFF'];
  s.appendChild(Object.assign(document.createElement('div'), {className: 'light', style: `background:linear-gradient(180deg,${colores[0]},${colores[1]});z-index:4`}));
  const f = E.especiales.farolillo;
  if (luz === 'NOCHE' && especiales.includes('farolillo') && f) {
    s.appendChild(Object.assign(document.createElement('div'), {className: 'glow', style: `z-index:5;background:radial-gradient(ellipse 22% 30% at ${f[0]}% ${f[1] - f[2] * .75}%, rgba(255,190,110,.75), rgba(255,160,80,.22) 45%, rgba(0,0,0,0) 100%)`}));
  }
  return s;
}

function pintarResumen() {
  const n = v => D.claves.filter(k => estadoDe(k) === v).length;
  document.getElementById('resumen').textContent = `${n('aprobada')} aprobadas · ${n('rechazada')} rechazadas · ${n('pendiente')} pendientes de ${D.claves.length}`;
}

document.getElementById('exportar').onclick = () => {
  const piezas = {}; D.claves.forEach(k => { piezas[k] = estadoDe(k); });
  const blob = new Blob([JSON.stringify({piezas}, null, 2)], {type: 'application/json'});
  Object.assign(document.createElement('a'), {href: URL.createObjectURL(blob), download: 'estado.json'}).click();
};
document.getElementById('importar').onchange = e => {
  const r = new FileReader(); r.onload = () => { estado = JSON.parse(r.result).piezas || {}; guardar(); location.reload(); }; r.readAsText(e.target.files[0]);
};
(function selectorFondo() {
  const cont = document.getElementById('fondos');
  let actual = 'claro'; try { actual = localStorage.getItem('bito-galeria-fondo') || 'claro'; } catch (e) {}
  const poner = f => { document.body.dataset.fondo = f; cont.querySelectorAll('button').forEach(b => b.classList.toggle('on', b.textContent === f)); try { localStorage.setItem('bito-galeria-fondo', f); } catch (e) {} };
  FONDOS.forEach(f => { const b = document.createElement('button'); b.textContent = f; b.onclick = () => poner(f); cont.appendChild(b); });
  poner(FONDOS.includes(actual) ? actual : 'claro');
})();

const ALTO = 150;
const ACC = Object.keys(D.piezas).filter(k => /^(head|face|neck|feet)-/.test(k));
const ejeDe = k => k.split('-')[0];
const normal = ANIMO_NORMAL;
seccion('Personalidad × ánimo', PERSONALIDADES.flatMap(p => ANIMOS.map(a => celda(`${p.toLowerCase()} · ${a.toLowerCase()}`, habi({pers: p, animo: a}, ALTO)))));
seccion('Ojos cerrados y alegría', PERSONALIDADES.flatMap(p => [celda(`${p.toLowerCase()} · cerrados`, habi({pers: p, animo: normal, cerrados: true}, ALTO)), celda(`${p.toLowerCase()} · alegría`, habi({pers: p, animo: normal, feliz: true}, ALTO))]));
seccion('Accesorios con cada personalidad', ACC.flatMap(k => PERSONALIDADES.map(p => celda(`${k} · ${p.toLowerCase()}`, habi({pers: p, animo: normal, [ejeDe(k)]: k}, ALTO)))));
seccion('Disfraces con cada personalidad (la cara, recortada por el hueco)', Object.keys(D.piezas).filter(k => k.startsWith('costume-')).flatMap(k => PERSONALIDADES.map(p => celda(`${k} · ${p.toLowerCase()}`, habi({pers: p, animo: normal, costume: k}, ALTO)))));
seccion('Colores de cuerpo (y el apagado del onboarding)', ['', ...Object.keys(D.tintes.cuerpo), 'apagado'].map(k => celda(k || 'salvia', habi({pers: PERSONALIDAD_BASE, animo: normal, cuerpo: k || null}, ALTO))));
seccion('Colores de ojos', ['', ...Object.keys(D.tintes.ojos)].map(k => celda(k || 'tinta', habi({pers: PERSONALIDAD_BASE, animo: normal, ojos: k || null}, ALTO))));
seccion('Jardín · etapas', [1, 2, 3, 4, 5].map(e => celda(`etapa ${e}`, escena({etapa: e, abiertas: 1, especiales: []}), null, false)));
seccion('Jardín · mustio', [0, 1, 2, 3].map(m => celda(`mustio ${m}`, escena({mustio: m}), null, false)));
seccion('Jardín · luz', Object.keys(J.luz || {DIA: 1}).map(l => celda(l.toLowerCase(), escena({luz: l}), null, false)));
seccion('Jardín · estaciones', [['verano', null], ...Object.keys(J.estaciones || {}).map(e => [e.toLowerCase(), e])].map(([n, e]) => celda(n, escena({estacion: e}), null, false)));
seccion('Jardín · todos los especiales', [celda('especiales', escena({especiales: Object.keys(D.jardin.especiales)}), null, false)]);
seccion('Piezas para aprobar', D.claves.map(k => celda(k, Object.assign(document.createElement('img'), {src: D.minis[k]}), k)));
pintarResumen();
</script>
</body>
</html>
"""


def main(argv=None):
    p = argparse.ArgumentParser(description="Genera la galería de revisión del arte.")
    p.add_argument("--habi", default=os.path.join(AQUI, "habi.json"))
    p.add_argument("--jardin", default=os.path.join(AQUI, "jardin.json"))
    p.add_argument("--tintes", default=os.path.join(AQUI, "tintes.json"))
    p.add_argument("--caras", default=os.path.join(AQUI, "caras.json"))
    p.add_argument("--salida", default=SALIDA)
    p.add_argument("--sembrar", default=None, help="estado.json del índice humano (solo lectura) con lo ya aprobado")
    args = p.parse_args(argv)
    salida = os.path.expanduser(args.salida)
    habi, jardin = exportar.cargar_json(args.habi), exportar.cargar_json(args.jardin)
    claves = [pz["clave"] for pz in habi["piezas"] if pz.get("generada") != "sombra"] + [c for c, _ in exportar.claves_jardin(jardin)]
    ruta_estado = os.path.join(salida, "estado.json")
    piezas = exportar.cargar_json(ruta_estado).get("piezas", {}) if os.path.exists(ruta_estado) else {}
    if args.sembrar:
        semilla, avisos = sembrar(exportar.cargar_json(args.sembrar), claves)
        for aviso in avisos:
            print("AVISO", aviso)
        piezas = combinar(piezas, semilla)
    piezas = {c: piezas[c] for c in claves if piezas.get(c) in ESTADOS}
    ruta = generar(
        habi,
        args.habi,
        jardin,
        args.jardin,
        exportar.cargar_json(args.tintes),
        salida,
        caras=exportar.cargar_json(args.caras),
        semilla=piezas,
    )
    if args.sembrar:
        completo = {c: piezas.get(c, "pendiente") for c in claves}
        with open(ruta_estado, "w", encoding="utf-8") as f:
            json.dump({"piezas": completo}, f, ensure_ascii=False, indent=2)
        print("estado:", ruta_estado)
    cuenta = resumen(claves, piezas)
    print(f"{cuenta['aprobada']} aprobadas, {cuenta['rechazada']} rechazadas, {cuenta['pendiente']} pendientes de {len(claves)}")
    print("galería:", ruta)
    return 0


if __name__ == "__main__":
    sys.exit(main())
