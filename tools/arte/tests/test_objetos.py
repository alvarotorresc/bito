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

    def test_el_jardin_real_tiene_la_clave_objetos_vacia(self):
        real = exportar.cargar_json(os.path.join(exportar.AQUI, "jardin.json"))
        self.assertEqual(real.get("objetos", []), [])


if __name__ == "__main__":
    unittest.main()
