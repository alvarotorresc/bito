import os, sys, tempfile, unittest
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import muestra

CAB = "bolsa\tindice\tetiquetas\tes\ten\n"
NOMBRES = ["Estiramientos largos", "Ana"]


def tsv_valido():
    filas = []
    for i in range(8):
        filas.append(f"nu_saludo\t{i}\t\tHola, {{nombre}}. Llevas {{hechos}} de {{total}}.\tHi, {{nombre}}. {{hechos}} of {{total}} so far.")
    for i in range(8, 12):
        filas.append(f"nu_saludo\t{i}\t\tHoy llevas {{hechos}} de {{total}}.\tToday: {{hechos}} of {{total}}.")
    for i in range(10):
        filas.append(f"nu_todo_hecho\t{i}\t\tTodo hecho: {{n|bito|bitos}} para ti.\tAll done: {{n|bito|bitos}} for you.")
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

    def test_nombre_largo_y_sin_nombre(self):
        frases = muestra.componer(tsv_valido(), n=30, semilla=1)
        self.assertTrue(any("Estiramientos largos" in f for f in frases))
        self.assertTrue(any(not any(n in f for n in NOMBRES) for f in frases))

    def test_no_escribe_si_comprobar_falla(self):
        f = tempfile.NamedTemporaryFile("w", suffix=".neutra.tsv", delete=False, encoding="utf-8")
        f.write(CAB + "nu_saludo\t0\t\tHola — mal.\tHi.\n"); f.close()
        salida = tempfile.mkdtemp()
        with self.assertRaises(SystemExit):
            muestra.escribir(f.name, salida)
        self.assertEqual([], os.listdir(salida))


if __name__ == "__main__":
    unittest.main()
