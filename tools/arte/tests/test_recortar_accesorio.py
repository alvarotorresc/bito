import os
import sys
import tempfile
import unittest
from unittest import mock

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import accesorio  # noqa: E402
import recortar  # noqa: E402
from test_comun import SALVIA, elipse, lienzo  # noqa: E402

NARANJA = (230, 120, 30)
AMARILLO = (220, 160, 40)


def guardar(img, ruta):
    Image.fromarray(img.astype(np.uint8), "RGB").save(ruta)
    return ruta


class RecortarTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        hoja = lienzo(400, 200)
        hoja[20:80, 20:80] = (120, 70, 40)
        hoja[20:80, 200:260] = (120, 70, 40)
        hoja[120:180, 100:160] = (40, 40, 40)
        self.hoja = guardar(hoja, os.path.join(self.dir, "hoja.png"))

    def test_separa_cada_pieza_en_un_png(self):
        rutas = recortar.recortar(self.hoja, os.path.join(self.dir, "out"), "p-", area_min=100)
        self.assertEqual(len(rutas), 3)
        self.assertTrue(all(os.path.exists(r) for r in rutas))

    def test_un_recuento_distinto_sale_con_codigo_2(self):
        codigo = recortar.main([self.hoja, os.path.join(self.dir, "out2"), "p-", "--area-min", "100", "--esperadas", "4"])
        self.assertEqual(codigo, 2)

    def test_el_recuento_esperado_sale_con_codigo_0(self):
        codigo = recortar.main([self.hoja, os.path.join(self.dir, "out3"), "p-", "--area-min", "100", "--esperadas", "3"])
        self.assertEqual(codigo, 0)


class AccesorioTest(unittest.TestCase):
    def test_extrae_el_gorro_alineado_y_sin_el_cuerpo(self):
        base = elipse(lienzo(300, 300), 150, 170, 80, 110, SALVIA)
        editada = base.copy()
        editada[40:75, 110:190] = AMARILLO
        capa, dev, hueco = accesorio.extraer(base, editada, "accesorio")
        alfa = np.asarray(capa)[..., 3]
        self.assertEqual(capa.size, (300, 300))
        self.assertGreater(alfa[57, 150], 200)
        self.assertEqual(alfa[170, 150], 0)
        self.assertLess(dev, 0.02)
        self.assertIsNone(hueco)

    def test_dos_zapatos_separados_salen_los_dos(self):
        base = elipse(lienzo(300, 300), 150, 150, 80, 110, SALVIA)
        editada = base.copy()
        editada[225:262, 85:135] = (60, 60, 70)  # zapato izquierdo
        editada[225:262, 165:215] = (60, 60, 70)  # zapato derecho
        capa, dev, _ = accesorio.extraer(base, editada, "accesorio")
        alfa = np.asarray(capa)[..., 3]
        self.assertGreater(alfa[245, 110], 200)
        self.assertGreater(alfa[245, 190], 200)
        self.assertEqual(alfa[150, 150], 0)
        self.assertLess(dev, 0.02)

    def test_un_accesorio_que_tapa_un_lado_se_alinea_igual(self):
        base = elipse(lienzo(300, 300), 150, 150, 80, 110, SALVIA)
        # La editada sale 12 px a la derecha y 8 abajo, y unos auriculares tapan el lado izquierdo.
        editada = elipse(lienzo(300, 300), 162, 158, 80, 110, SALVIA)
        editada[120:200, 60:100] = AMARILLO
        capa, dev, _ = accesorio.extraer(base, editada, "accesorio")
        alfa = np.asarray(capa)[..., 3]
        # El accesorio, que en la editada ocupa x 60..99, y 120..199, en la base cae en x 48..87, y 112..191.
        self.assertGreater(alfa[150, 50], 200)
        self.assertGreater(alfa[185, 85], 200)
        self.assertEqual(alfa[150, 44], 0)
        self.assertEqual(alfa[150, 95], 0)
        self.assertGreater(dev, 0.02)  # se desvió 12 px de 160: se avisa, pero la capa sale alineada

    def test_una_editada_reescalada_sale_con_codigo_3(self):
        d = tempfile.mkdtemp()
        base = elipse(lienzo(300, 300), 150, 170, 80, 110, SALVIA)
        editada = elipse(lienzo(300, 300), 150, 170, 92, 126, SALVIA)
        editada[30:60, 110:190] = AMARILLO
        codigo = accesorio.main(
            [guardar(base, os.path.join(d, "b.png")), guardar(editada, os.path.join(d, "e.png")), os.path.join(d, "s.png")],
        )
        self.assertEqual(codigo, 3)

    def test_el_disfraz_conserva_la_piel_del_hueco(self):
        base = elipse(lienzo(300, 300), 150, 170, 80, 110, SALVIA)
        disfraz = elipse(lienzo(300, 300), 150, 170, 95, 110, NARANJA)
        disfraz[140:190, 120:180] = SALVIA
        capa, dev, hueco = accesorio.extraer(base, disfraz, "disfraz", caja_cara=(120, 140, 180, 190))
        alfa = np.asarray(capa)[..., 3]
        self.assertGreater(alfa[165, 150], 200)
        self.assertLess(dev, 0.02)
        self.assertGreater(hueco, 0.9)

    def test_un_disfraz_con_cola_no_se_rechaza(self):
        base = elipse(lienzo(400, 400), 200, 200, 80, 120, SALVIA)
        disfraz = elipse(lienzo(400, 400), 200, 200, 95, 120, NARANJA)
        disfraz[275:305, 20:150] = NARANJA  # cola larga a un lado, colgando hasta el 8 % sobre los pies
        disfraz[150:190, 170:230] = SALVIA
        capa, dev, hueco = accesorio.extraer(base, disfraz, "disfraz", caja_cara=(170, 150, 230, 190))
        self.assertLess(dev, 0.02)
        self.assertGreater(hueco, 0.9)
        alfa = np.asarray(capa)[..., 3]
        self.assertGreater(alfa[290, 50], 200)  # la cola sigue en la capa

    def test_un_disfraz_lila_conserva_su_color(self):
        base = elipse(lienzo(300, 300), 150, 170, 80, 110, SALVIA)
        disfraz = elipse(lienzo(300, 300), 150, 170, 95, 110, (210, 120, 176))
        disfraz[140:190, 120:180] = SALVIA
        capa, _, _ = accesorio.extraer(base, disfraz, "disfraz", caja_cara=(120, 140, 180, 190))
        pix = np.asarray(capa)[100, 100]
        self.assertGreater(pix[3], 200)
        self.assertEqual(tuple(pix[:3]), (210, 120, 176))

    def test_un_hueco_fuera_de_sitio_sale_con_codigo_4(self):
        d = tempfile.mkdtemp()
        base = elipse(lienzo(300, 300), 150, 170, 80, 110, SALVIA)
        disfraz = elipse(lienzo(300, 300), 150, 170, 95, 110, NARANJA)  # sin hueco de la cara
        ruta_b, ruta_e = guardar(base, os.path.join(d, "b.png")), guardar(disfraz, os.path.join(d, "e.png"))
        with mock.patch.object(accesorio.comun, "CAJA_CARA", (120, 140, 180, 190)), mock.patch.object(
            accesorio.comun, "ORIGEN_CUERPO", (0, 0)
        ):
            codigo = accesorio.main([ruta_b, ruta_e, os.path.join(d, "s.png"), "--modo", "disfraz"])
        self.assertEqual(codigo, 4)


if __name__ == "__main__":
    unittest.main()
