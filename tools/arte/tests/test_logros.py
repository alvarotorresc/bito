import os
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import exportar  # noqa: E402


def pieza(ruta, ancho, alto):
    arr = np.zeros((alto, ancho, 4), dtype=np.uint8)
    arr[10 : alto - 10, 20 : ancho - 20] = (200, 120, 60, 255)
    Image.fromarray(arr, "RGBA").save(ruta)


class TestLogros(unittest.TestCase):
    def test_cada_logro_sale_cuadrado_al_tamano_y_con_su_nombre(self):
        with tempfile.TemporaryDirectory() as d:
            pieza(os.path.join(d, "r1.png"), 300, 200)
            pieza(os.path.join(d, "m4.png"), 120, 260)
            manifiesto = {"fuentes": d, "px": 64, "logros": [{"pieza": "r1", "id": "streak-7"}, {"pieza": "m4", "id": "first-freezer"}]}
            res = os.path.join(d, "res")
            os.makedirs(res)
            exportar.exportar_logros(manifiesto, res, 90)
            for nombre in ("logro_r1.webp", "logro_m4.webp"):
                img = Image.open(os.path.join(res, nombre))
                self.assertEqual((64, 64), img.size)
                self.assertGreater(img.getchannel("A").getextrema()[1], 0)

    def test_el_kotlin_mapea_cada_id_a_su_recurso(self):
        manifiesto = {"logros": [{"pieza": "r1", "id": "streak-7"}, {"pieza": "c6", "id": "perfect-month"}]}
        kt = exportar.kotlin_logros(manifiesto)
        self.assertIn('"streak-7" to R.drawable.logro_r1', kt)
        self.assertIn('"perfect-month" to R.drawable.logro_c6', kt)
        self.assertIn("internal object LogrosArte", kt)
        self.assertTrue(kt.startswith(exportar.CABECERA))

    def test_una_pieza_que_falta_para_la_exportacion(self):
        with tempfile.TemporaryDirectory() as d:
            manifiesto = {"fuentes": d, "px": 64, "logros": [{"pieza": "r9", "id": "streak-7"}]}
            with self.assertRaises(SystemExit):
                exportar.exportar_logros(manifiesto, d, 90)
