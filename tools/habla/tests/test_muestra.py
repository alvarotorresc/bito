import os, sys, tempfile, unittest
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import muestra

CAB = "bolsa\tindice\tetiquetas\tes\ten\n"
NOMBRES = ["Estiramientos largos", "Ana"]


def tsv_valido():
    filas = []
    for i in range(8):
        filas.append(f"nu_saludo\t{i}\t\tHola {i}, {{nombre}}. Llevas {{hechos}} de {{total}}.\tHi, {{nombre}}. {{hechos}} of {{total}} so far.")
    for i in range(8, 12):
        filas.append(f"nu_saludo\t{i}\t\tHoy {i} llevas {{hechos}} de {{total}}.\tToday: {{hechos}} of {{total}}.")
    for i in range(10):
        filas.append(f"nu_todo_hecho\t{i}\t\tTodo hecho {i}: {{n|bito|bitos}} para ti.\tAll done: {{n|bito|bitos}} for you.")
    f = tempfile.NamedTemporaryFile("w", suffix=".neutra.tsv", delete=False, encoding="utf-8")
    f.write(CAB + "".join(l + "\n" for l in filas)); f.close()
    return f.name


class MuestraTest(unittest.TestCase):
    def test_treinta_frases_deterministas_y_sin_marcas(self):
        ruta = tsv_valido()
        a = muestra.componer(ruta, n=30, semilla=1)
        b = muestra.componer(ruta, n=30, semilla=1)
        self.assertEqual(30, len(a))
        self.assertEqual(a, b)
        for frase in a:
            self.assertNotIn("{", frase)
            self.assertNotIn("[", frase)

    def test_nombre_de_persona_largo_y_sin_nombre(self):
        det = muestra.componer_detalle(tsv_valido(), n=30, semilla=1)
        self.assertTrue(any("Maria de los Angeles" in d["es"] for d in det))
        self.assertFalse(any("Estiramientos largos" in d["es"] and "Hola, Estiramientos" in d["es"] for d in det))
        # sin nombre: salen núcleos de nu_saludo, y solo los que no llevan {nombre}
        sin = [d for d in det if d["sin_nombre"] and "nu_saludo" in d["bolsas"]]
        self.assertTrue(sin)
        for d in sin:
            self.assertNotIn("Hola ", d["es"])
            self.assertNotIn("nombre", d["datos"])
        # con nombre: ninguna frase con hueco {nombre} usa otra cosa que un nombre de persona
        for d in det:
            if "nombre" in d["datos"]:
                self.assertIn(d["datos"]["nombre"], ("Ana", "Luis", "Maria de los Angeles"))

    def test_sin_repetidos_y_con_datos(self):
        det = muestra.componer_detalle(tsv_valido(), n=30, semilla=1)
        self.assertEqual(30, len({d["es"] for d in det}))
        self.assertTrue(all(d["etiquetas"] for d in det))
        destino = muestra.escribir(tsv_valido(), tempfile.mkdtemp())
        with open(destino, encoding="utf-8") as f:
            self.assertEqual(30, f.read().count("   datos: "))

    def test_solo_repite_si_no_queda_ningun_item_sin_usar(self):
        ruta = tsv_valido()
        filas, _ = muestra.comun.leer_tsv(ruta)
        for semilla in range(60):
            usados = set()
            for d in muestra.componer_detalle(ruta, n=30, semilla=semilla):
                esc = muestra.ESCENARIOS[d["escenario"]]
                if d["nucleo"] in usados:
                    libres = [f for f in filas if (f["bolsa"], f["indice"]) not in usados and muestra._elegible(f, esc)]
                    self.assertEqual([], libres, f"semilla {semilla}: repite {d['nucleo']}")
                usados.add(d["nucleo"])

    def test_avisa_si_no_llega_al_numero_pedido(self):
        f = tempfile.NamedTemporaryFile("w", suffix=".neutra.tsv", delete=False, encoding="utf-8")
        f.write(CAB + "nu_saludo\t0\t\tHola.\tHi.\n"); f.close()
        salida = tempfile.mkdtemp()
        with self.assertRaises(SystemExit):
            muestra.escribir(f.name, salida, minimos=False)
        self.assertEqual([], os.listdir(salida))

    def test_no_escribe_si_comprobar_falla(self):
        f = tempfile.NamedTemporaryFile("w", suffix=".neutra.tsv", delete=False, encoding="utf-8")
        f.write(CAB + "nu_saludo\t0\t\tHola — mal.\tHi.\n"); f.close()
        salida = tempfile.mkdtemp()
        with self.assertRaises(SystemExit):
            muestra.escribir(f.name, salida)
        self.assertEqual([], os.listdir(salida))


if __name__ == "__main__":
    unittest.main()
