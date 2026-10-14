import os
import sys
import unittest

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import anclas  # noqa: E402


def planta_sintetica():
    """Un tronco centrado (x 45..55) en toda la altura y una copa ancha arriba que llega a x=90 (la pieza recortada mide x 10..90) entre el 15 % y el 50 %."""
    arr = np.zeros((100, 100, 4), dtype=np.uint8)
    arr[:, 45:56] = (90, 60, 40, 255)
    arr[15:51, 10:91] = (60, 140, 70, 255)
    return Image.fromarray(arr, "RGBA")


class TestAnclas(unittest.TestCase):
    def test_tres_puntos_desde_el_alfa(self):
        (fx1, fy1), (fx2, fy2), (fx3, fy3) = anclas.anclas_de(planta_sintetica())
        self.assertAlmostEqual(0.99, fx1, delta=0.02)  # marco recortado: la copa llega al borde derecho
        self.assertTrue(0.10 <= fy1 <= 0.30)
        self.assertAlmostEqual(0.99, fx2, delta=0.02)
        self.assertTrue(0.35 <= fy2 <= 0.55)
        self.assertAlmostEqual(0.50, fx3, delta=0.02)
        self.assertAlmostEqual(0.70, fy3, delta=0.01)

    def test_las_fracciones_son_del_marco_recortado(self):
        arr = np.zeros((200, 200, 4), dtype=np.uint8)
        arr[50:150, 50:150] = (60, 140, 70, 255)   # pieza de 100x100 con 50 px de margen
        img = Image.fromarray(arr, "RGBA")
        (fx1, _), _, (fx3, fy3) = anclas.anclas_de(img)
        self.assertAlmostEqual(1.0, fx1, delta=0.02)
        self.assertAlmostEqual(0.5, fx3, delta=0.02)
        self.assertAlmostEqual(0.7, fy3, delta=0.01)

    def test_convertir_del_marco_entero_al_recortado(self):
        caja = (50, 50, 150, 150)
        self.assertEqual((0.5, 0.25), anclas.al_marco_recortado((0.5, 0.375), (200, 200), caja))

    def test_las_medidas_a_mano_mandan(self):
        tabla = anclas.combinar({"manzano-5": [(0.1, 0.1), (0.2, 0.2), (0.3, 0.3)]}, {"manzano-5": [(0.7, 0.2), (0.8, 0.4), (0.5, 0.6)]})
        self.assertEqual([(0.7, 0.2), (0.8, 0.4), (0.5, 0.6)], tabla["manzano-5"])

    def test_las_claves_del_prototipo_pasan_a_las_de_la_app(self):
        self.assertEqual("hojas-redondas-4", anclas.clave_app("planta-4"))
        self.assertEqual("manzano-5", anclas.clave_app("manzano-5"))

    def test_el_kotlin_tiene_las_treinta(self):
        tabla = {f"{e}-{n}": [(0.5, 0.2), (0.6, 0.4), (0.5, 0.7)] for e in anclas.ESPECIES for n in range(1, 6)}
        kt = anclas.kotlin(tabla)
        self.assertEqual(30, kt.count("floatArrayOf("))
        self.assertIn('"cerezo-2" to floatArrayOf(', kt)
        self.assertIn("internal object AnclasPlanta", kt)
