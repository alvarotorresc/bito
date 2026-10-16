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

def tsv_g(lineas):
    d = tempfile.mkdtemp()
    ruta = os.path.join(d, "G.neutra.tsv")
    with open(ruta, "w", encoding="utf-8") as f:
        f.write(CAB + "".join(l + "\n" for l in lineas))
    return ruta


def fila(bolsa, i, es, en=None):
    return f"{bolsa}\t{i}\t\t{es}\t{en or es}"


class SesionTest(unittest.TestCase):
    def fallos(self, lineas, minimos=False):
        return comprobar.comprobar([tsv_g(lineas)], minimos=minimos)

    def test_grupo_g_cuenta_minimos(self):
        f = comprobar.comprobar([tsv_g([fila("se_entrar", 0, "Aqui estoy.")])], minimos=True)
        self.assertTrue(any("falta la bolsa de_habito" in x for x in f), f)
        self.assertTrue(any("se_entrar tiene 1 y necesita 10" in x for x in f), f)

    def test_globito_de_noventa(self):
        self.assertTrue(any("longitud" in f for f in self.fallos([fila("se_entrar", 0, "a" * 95)])))
        self.assertEqual([], self.fallos([fila("se_entrar", 0, "a" * 85)]))
        self.assertEqual([], self.fallos([fila("se_entrar", 0, "a" * 90)]))

    def test_descanso_y_hecha_de_noventa(self):
        for b in ("de_habito", "de_habito_largo", "de_sin_pendientes", "re_fin_respirar",
                  "se_hecha_tarea", "se_hecha_habito"):
            self.assertTrue(any("longitud" in f for f in self.fallos([fila(b, 0, "a" * 95)])), b)

    def test_respuesta_de_cien(self):
        self.assertTrue(any("longitud" in f for f in self.fallos([fila("ce_mejor", 0, "a" * 101)])))
        self.assertEqual([], self.fallos([fila("ce_mejor", 0, "a" * 100)]))

    def test_hermano_sin_nombre(self):
        todos = [fila("re_fin_respirar", i, f"Ya esta {i}, {{nombre}}.", f"Done {i}, {{nombre}}.") for i in range(3)]
        self.assertTrue(any("hermano" in f for f in self.fallos(todos)))
        mixto = todos[:2] + [fila("re_fin_respirar", 2, "Ya esta 2.", "Done 2."),
                             fila("re_fin_respirar", 3, "Ya esta 3.", "Done 3.")]
        self.assertEqual([], self.fallos(mixto))
        de_mas = todos[:1] + [fila("re_fin_respirar", 1, "Ya esta 1.", "Done 1.")] + \
            [fila("re_fin_respirar", i, f"Ya esta {i}, {{nombre}}.", f"Done {i}, {{nombre}}.") for i in (2, 3)]
        self.assertTrue(any("hermano" in f for f in self.fallos(de_mas)))

    def test_hueco_modo(self):
        ok = fila("re_respirar_mejor", 0, "Tras {modo} sueles ir mejor.", "After {modo} you tend to feel better.")
        self.assertEqual([], self.fallos([ok]))
        mal = fila("re_respirar_mejor", 0, "Tras {habito} sueles ir mejor.", "After {habito} you tend to feel better.")
        self.assertTrue(any("no permitido" in f for f in self.fallos([mal])))


class BolsasTest(unittest.TestCase):
    def test_nombre_coincide_con_huecos(self):
        for b in comprobar.comun.cargar_bolsas().values():
            self.assertEqual(b["nombre"], "nombre" in b["huecos"], b["bolsa"])


if __name__ == "__main__":
    unittest.main()
