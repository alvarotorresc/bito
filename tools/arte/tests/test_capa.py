import os
import sys
import tempfile
import unittest

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import capa  # noqa: E402

MAGENTA = (255, 0, 255)
SALVIA = (150, 185, 140)
ROJO = (200, 60, 50)


def lienzo(ancho, alto, color=MAGENTA):
    img = np.zeros((alto, ancho, 3), np.float32)
    img[...] = color
    return img


def elipse(img, cx, cy, rx, ry, color):
    yy, xx = np.mgrid[0 : img.shape[0], 0 : img.shape[1]]
    img[((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1] = color
    return img


class CapaTest(unittest.TestCase):
    def setUp(self):
        self.base = elipse(lienzo(240, 240), 120, 140, 70, 90, SALVIA)
        self.puesto = self.base.copy()
        self.puesto[30:90, 60:180] = ROJO  # una prenda sobre la cabeza que sobresale al fondo

    def test_se_queda_con_lo_anadido_y_suelta_la_piel_y_el_fondo(self):
        rgba = np.asarray(capa.extraer_capa(self.base, self.puesto))
        self.assertGreater(rgba[60, 120, 3], 200)  # la prenda
        self.assertLess(rgba[180, 120, 3], 20)  # la piel de abajo
        self.assertLess(rgba[5, 5, 3], 20)  # el fondo magenta

    def test_la_linea_de_ordenes_solo_escribe_la_salida(self):
        from PIL import Image

        with tempfile.TemporaryDirectory() as d:
            base, puesto, salida = (os.path.join(d, n) for n in ("b.png", "p.png", "s.png"))
            Image.fromarray(self.base.astype(np.uint8)).save(base)
            Image.fromarray(self.puesto.astype(np.uint8)).save(puesto)
            antes = set(os.listdir(os.getcwd()))
            self.assertEqual(capa.main(["--base", base, "--puesto", puesto, "--salida", salida, "--recorte", "20,10,220,230"]), 0)
            self.assertEqual(set(os.listdir(os.getcwd())), antes)
            self.assertEqual(sorted(os.listdir(d)), ["b.png", "p.png", "s.png"])
            self.assertEqual(Image.open(salida).size, (200, 220))

    def test_tamanos_distintos_es_un_error(self):
        from PIL import Image

        with tempfile.TemporaryDirectory() as d:
            base, puesto = os.path.join(d, "b.png"), os.path.join(d, "p.png")
            Image.fromarray(self.base.astype(np.uint8)).save(base)
            Image.fromarray(self.puesto[:200].astype(np.uint8)).save(puesto)
            self.assertEqual(capa.main(["--base", base, "--puesto", puesto, "--salida", os.path.join(d, "s.png")]), 2)


if __name__ == "__main__":
    unittest.main()
