import os
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import exportar  # noqa: E402


def jardin_minimo(d):
    fondo = np.zeros((30, 40, 3), dtype=np.uint8)
    Image.fromarray(fondo, "RGB").save(os.path.join(d, "fondo.jpg"))
    pieza = np.zeros((20, 20, 4), dtype=np.uint8)
    pieza[0:20, 0:20] = (90, 140, 60, 255)
    Image.fromarray(pieza, "RGBA").save(os.path.join(d, "e1.png"))
    return {
        "fuentes": d,
        "fondo": {"archivo": "fondo.jpg"},
        "ancho_fondo": 40,
        "escala_piezas": 1.0,
        "alto_parcela": 0.16,
        "parcelas": [{"archivo": "e1.png"}],
        "especies": [{"clave": "hojas-redondas", "etapas": ["e1.png"]}],
        "especiales": [{"clave": "farolillo", "archivo": "e1.png"}],
        "escena": {"habi": [50, 93, 40], "parcelas": [[9, 64, 34.2]], "especiales": {"farolillo": [36, 58, 22]}},
    }


FUENTES = os.path.expanduser("~/Pictures/bito-habi/prueba-piezas/piezas/banco.png")


class ObjetosDelJardinTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.res = os.path.join(self.dir, "res")
        os.makedirs(self.res)
        self.jardin = jardin_minimo(self.dir)

    def kotlin(self):
        datos = exportar.exportar_jardin(self.jardin, os.path.join(self.dir, "jardin.json"), self.res, 90)
        return exportar.kotlin_jardin(datos)

    def test_exportar_emits_empty_object_maps_when_jardin_json_has_no_objetos(self):
        kt = self.kotlin()
        self.assertIn("val objectPieces: Map<String, GardenPiece> = emptyMap()", kt)
        self.assertIn("val objectSpots: Map<String, GardenSpot> = emptyMap()", kt)

    def test_exportar_is_idempotent(self):
        self.assertEqual(self.kotlin(), self.kotlin())

    def test_exportar_emits_the_banco_and_regadera_spots_from_jardin_json(self):
        real = exportar.cargar_json(os.path.join(exportar.AQUI, "jardin.json"))
        self.assertEqual(["banco", "regadera"], [o["clave"] for o in real["objetos"]])
        self.assertEqual([71, 97, 13.2], real["escena"]["especiales"]["setas"])
        self.assertEqual(sorted(["banco", "regadera"]), sorted(real["escena"]["objetos"]))
        self.assertEqual([69, 72.5], real["escena"]["objetos"]["banco"][:2])
        self.assertEqual([44, 99], real["escena"]["objetos"]["regadera"][:2])

    @unittest.skipUnless(os.path.exists(FUENTES), "las fuentes del arte no están en esta máquina")
    def test_the_spot_height_gives_the_width_of_the_mock(self):
        """alto % = ancho % x aspecto de la escena / aspecto del recorte: el banco mide el 17 % y la regadera el 9 % del ancho."""
        real = exportar.cargar_json(os.path.join(exportar.AQUI, "jardin.json"))
        with tempfile.TemporaryDirectory() as res:
            datos = exportar.exportar_jardin(real, os.path.join(exportar.AQUI, "jardin.json"), res, 90)
        for clave, ancho in (("banco", 17.0), ("regadera", 9.0)):
            alto = datos["escena"]["objetos"][clave][2]
            medido = alto * datos["objetos"][clave]["aspecto"] / datos["aspecto"]
            self.assertAlmostEqual(ancho, medido, delta=0.1, msg=clave)
        kt = exportar.kotlin_jardin(datos)
        self.assertIn('"banco" to GardenPiece(R.drawable.garden_banco', kt)
        self.assertIn('"regadera" to GardenSpot(44.0f, 99.0f', kt)
        # GardenArt.kt es un archivo generado: lo que hay en el repo es lo que emite el script.
        with open(os.path.join(exportar.KT_JARDIN, "GardenArt.kt"), encoding="utf-8") as f:
            self.assertEqual(f.read(), kt)


if __name__ == "__main__":
    unittest.main()
