package com.alvarotc.bito.ui.sesion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Los textos fijos de la sesión tal y como quedan en los recursos. JVM puro. */
class TextosSesionTest {
    private val res = File("src/main/res")
    private val archivos =
        listOf("strings_sesion.xml", "strings_sesion_recorrido.xml", "strings_sesion_respirar.xml", "strings_sesion_fuera.xml")

    private fun cargar(
        carpeta: String,
        archivo: String,
    ): Map<String, String> {
        val f = File(res, "$carpeta/$archivo")
        assertTrue("falta ${f.path}", f.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val out = linkedMapOf<String, String>()
        for (tag in listOf("string", "plurals")) {
            val nodos = doc.getElementsByTagName(tag)
            for (i in 0 until nodos.length) {
                val n = nodos.item(i)
                out[n.attributes.getNamedItem("name").nodeValue] = n.textContent.replace("\\'", "'")
            }
        }
        return out
    }

    private fun todo(carpeta: String) = archivos.flatMap { cargar(carpeta, it).entries }.associate { it.key to it.value }

    @Test
    fun `values and values-es have the same keys in every sesion file`() {
        for (a in archivos) {
            assertEquals(a, cargar("values-es", a).keys, cargar("values", a).keys)
        }
        assertTrue(cargar("values", "strings_sesion.xml").size > 100)
    }

    @Test
    fun `no long dash in any text`() {
        for (c in listOf("values", "values-es")) {
            for ((k, v) in todo(c)) assertFalse("$c/$k lleva una raya larga", v.contains('—'))
        }
    }

    @Test
    fun `the three voices of the denied sheet are literal`() {
        val es = cargar("values-es", "strings_sesion.xml")
        assertEquals("Sin ese permiso no puedo silenciar el móvil durante la sesión. Lo demás funciona igual.", es["ses_dnd_no_neutra"])
        assertEquals(
            "Sin ese permiso no puedo silenciar el móvil mientras trabajas. ¡Pero la sesión sigue igual!",
            es["ses_dnd_no_cheerleader"],
        )
        assertEquals("Sin ese permiso el móvil puede sonar durante la sesión. La sesión sigue.", es["ses_dnd_no_sargento"])
        val en = cargar("values", "strings_sesion.xml")
        assertEquals(
            "Without that permission I can't silence your phone during the session. Everything else works the same.",
            en["ses_dnd_no_neutra"],
        )
    }

    @Test
    fun `fixed texts of the mock are literal in spanish`() {
        val es = cargar("values-es", "strings_sesion.xml")
        assertEquals("¿En qué te concentras?", es["ses_preparar_titulo"])
        assertEquals("Primer paso: %1\$s", es["ses_primer_paso"])
        assertEquals("%1\$s: ¿has acabado?", es["ses_has_acabado"])
        assertEquals("Ronda de %1\$d bloques hecha. Después, cerramos la sesión.", es["ses_ronda_hecha"])
        assertEquals("Listo: el móvil se silencia al empezar.", es["ses_aviso_dnd_listo"])
    }

    @Test
    fun `habi carries no gender agreement`() {
        val generos = Regex("\\b(Habi)\\b[^.!?]{0,60}\\b(content[oa]|list[oa]|cansad[oa]|orgullos[oa]|sol[oa])\\b", RegexOption.IGNORE_CASE)
        for (c in listOf("values", "values-es")) {
            for ((k, v) in todo(c)) assertFalse("$c/$k: $v", generos.containsMatchIn(v))
        }
    }

    @Test
    fun `the voice sentences carry no exclamation except the cheerleader`() {
        val es = cargar("values-es", "strings_sesion.xml")
        assertFalse(es.getValue("ses_dnd_no_neutra").contains('!'))
        assertFalse(es.getValue("ses_dnd_no_sargento").contains('!'))
    }
}
