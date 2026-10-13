import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import comun  # noqa: E402

MAGENTA = (255, 0, 255)
# Verde salvia como el del cuerpo real: h ~ 107, s ~ 0,24, v ~ 0,73.
SALVIA = (150, 185, 140)
LILA_FUERTE = (210, 120, 176)  # min(R, B) - G = 56


def lienzo(ancho, alto, color=MAGENTA):
    img = np.zeros((alto, ancho, 3), np.float32)
    img[...] = color
    return img


def elipse(img, cx, cy, rx, ry, color):
    yy, xx = np.mgrid[0 : img.shape[0], 0 : img.shape[1]]
    dentro = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1
    img[dentro] = color
    return img


class QuitarMagentaTest(unittest.TestCase):
    def test_el_magenta_es_transparente_y_la_piel_opaca(self):
        img = lienzo(10, 10)
        img[5, 5] = SALVIA
        rgba = comun.quitar_magenta(img)
        self.assertEqual(rgba[0, 0, 3], 0)
        self.assertEqual(rgba[5, 5, 3], 255)

    def test_un_traje_lila_rodeado_de_magenta_sobrevive_y_su_hueco_puro_no(self):
        self.assertEqual(min(LILA_FUERTE[0], LILA_FUERTE[2]) - LILA_FUERTE[1], 56)
        img = lienzo(120, 120)
        img[10:110, 10:110] = LILA_FUERTE
        img[40:70, 40:70] = MAGENTA  # hueco interior de magenta puro (900 px)
        rgba = comun.quitar_magenta(img)
        self.assertEqual(rgba[20, 20, 3], 255)
        self.assertEqual(tuple(rgba[20, 20, :3]), (210, 120, 176))  # sin despill dentro de la pieza
        self.assertEqual(rgba[55, 55, 3], 0)
        self.assertEqual(rgba[2, 2, 3], 0)

    def test_un_punto_magenta_interior_pequeno_no_se_borra(self):
        img = lienzo(60, 60, SALVIA)
        img[30, 30] = MAGENTA
        self.assertEqual(comun.quitar_magenta(img)[30, 30, 3], 255)


class ComponentesTest(unittest.TestCase):
    def test_cuenta_tres_manchas_separadas(self):
        m = np.zeros((40, 40), bool)
        m[2:6, 2:6] = True
        m[20:25, 3:8] = True
        m[30:38, 30:38] = True
        self.assertEqual(len(comun.componentes(m)), 3)

    def test_las_diagonales_cuentan_como_conectadas(self):
        m = np.zeros((10, 10), bool)
        m[2, 2] = m[3, 3] = m[4, 4] = True
        self.assertEqual(len(comun.componentes(m)), 1)

    def test_area_minima_descarta_ruido(self):
        m = np.zeros((40, 40), bool)
        m[2:6, 2:6] = True
        m[30, 30] = True
        self.assertEqual(len(comun.componentes(m, area_min=4)), 1)

    def test_mayor_componente_se_queda_solo_con_la_grande(self):
        m = np.zeros((40, 40), bool)
        m[2:6, 2:6] = True
        m[10:30, 10:30] = True
        grande = comun.mayor_componente(m, paso=1)
        self.assertTrue(grande[20, 20])
        self.assertFalse(grande[3, 3])

    def test_componentes_grandes_conserva_dos_zapatos_y_tira_el_ruido(self):
        m = np.zeros((60, 60), bool)
        m[40:50, 5:25] = True  # zapato izquierdo (200)
        m[40:50, 35:53] = True  # zapato derecho (180)
        m[2:4, 2:4] = True  # mota
        quedan = comun.componentes_grandes(m, paso=1)
        self.assertTrue(quedan[45, 10] and quedan[45, 40])
        self.assertFalse(quedan[3, 3])


class AlineacionTest(unittest.TestCase):
    def test_la_misma_imagen_no_se_desvia(self):
        base = elipse(lienzo(300, 300), 150, 160, 80, 110, SALVIA)
        b = comun.contorno(comun.mascara_verde(base))
        self.assertAlmostEqual(comun.desviacion(b, b), 0.0, places=6)

    def test_un_habi_movido_un_cinco_por_ciento_supera_el_umbral(self):
        base = elipse(lienzo(300, 300), 150, 160, 80, 110, SALVIA)
        movida = elipse(lienzo(300, 300), 158, 160, 80, 110, SALVIA)
        b = comun.contorno(comun.mascara_verde(base))
        e = comun.contorno(comun.mascara_verde(movida))
        self.assertGreater(comun.desviacion(b, e), comun.UMBRAL_ALINEACION)

    def test_la_mascara_de_piel_acepta_el_salvia_y_rechaza_el_resto(self):
        img = lienzo(4, 1)
        img[0, 1] = SALVIA
        img[0, 2] = (230, 120, 30)  # naranja
        img[0, 3] = (10, 25, 10)  # negro con tinte verde
        self.assertEqual(comun.mascara_verde(img)[0].tolist(), [False, True, False, False])

    def test_alinear_encuentra_el_desplazamiento(self):
        base = elipse(lienzo(300, 300), 150, 160, 80, 110, SALVIA)
        movida = elipse(lienzo(300, 300), 140, 150, 80, 110, SALVIA)
        iou, escala, dx, dy = comun.alinear(comun.mascara_verde(base), comun.mascara_verde(movida))
        self.assertGreater(iou, 0.95)
        self.assertAlmostEqual(escala, 1.0, places=2)
        self.assertAlmostEqual(dx, 10, delta=2)
        self.assertAlmostEqual(dy, 10, delta=2)

    def test_un_disfraz_mas_ancho_no_cuenta_como_escala(self):
        base = elipse(lienzo(300, 300), 150, 160, 80, 110, SALVIA)
        disfraz = elipse(lienzo(300, 300), 150, 160, 110, 110, (230, 120, 30))
        b = comun.pies(comun.mascara_verde(base), 220)
        e = comun.pies(comun.quitar_magenta(disfraz)[..., 3] > 127, 220)
        self.assertLess(comun.desviacion_disfraz(b, e, 160), comun.UMBRAL_ALINEACION)

    def test_un_disfraz_con_cola_a_un_lado_no_supera_el_umbral(self):
        base = elipse(lienzo(400, 400), 200, 200, 80, 120, SALVIA)
        disfraz = elipse(lienzo(400, 400), 200, 200, 95, 120, (230, 120, 30))
        disfraz[275:305, 20:150] = (230, 120, 30)  # cola larga a la izquierda, colgando hasta el 8 % sobre los pies
        b = comun.pies(comun.mascara_verde(base), 240)
        e = comun.pies(comun.quitar_magenta(disfraz)[..., 3] > 127, 240)
        self.assertLess(comun.desviacion_disfraz(b, e, 160), comun.UMBRAL_ALINEACION)
        # El contorno entero sí se habría desplazado más del 2 %.
        bx = comun.contorno(comun.mascara_verde(base))
        ex = comun.contorno(comun.quitar_magenta(disfraz)[..., 3] > 127)
        centro = abs((ex[0] + ex[1]) / 2 - (bx[0] + bx[1]) / 2) / (bx[1] - bx[0])
        self.assertGreater(centro, comun.UMBRAL_ALINEACION)


class HaloYHuecoTest(unittest.TestCase):
    def test_un_borde_magenta_semitransparente_es_halo(self):
        rgba = np.zeros((10, 10, 4), np.uint8)
        rgba[2:8, 2:8] = (200, 60, 200, 128)
        self.assertGreater(comun.halo(rgba), comun.UMBRAL_HALO)

    def test_un_borde_limpio_no_es_halo(self):
        rgba = np.zeros((10, 10, 4), np.uint8)
        rgba[2:8, 2:8] = (60, 170, 70, 128)
        self.assertLessEqual(comun.halo(rgba), comun.UMBRAL_HALO)

    def test_fraccion_verde_mide_la_piel_de_una_caja(self):
        img = lienzo(20, 20, (230, 120, 30))
        img[5:15, 5:10] = SALVIA
        self.assertAlmostEqual(comun.fraccion_verde(img, (5, 5, 15, 15)), 0.5, places=6)


if __name__ == "__main__":
    unittest.main()
