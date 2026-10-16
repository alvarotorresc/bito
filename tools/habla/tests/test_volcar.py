import os, sys, tempfile, unittest
import xml.etree.ElementTree as ET
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import volcar

CAB = "bolsa\tindice\tetiquetas\tes\ten\n"


def escribir(dir_, nombre, lineas):
    with open(os.path.join(dir_, nombre), "w", encoding="utf-8") as f:
        f.write(CAB + "".join(l + "\n" for l in lineas))


def items(ruta, nombre):
    raiz = ET.parse(ruta).getroot()
    for a in raiz.findall("string-array"):
        if a.get("name") == nombre:
            return [i.text for i in a.findall("item")]
    raise AssertionError("no hay " + nombre)


class VolcarTest(unittest.TestCase):
    def preparar(self, neutra, sargento=None):
        self.bor = tempfile.mkdtemp()
        self.res = tempfile.mkdtemp()
        escribir(self.bor, "C.neutra.tsv", neutra)
        escribir(self.bor, "C.sargento.tsv", sargento or ["nu_saludo\t0\t\tBuenas, {nombre}.\tHey, {nombre}."])

    def test_escribe_los_dos_idiomas_en_orden_de_indice(self):
        self.preparar([
            "nu_saludo\t1\t\tSegundo, {nombre}.\tSecond, {nombre}.",
            "nu_saludo\t0\t[lunes,invierno]\tPrimero, {nombre}.\tFirst, {nombre}.",
        ])
        volcar.volcar(self.bor, self.res)
        en = os.path.join(self.res, "values", "strings_habla_hoy2.xml")
        es = os.path.join(self.res, "values-es", "strings_habla_hoy2.xml")
        self.assertEqual(["[lunes,invierno] First, {nombre}.", "Second, {nombre}."], items(en, "habla_nu_saludo_neutra"))
        self.assertEqual(["[lunes,invierno] Primero, {nombre}.", "Segundo, {nombre}."], items(es, "habla_nu_saludo_neutra"))
        self.assertEqual(["Hey, {nombre}."], items(en, "habla_nu_saludo_sargento"))

    def test_escapes(self):
        self.preparar(["nu_saludo\t0\t\tCafé & té, {nombre}.\tIt's \"fine\" & ok, {nombre}."])
        volcar.volcar(self.bor, self.res)
        en = os.path.join(self.res, "values", "strings_habla_hoy2.xml")
        with open(en, encoding="utf-8") as fh:
            crudo = fh.read()
        self.assertIn("&amp;", crudo)
        self.assertEqual(["It\\'s \\\"fine\\\" & ok, {nombre}."], items(en, "habla_nu_saludo_neutra"))
        es = os.path.join(self.res, "values-es", "strings_habla_hoy2.xml")
        self.assertEqual(["Café & té, {nombre}."], items(es, "habla_nu_saludo_neutra"))
        self.assertTrue(crudo.startswith('<?xml version="1.0" encoding="utf-8"?>'))
        self.assertIn("Generado por tools/habla/volcar.py; no editar a mano", crudo)

    def test_volcar_rechaza_si_comprobar_falla(self):
        self.preparar(["nu_saludo\t0\t\tHola — mal, {nombre}.\tHi."])
        with self.assertRaises(SystemExit):
            volcar.volcar(self.bor, self.res)
        self.assertEqual([], os.listdir(self.res))


if __name__ == "__main__":
    unittest.main()
