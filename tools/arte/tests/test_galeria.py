import json
import os
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import exportar  # noqa: E402
import galeria  # noqa: E402

SALVIA = (150, 185, 140, 255)
TRAJE = (130, 80, 40, 255)


def datos(html):
    """El objeto D incrustado en el index.html."""
    inicio = html.index("const D = ") + len("const D = ")
    fin = html.index(";\nconst CLAVE_ESTADO")
    return json.loads(html[inicio:fin].replace("<\\/", "</"))


def montar(con_disfraz=False, con_caras=False):
    d = tempfile.mkdtemp()
    cuerpo = np.zeros((120, 100, 4), np.uint8)
    cuerpo[...] = (60, 170, 70, 255)
    Image.fromarray(cuerpo, "RGBA").save(os.path.join(d, "cuerpo.png"))
    ojo = np.zeros((10, 10, 4), np.uint8)
    ojo[2:8, 2:8] = (120, 70, 40, 255)
    Image.fromarray(ojo, "RGBA").save(os.path.join(d, "ojo.png"))
    Image.new("RGB", (40, 30), (120, 160, 90)).save(os.path.join(d, "fondo.jpg"))
    habi = {
        "fuentes": d,
        "cuerpo": {"ancho": 100, "alto": 120},
        "alto_salida": 60,
        "piezas": [
            {"clave": "sombra", "generada": "sombra", "caja": [-5, 110, 110, 20]},
            {"clave": "cuerpo", "archivo": "cuerpo.png", "origen": [0, 0]},
            {"clave": "ojo-abierto-i", "archivo": "ojo.png", "centro": [30, 40], "escala": 2, "iris": True},
        ],
    }
    if con_disfraz:
        traje = np.zeros((120, 100, 4), np.uint8)
        traje[...] = TRAJE
        yy, xx = np.mgrid[0:120, 0:100]
        traje[(xx - 50) ** 2 + (yy - 40) ** 2 <= 100] = SALVIA
        Image.fromarray(traje, "RGBA").save(os.path.join(d, "traje.png"))
        habi["piezas"].append({"clave": "costume-reno", "archivo": "traje.png", "origen": [0, 0], "cara": [-19, -71]})
    jardin = {
        "fuentes": d,
        "fondo": {"archivo": "fondo.jpg"},
        "ancho_fondo": 40,
        "escala_piezas": 1.0,
        "alto_parcela": 0.16,
        "parcelas": [{"archivo": "ojo.png"}],
        "especies": [{"clave": "hojas-redondas", "etapas": ["ojo.png", "cuerpo.png"]}],
        "especiales": [{"clave": "farolillo", "archivo": "ojo.png"}],
        "escena": {"habi": [50, 93, 40], "parcelas": [[9, 64, 34.2]], "especiales": {"farolillo": [36, 58, 22]}},
    }
    tintes = {
        "cuerpo": {"body-cielo": {"hue": 75, "saturacion": 1.2}},
        "ojos": {},
        "apagado": {"saturacion": 0.55},
        "jardin": {"hundido_tierra": 0.35, "luz": {"NOCHE": ["#31406E", "#4B5A8A"]}},
    }
    caras = None
    if con_caras:
        caras = {
            "tabla": {"NORMAL": {"NEUTRA": ["ojo-abierto", "boca-sonrisa", None]}},
            "marcas": {"NEUTRA": []},
            "piezas": {},
        }
    return d, habi, jardin, tintes, caras


def generar(d, habi, jardin, tintes, caras=None, semilla=None):
    salida = os.path.join(d, "galeria")
    ruta = galeria.generar(
        habi, os.path.join(d, "habi.json"), jardin, os.path.join(d, "jardin.json"), tintes, salida, caras=caras, semilla=semilla
    )
    return salida, ruta


class GaleriaTest(unittest.TestCase):
    def test_genera_un_html_con_cada_pieza_y_la_mascara_de_iris(self):
        d, habi, jardin, tintes, _ = montar()
        salida, ruta = generar(d, habi, jardin, tintes)

        html = open(ruta, encoding="utf-8").read()
        for clave in ["cuerpo", "ojo-abierto-i", "fondo", "parcela-1", "hojas-redondas-2", "farolillo", "body-cielo"]:
            self.assertIn(clave, html)
        self.assertIn("bito-galeria-estado", html)
        self.assertIn("hue-rotate", html)
        self.assertTrue(os.path.exists(os.path.join(salida, "iris", "ojo-abierto-i.png")))

    def test_el_selector_de_fondo_ofrece_claro_oscuro_y_damero(self):
        d, habi, jardin, tintes, _ = montar()
        html = open(generar(d, habi, jardin, tintes)[1], encoding="utf-8").read()
        self.assertIn("['claro', 'oscuro', 'damero']", html)
        self.assertIn('data-fondo="oscuro"', html)
        self.assertIn('data-fondo="damero"', html)

    def test_el_disfraz_lleva_el_desplazamiento_de_cara_y_su_hueco_incrustado(self):
        d, habi, jardin, tintes, _ = montar(con_disfraz=True)
        _, ruta = generar(d, habi, jardin, tintes)
        pieza = datos(open(ruta, encoding="utf-8").read())["piezas"]["costume-reno"]
        self.assertEqual(pieza["cara"], [-19.0, -71.0])
        # file:// no puede usar una imagen como mask-image: va incrustada.
        self.assertTrue(pieza["hueco"].startswith("data:image/png;base64,"))

    def test_las_caras_y_el_aspecto_del_jardin_salen_de_los_manifiestos(self):
        d, habi, jardin, tintes, caras = montar(con_caras=True)
        _, ruta = generar(d, habi, jardin, tintes, caras=caras)
        html = open(ruta, encoding="utf-8").read()
        incrustado = datos(html)
        self.assertEqual(incrustado["caras"]["tabla"]["NORMAL"]["NEUTRA"], ["ojo-abierto", "boca-sonrisa", None])
        self.assertEqual(incrustado["tintes"]["jardin"]["hundido_tierra"], 0.35)
        # Ni la tabla de caras ni el aspecto del jardín están copiados en el JS.
        for literal in ["ojo-lloroso", "ceja-preocupada", "#31406E", "0.35", "'otono'"]:
            self.assertNotIn(literal, html.split("const D = ")[0] + html.split(";\nconst CLAVE_ESTADO")[1])

    def test_la_semilla_arranca_el_estado_de_las_piezas_que_existen(self):
        d, habi, jardin, tintes, _ = montar()
        _, ruta = generar(d, habi, jardin, tintes, semilla={"cuerpo": "aprobada", "no-existe": "aprobada", "fondo": "rara"})
        incrustado = datos(open(ruta, encoding="utf-8").read())
        self.assertEqual(incrustado["semilla"], {"cuerpo": "aprobada"})

    def test_todas_las_piezas_tienen_miniatura(self):
        d, habi, jardin, tintes, _ = montar()
        salida, ruta = generar(d, habi, jardin, tintes)
        incrustado = datos(open(ruta, encoding="utf-8").read())
        for clave in incrustado["claves"]:
            self.assertTrue(os.path.exists(os.path.join(salida, incrustado["minis"][clave])), clave)


INDICE = {
    "secciones": [
        {"titulo": "Habi: base", "items": [
            {"nombre": "Habi A (imagen principal)", "estado": "aprobada"},
            {"nombre": "Cuerpo sin cara", "estado": "aprobada"},
        ]},
        {"titulo": "Lote A · Caras", "items": [{"nombre": "Tabla personalidad × ánimo (12 caras)", "estado": "aprobada"}]},
        {"titulo": "Lote C · Accesorios (13)", "items": [
            {"nombre": "Boina · Cabeza", "estado": "aprobada"},
            {"nombre": "Sombrero de paja · Cabeza", "estado": "revisar"},
        ]},
        {"titulo": "Lote D · Jardín (9)", "items": [
            {"nombre": "Hoja de parcelas de tierra", "estado": "aprobada"},
            {"nombre": "Especie 3 · lavanda (5 etapas)", "estado": "por-generar"},
        ]},
        {"titulo": "Sección nueva", "items": [{"nombre": "Algo", "estado": "aprobada"}]},
    ]
}


class SembrarTest(unittest.TestCase):
    CLAVES = ["cuerpo", "ojo-abierto-i", "boca-feliz", "ceja-dura-d", "head-boina", "head-paja", "costume-reno"] + [
        f"parcela-{n}" for n in range(1, 7)
    ]

    def test_aprueba_solo_los_items_aprobados_con_sus_claves(self):
        piezas, avisos = galeria.sembrar(INDICE, self.CLAVES)
        self.assertEqual(piezas["cuerpo"], "aprobada")
        self.assertEqual(piezas["head-boina"], "aprobada")
        for clave in ["ojo-abierto-i", "boca-feliz", "ceja-dura-d"]:
            self.assertEqual(piezas[clave], "aprobada", clave)
        self.assertEqual([piezas[f"parcela-{n}"] for n in range(1, 7)], ["aprobada"] * 6)
        # revisar no aprueba, y una pieza sin item tampoco.
        self.assertNotIn("head-paja", piezas)
        self.assertNotIn("costume-reno", piezas)

    def test_avisa_de_lo_aprobado_que_no_sabe_donde_poner(self):
        _, avisos = galeria.sembrar(INDICE, self.CLAVES)
        self.assertEqual(len(avisos), 1)
        self.assertIn("Sección nueva", avisos[0])

    def test_avisa_de_una_clave_de_la_tabla_que_no_existe(self):
        _, avisos = galeria.sembrar(INDICE, ["cuerpo"])
        self.assertTrue(any("head-boina" in a for a in avisos))

    def test_combinar_no_pisa_lo_decidido(self):
        mezcla = galeria.combinar({"cuerpo": "rechazada", "head-boina": "pendiente"}, {"cuerpo": "aprobada", "head-boina": "aprobada"})
        self.assertEqual(mezcla, {"cuerpo": "rechazada", "head-boina": "aprobada"})

    def test_resumen_cuenta_por_estado(self):
        cuenta = galeria.resumen(["a", "b", "c"], {"a": "aprobada", "b": "rechazada"})
        self.assertEqual(cuenta, {"pendiente": 1, "aprobada": 1, "rechazada": 1})

    def test_la_tabla_cubre_todo_lo_aprobado_del_indice_real(self):
        ruta = os.path.expanduser("~/Pictures/bito-habi/estado.json")
        if not os.path.exists(ruta):
            self.skipTest("sin el índice real")
        habi = exportar.cargar_json(os.path.join(os.path.dirname(galeria.__file__), "habi.json"))
        jardin = exportar.cargar_json(os.path.join(os.path.dirname(galeria.__file__), "jardin.json"))
        claves = [p["clave"] for p in habi["piezas"] if p.get("generada") != "sombra"] + [c for c, _ in exportar.claves_jardin(jardin)]
        _, avisos = galeria.sembrar(exportar.cargar_json(ruta), claves)
        self.assertEqual(avisos, [])


if __name__ == "__main__":
    unittest.main()
