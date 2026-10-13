import os
import re
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import exportar  # noqa: E402

# Verde salvia como el del cuerpo real (pasa comun.mascara_verde) y marrón de traje.
SALVIA = (150, 185, 140, 255)
TRAJE = (130, 80, 40, 255)


def png(ruta, arr):
    Image.fromarray(arr.astype(np.uint8), "RGBA").save(ruta)
    return os.path.basename(ruta)


def disfraz(ancho, alto, centro, radio):
    """Un traje marrón que ocupa todo el cuerpo con un hueco de piel verde circular."""
    arr = np.zeros((alto, ancho, 4))
    arr[...] = TRAJE
    yy, xx = np.mgrid[0:alto, 0:ancho]
    arr[(xx - centro[0]) ** 2 + (yy - centro[1]) ** 2 <= radio**2] = SALVIA
    return arr


class ExportarHabiTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.res = os.path.join(self.dir, "res")
        os.makedirs(self.res)
        cuerpo = np.zeros((120, 100, 4))
        cuerpo[...] = (60, 170, 70, 255)
        png(os.path.join(self.dir, "cuerpo.png"), cuerpo)
        ojo = np.zeros((10, 10, 4))
        ojo[2:8, 2:8] = (120, 70, 40, 255)
        ojo[4, 4] = (255, 255, 255, 255)
        png(os.path.join(self.dir, "ojo.png"), ojo)
        gorro = np.zeros((150, 100, 4))
        gorro[0:20, 30:70] = (220, 160, 40, 255)
        png(os.path.join(self.dir, "gorro.png"), gorro)
        self.habi = {
            "fuentes": self.dir,
            "cuerpo": {"ancho": 100, "alto": 120},
            "alto_salida": 60,
            "piezas": [
                {"clave": "cuerpo", "archivo": "cuerpo.png", "origen": [0, 0]},
                {"clave": "sombra", "generada": "sombra", "caja": [-5, 110, 110, 20]},
                {"clave": "ojo-abierto-i", "archivo": "ojo.png", "centro": [30, 40], "escala": 2, "iris": True},
                {"clave": "head-gorro-lana", "archivo": "gorro.png", "origen": [0, -30]},
                {"clave": "costume-calabaza", "archivo": "cuerpo.png", "origen": [0, 0], "provisional": True},
            ],
        }
        self.ruta = os.path.join(self.dir, "habi.json")

    def test_escribe_un_webp_por_pieza_con_su_prefijo(self):
        exportar.exportar_habi(self.habi, self.ruta, self.res, 90)
        for nombre in ["habi_cuerpo", "habi_sombra", "habi_ojo_abierto_i", "habi_ojo_abierto_i_iris", "habi_head_gorro_lana", "costume_calabaza"]:
            self.assertTrue(os.path.exists(os.path.join(self.res, nombre + ".webp")), nombre)

    def test_el_lienzo_cubre_el_gorro_y_la_sombra(self):
        _, lienzo, fuente_px = exportar.exportar_habi(self.habi, self.ruta, self.res, 90)
        left, top, ancho, alto = lienzo
        self.assertEqual(top, -30.0)
        self.assertEqual(left, -5.0)
        self.assertEqual(ancho, 110.0)
        self.assertEqual(alto, 160.0)
        self.assertAlmostEqual(fuente_px, 80.0)

    def test_el_ojo_recortado_queda_en_su_sitio(self):
        entradas, _, _ = exportar.exportar_habi(self.habi, self.ruta, self.res, 90)
        ojo = next(e for e in entradas if e["clave"] == "ojo-abierto-i")
        # 10 px a escala 2 centrados en (30, 40) empiezan en (20, 30); el contenido va de 2 a 8.
        self.assertEqual((ojo["left"], ojo["top"], ojo["ancho"], ojo["alto"]), (24.0, 34.0, 12.0, 12.0))

    def test_la_mascara_de_iris_quita_el_brillo_blanco(self):
        exportar.exportar_habi(self.habi, self.ruta, self.res, 100)
        ojo = np.asarray(Image.open(os.path.join(self.res, "habi_ojo_abierto_i.webp")).convert("RGBA"))
        iris = np.asarray(Image.open(os.path.join(self.res, "habi_ojo_abierto_i_iris.webp")).convert("RGBA"))
        self.assertGreater((iris[..., 3] > 127).sum(), 0)
        self.assertLess((iris[..., 3] > 127).sum(), (ojo[..., 3] > 127).sum())

    def test_el_kotlin_del_atlas_lleva_cada_pieza(self):
        entradas, lienzo, fuente_px = exportar.exportar_habi(self.habi, self.ruta, self.res, 90)
        kt = exportar.kotlin_atlas(entradas, lienzo, self.habi["cuerpo"], fuente_px)
        self.assertIn("// Generado por tools/arte/exportar.py. No editar a mano.", kt)
        self.assertIn('"cuerpo" to AtlasPiece(R.drawable.habi_cuerpo, 0.0f, 0.0f, 100.0f, 120.0f)', kt)
        self.assertIn("iris = R.drawable.habi_ojo_abierto_i_iris", kt)
        self.assertIn("val canvas = RigCanvas(left = -5.0f, top = -30.0f, width = 110.0f, height = 160.0f)", kt)
        self.assertTrue(all(len(linea) <= 120 for linea in kt.splitlines()))

    def test_definitivo_rechaza_provisionales_y_piezas_sin_aprobar(self):
        estado = {"piezas": {p["clave"]: "aprobada" for p in self.habi["piezas"]}}
        estado["piezas"]["head-gorro-lana"] = "rechazada"
        problemas = exportar.comprobar_definitivo(self.habi, {"especies": [], "parcelas": [], "especiales": []}, estado)
        self.assertTrue(any("costume-calabaza" in p for p in problemas))
        self.assertTrue(any("head-gorro-lana" in p for p in problemas))
        self.assertFalse(any("sombra" in p for p in problemas))

    def test_definitivo_exige_el_desplazamiento_de_cara_aprobado_en_cada_disfraz(self):
        self.habi["piezas"][4] = {"clave": "costume-calabaza", "archivo": "cuerpo.png", "origen": [0, 0]}
        self.habi["piezas"].append({"clave": "costume-reno", "archivo": "cuerpo.png", "origen": [0, 0], "cara": [-19, -71]})
        estado = {"piezas": {p["clave"]: "aprobada" for p in self.habi["piezas"]}}
        problemas = exportar.comprobar_definitivo(self.habi, {"especies": [], "parcelas": [], "especiales": []}, estado)
        self.assertEqual(problemas, ["costume-calabaza: sin desplazamiento de cara aprobado"])

    # C11: rigFit centra el lienzo, así que el lienzo tiene que estar centrado en el cuerpo.
    def test_una_pieza_que_sobresale_solo_por_la_izquierda_ensancha_el_lienzo_por_los_dos_lados(self):
        cola = np.zeros((20, 40, 4))
        cola[...] = (90, 60, 160, 255)
        png(os.path.join(self.dir, "cola.png"), cola)
        self.habi["piezas"].append({"clave": "costume-dinosaurio", "archivo": "cola.png", "origen": [-30, 90]})
        _, lienzo, _ = exportar.exportar_habi(self.habi, self.ruta, self.res, 90)
        left, _, ancho, _ = lienzo
        self.assertEqual(left, -30.0)
        self.assertEqual(ancho, 160.0)
        self.assertEqual(left + ancho / 2, self.habi["cuerpo"]["ancho"] / 2)

    # C12: los centros y escalas de la cara viven solo en caras.json.
    def test_una_pieza_de_cara_sin_posicion_la_toma_de_caras_json(self):
        caras = {"piezas": {"ojo-abierto-i": {"centro": [30, 40], "escala": 2}}}
        self.habi["piezas"][2] = {"clave": "ojo-abierto-i", "archivo": "ojo.png", "iris": True}
        entradas, _, _ = exportar.exportar_habi(self.habi, self.ruta, self.res, 90, caras)
        ojo = next(e for e in entradas if e["clave"] == "ojo-abierto-i")
        self.assertEqual((ojo["left"], ojo["top"], ojo["ancho"], ojo["alto"]), (24.0, 34.0, 12.0, 12.0))

    def test_una_pieza_sin_posicion_ni_entrada_en_caras_json_es_un_error(self):
        self.habi["piezas"][2] = {"clave": "ojo-abierto-i", "archivo": "ojo.png"}
        with self.assertRaises(SystemExit):
            exportar.exportar_habi(self.habi, self.ruta, self.res, 90, {"piezas": {}})


class CaraDelDisfrazTest(unittest.TestCase):
    """C3: con disfraz, la cara se desplaza a su hueco y se recorta con la máscara del hueco."""

    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.res = os.path.join(self.dir, "res")
        os.makedirs(self.res)
        self.ref = exportar.REFERENCIA_CARA
        # El disfraz se pinta en un lienzo con el cuerpo en (40, 30), como las capas de la base.
        self.origen = (-40, -30)
        self.centro_hueco = (self.ref[0] + 12, self.ref[1] - 20)
        arr = disfraz(1300, 1500, (self.centro_hueco[0] + 40, self.centro_hueco[1] + 30), 150)
        png(os.path.join(self.dir, "reno.png"), arr)
        self.habi = {
            "fuentes": self.dir,
            "cuerpo": {"ancho": 1132, "alto": 1404},
            "alto_salida": 1404,
            "piezas": [{"clave": "costume-reno", "archivo": "reno.png", "origen": list(self.origen)}],
        }
        self.ruta = os.path.join(self.dir, "habi.json")

    def cara(self):
        entradas, _, _ = exportar.exportar_habi(self.habi, self.ruta, self.res, 100)
        return entradas[0]["cara"]

    def test_el_desplazamiento_lleva_la_cara_al_centro_del_hueco(self):
        cara = self.cara()
        self.assertAlmostEqual(cara["dx"], 12.0, delta=0.5)
        self.assertAlmostEqual(cara["dy"], -20.0, delta=0.5)

    def test_el_desplazamiento_aprobado_del_manifiesto_manda_sobre_el_calculado(self):
        self.habi["piezas"][0]["cara"] = [-19, -71]
        cara = self.cara()
        self.assertEqual((cara["dx"], cara["dy"]), (-19.0, -71.0))

    def test_la_mascara_del_hueco_deja_ver_el_hueco_y_tapa_el_traje(self):
        cara = self.cara()
        m = cara["mascara"]
        self.assertEqual(m["recurso"], "costume_reno_hueco")
        img = np.asarray(Image.open(os.path.join(self.res, "costume_reno_hueco.webp")).convert("RGBA"))
        # El rectángulo de la máscara va en coordenadas del cuerpo: el hueco (r = 150), dilatado 1 px
        # y suavizado, ocupa unos 2 px más por cada lado.
        self.assertAlmostEqual(m["left"], self.centro_hueco[0] - 152, delta=2)
        self.assertAlmostEqual(m["top"], self.centro_hueco[1] - 152, delta=2)
        self.assertAlmostEqual(m["ancho"], 305, delta=3)
        cx = round(self.centro_hueco[0] - m["left"])
        cy = round(self.centro_hueco[1] - m["top"])
        self.assertGreater(img[cy, cx, 3], 250)
        self.assertEqual(img[2, 2, 3], 0)  # esquina del rectángulo: fuera del círculo, sobre el traje

    def test_la_mascara_no_cuenta_para_el_lienzo(self):
        _, lienzo, _ = exportar.exportar_habi(self.habi, self.ruta, self.res, 100)
        self.assertEqual(lienzo[1], -30.0)

    def test_un_disfraz_sin_piel_verde_no_mueve_ni_recorta_la_cara(self):
        png(os.path.join(self.dir, "reno.png"), np.full((1500, 1300, 4), TRAJE))
        entradas, _, _ = exportar.exportar_habi(self.habi, self.ruta, self.res, 100)
        self.assertIsNone(entradas[0]["cara"])
        self.assertFalse(os.path.exists(os.path.join(self.res, "costume_reno_hueco.webp")))

    def test_un_disfraz_provisional_que_es_el_cuerpo_entero_no_tiene_hueco(self):
        # El arte provisional reutiliza cuerpo.png: toda la piel es «hueco», más que una cara.
        png(os.path.join(self.dir, "reno.png"), np.full((1404, 1132, 4), SALVIA))
        self.habi["piezas"][0]["origen"] = [0, 0]
        entradas, _, _ = exportar.exportar_habi(self.habi, self.ruta, self.res, 100)
        self.assertIsNone(entradas[0]["cara"])

    def test_el_kotlin_lleva_la_cara_de_cada_disfraz(self):
        self.habi["piezas"][0]["cara"] = [-19, -71]
        entradas, lienzo, fuente_px = exportar.exportar_habi(self.habi, self.ruta, self.res, 100)
        kt = exportar.kotlin_atlas(entradas, lienzo, self.habi["cuerpo"], fuente_px)
        self.assertIn("val costumeFaces: Map<String, CostumeFace> =", kt)
        self.assertIn('"costume-reno" to', kt)
        plano = re.sub(r",\s*\)", ")", re.sub(r"\(\s+", "(", re.sub(r"\s+", " ", kt)))
        self.assertIn("CostumeFace(-19.0f, -71.0f, AtlasPiece(R.drawable.costume_reno_hueco, ", plano)
        self.assertTrue(all(len(linea) <= 120 for linea in kt.splitlines()))

    def test_sin_disfraces_con_hueco_el_mapa_de_caras_queda_vacio(self):
        kt = exportar.kotlin_atlas([], (0.0, 0.0, 1.0, 1.0), {"ancho": 1, "alto": 1}, 1.0)
        self.assertIn("val costumeFaces: Map<String, CostumeFace> = emptyMap()", kt)


class ExportarTintesYJardinTest(unittest.TestCase):
    def test_los_tintes_se_generan_con_sus_valores_por_defecto(self):
        kt = exportar.kotlin_tintes({"cuerpo": {"body-cielo": {"hue": 75, "saturacion": 1.2}}, "ojos": {}, "apagado": {"saturacion": 0.55, "brillo": 0.95}})
        self.assertIn('"body-cielo" to ColorTint(hueDegrees = 75.0f, saturation = 1.2f, brightness = 1.0f, sepia = 0.0f)', kt)
        self.assertIn("val muted = ColorTint(hueDegrees = 0.0f, saturation = 0.55f, brightness = 0.95f, sepia = 0.0f)", kt)

    def test_el_jardin_guarda_aspecto_y_alto_relativo_de_cada_etapa(self):
        d = tempfile.mkdtemp()
        res = os.path.join(d, "res")
        os.makedirs(res)
        Image.new("RGB", (40, 30), (120, 160, 90)).save(os.path.join(d, "fondo.jpg"))
        chica = np.zeros((20, 20, 4))
        chica[10:20, 5:15] = (90, 140, 60, 255)
        grande = np.zeros((40, 20, 4))
        grande[0:40, 0:20] = (90, 140, 60, 255)
        png(os.path.join(d, "e1.png"), chica)
        png(os.path.join(d, "e2.png"), grande)
        jardin = {
            "fuentes": d,
            "fondo": {"archivo": "fondo.jpg"},
            "ancho_fondo": 40,
            "escala_piezas": 1.0,
            "alto_parcela": 0.16,
            "parcelas": [{"archivo": "e1.png"}],
            "especies": [{"clave": "hojas-redondas", "etapas": ["e1.png", "e2.png"]}],
            "especiales": [{"clave": "farolillo", "archivo": "e2.png"}],
            "escena": {"habi": [50, 93, 40], "parcelas": [[9, 64, 34.2]], "especiales": {"farolillo": [36, 58, 22]}},
        }
        datos = exportar.exportar_jardin(jardin, os.path.join(d, "jardin.json"), res, 90)
        kt = exportar.kotlin_jardin(datos)
        self.assertIn("const val SCENE_ASPECT = 1.3333f", kt)
        self.assertIn("GardenPiece(R.drawable.garden_hojas_redondas_1, aspect = 1.0000f, heightRatio = 0.2500f)", kt)
        self.assertIn("val habiSpot = GardenSpot(50.0f, 93.0f, 40.0f)", kt)
        self.assertTrue(os.path.exists(os.path.join(res, "garden_fondo.webp")))
        self.assertEqual(
            [c for c, _ in exportar.claves_jardin(jardin)],
            ["fondo", "parcela-1", "hojas-redondas-1", "hojas-redondas-2", "farolillo"],
        )

    def test_el_jardin_genera_el_orden_de_las_especies(self):
        datos = {
            "aspecto": 1.5,
            "parcelas": [],
            "especies": [],
            "claves_especies": ["hojas-redondas", "girasoles"],
            "especiales": {},
            "escena": {"habi": [50, 88, 40], "parcelas": [], "especiales": {}},
        }
        kt = exportar.kotlin_jardin(datos)
        self.assertIn('val plantKeys: List<String> = listOf("hojas-redondas", "girasoles")', kt)


class AspectoDelJardinTest(unittest.TestCase):
    """C12: el aspecto del jardín sale de tintes.json, no se copia a mano en Kotlin ni en la galería."""

    JARDIN = {
        "mustio_planta": {"saturacion": 0.2, "sepia": 0.15, "brillo": 0.1 / 3, "encoge": 0.04, "inclina": 1.7},
        "mustio_cesped": {"saturacion": 0.4 / 3, "sepia": 0.25 / 3, "brillo": 0.01},
        "estaciones": {"SPRING": {"saturacion": 1.08}, "AUTUMN": {"hue": -14, "saturacion": 1.05, "sepia": 0.18}},
        "luz": {"DIA": ["#FFFFFF", "#FFFFFF"], "NOCHE": ["#31406E", "#4B5A8A"]},
        "hundido_tierra": 0.35,
    }

    def test_genera_las_constantes_del_mustio_y_el_hundido(self):
        kt = exportar.kotlin_aspecto_jardin(self.JARDIN)
        self.assertIn("// Generado por tools/arte/exportar.py. No editar a mano.", kt)
        self.assertIn("internal object GardenLookTable {", kt)
        self.assertIn("const val PLANT_SATURATION_LOSS = 0.2f", kt)
        self.assertIn("const val PLANT_DIM = 0.033333f", kt)
        self.assertIn("const val PLANT_LEAN_DEGREES = 1.7f", kt)
        self.assertIn("const val GRASS_SEPIA = 0.083333f", kt)
        self.assertIn("const val SOIL_SINK = 0.35f", kt)

    def test_genera_estaciones_y_luz_por_nombre_de_enum(self):
        kt = exportar.kotlin_aspecto_jardin(self.JARDIN)
        self.assertIn('"AUTUMN" to ColorTint(hueDegrees = -14.0f, saturation = 1.05f, brightness = 1.0f, sepia = 0.18f)', kt)
        self.assertNotIn('"SUMMER"', kt)
        self.assertIn('"NOCHE" to (0xFF31406EL to 0xFF4B5A8AL)', kt)
        self.assertTrue(all(len(linea) <= 120 for linea in kt.splitlines()))


class TablaDeCarasTest(unittest.TestCase):
    """C12: la tabla de caras vive en tools/arte/caras.json y se genera en Kotlin."""

    def setUp(self):
        self.caras = exportar.cargar_json(os.path.join(exportar.AQUI, "caras.json"))

    def test_caras_json_lleva_la_tabla_aprobada(self):
        # Spec §5.2 (tabla aprobada, la del prototipo vivo).
        tabla = self.caras["tabla"]
        self.assertEqual(tabla["RADIANT"]["SARGENTO"], ["ojo-feliz", "boca-sonrisa", "ceja-dura"])
        self.assertEqual(tabla["WILTED"]["SARGENTO"], ["ojo-abierto", "boca-triste", "ceja-preocupada"])
        self.assertEqual(tabla["DRAMATIC"]["CHEERLEADER"], ["ojo-lloroso", "boca-o", None])
        self.assertEqual(sorted(tabla), ["DRAMATIC", "NORMAL", "RADIANT", "WILTED"])
        for fila in tabla.values():
            self.assertEqual(sorted(fila), ["CHEERLEADER", "NEUTRA", "SARGENTO"])

    def test_caras_json_lleva_centro_y_escala_de_cada_pieza(self):
        piezas = self.caras["piezas"]
        self.assertEqual(piezas["ojo-abierto-i"], {"centro": [436, 533], "escala": 2.25})
        self.assertEqual(piezas["ceja-dura-d"], {"centro": [822, 425], "escala": 0.42})
        self.assertEqual(piezas["pintura-d-b"], {"centro": [940, 690], "escala": 1.3})
        self.assertEqual(len(piezas), 30)

    def test_toda_pieza_de_la_tabla_y_de_las_marcas_tiene_posicion(self):
        piezas = self.caras["piezas"]
        for fila in self.caras["tabla"].values():
            for ojos, boca, cejas in fila.values():
                self.assertIn(ojos + "-i", piezas)
                self.assertIn(boca, piezas)
                if cejas:
                    self.assertIn(cejas + "-d", piezas)
        for marcas in self.caras["marcas"].values():
            for marca in marcas:
                self.assertIn(marca, piezas)

    def test_el_kotlin_de_la_tabla_de_caras(self):
        kt = exportar.kotlin_caras(self.caras)
        self.assertIn("// Generado por tools/arte/exportar.py. No editar a mano.", kt)
        self.assertIn("internal object HabiFaceTable {", kt)
        self.assertIn('"SARGENTO" to Triple("ojo-feliz", "boca-sonrisa", "ceja-dura"),', kt)
        self.assertIn('"NEUTRA" to Triple("ojo-feliz", "boca-feliz", null),', kt)
        self.assertIn('"CHEERLEADER" to listOf("mofletes-i", "mofletes-d"),', kt)
        self.assertIn('"NEUTRA" to emptyList(),', kt)
        self.assertTrue(all(len(linea) <= 120 for linea in kt.splitlines()))


if __name__ == "__main__":
    unittest.main()
