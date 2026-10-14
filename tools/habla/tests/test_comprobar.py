import os, sys, tempfile, unittest
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import comprobar

CAB = "bolsa\tindice\tetiquetas\tes\ten\n"

def tsv(lineas):
    f = tempfile.NamedTemporaryFile("w", suffix=".neutra.tsv", delete=False, encoding="utf-8")
    f.write(CAB + "".join(l + "\n" for l in lineas)); f.close()
    return f.name

class ComprobarTest(unittest.TestCase):
    def fallos(self, lineas, minimos=False):
        return comprobar.comprobar([tsv(lineas)], minimos=minimos)

    def test_un_item_valido_pasa(self):
        self.assertEqual([], self.fallos(["nu_ayer_escapo\t0\t\tAyer se quedó sin hacer: {habito}. Hoy son {minutos} min.\tYesterday slipped by: {habito}. Today it is {minutos} min."]))

    def test_neutra_sin_exclamacion(self):
        self.assertTrue(any("exclamación" in f for f in self.fallos(["nu_saludo\t0\t\tHola, {nombre}!\tHi, {nombre}."])))

    def test_huecos_distintos_entre_idiomas(self):
        self.assertTrue(any("huecos" in f for f in self.fallos(["nu_ayer_escapo\t0\t\t{habito}, hoy a las {hora}.\t{habito}, today."])))

    def test_hueco_no_permitido_en_la_bolsa(self):
        self.assertTrue(any("no permitido" in f for f in self.fallos(["ci_calma\t0\t\tCon calma, {habito}.\tEasy, {habito}."])))

    def test_genero_de_habi(self):
        self.assertTrue(any("género" in f for f in self.fallos(["nu_noche\t0\t\tEstoy cansado. Mañana más.\tTired. More tomorrow."])))

    def test_pronombre_en_ingles(self):
        self.assertTrue(any("género" in f for f in self.fallos(["nu_noche\t0\t\tHabi duerme.\tHe is asleep."])))

    def test_hueco_de_nombre_libre_mal_colocado(self):
        self.assertTrue(any("hueco libre" in f for f in self.fallos(["nu_ayer_escapo\t0\t\tAyer se te escapó la {habito}.\tYesterday the {habito} slipped."])))

    def test_hueco_tras_dos_puntos_entre_comillas_o_al_principio(self):
        ok = ["nu_ayer_escapo\t0\t\tAyer quedó «{habito}». Hoy, {minutos} min.\tYesterday “{habito}” waited. Today, {minutos} min.",
              "nu_ayer_escapo\t1\t[lunes]\t{habito}, hoy a las {hora}.\t{habito}, today at {hora}."]
        self.assertEqual([], self.fallos(ok))

    def test_plural_mal_formado(self):
        self.assertTrue(any("plural" in f for f in self.fallos(["nu_vuelta\t0\t\t{dias_fuera|día} fuera. Vuelve {habito}.\t{dias_fuera|day} away."])))

    def test_etiqueta_desconocida(self):
        self.assertTrue(any("etiqueta" in f for f in self.fallos(["nu_saludo\t0\t[lune]\tHola.\tHi."])))

    def test_longitud_con_huecos_de_veinte(self):
        largo = "{habito} " + "x" * 95
        self.assertTrue(any("longitud" in f for f in self.fallos([f"nu_ayer_escapo\t0\t\t{largo}\t{largo}"])))

    def test_minimo_por_bolsa(self):
        self.assertTrue(any("mínimo" in f for f in self.fallos(["nu_saludo\t0\t\tHola.\tHi."], minimos=True)))

    def test_raya_larga_y_emoji(self):
        f = self.fallos(["nu_saludo\t0\t\tHola — qué tal 🙂\tHi."])
        self.assertTrue(any("lista negra" in x for x in f))

if __name__ == "__main__":
    unittest.main()
