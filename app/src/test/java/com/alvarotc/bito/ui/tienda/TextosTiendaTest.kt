package com.alvarotc.bito.ui.tienda

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextosTiendaTest {
    private val claves =
        listOf(
            "store_title", "store_light", "store_light_day", "store_light_afternoon", "store_light_night",
            "store_tab_habi", "store_tab_garden", "store_tab_freezers",
            "store_slot_head", "store_slot_face", "store_slot_neck", "store_slot_feet", "store_slot_colors",
            "store_slot_eyes", "store_slot_costume", "store_slot_worn", "store_slot_count", "store_slot_covered",
            "store_label_worn", "store_label_owned", "store_label_trying", "store_label_covered",
            "store_label_streak", "store_label_season_october", "store_label_season_december",
            "store_label_season_carnival", "store_buy", "store_not_now", "store_take_off", "store_take_off_object",
            "store_garden_bubble_special", "store_bar_price", "store_bar_missing", "store_bar_streak",
            "store_bar_out_of_season", "store_bar_in_season", "store_bar_garden", "store_bar_badge",
            "store_season_long_dec", "store_season_carnival", "store_note_costumes", "store_note_eyes",
            "store_welcome", "store_slot_empty", "store_all_bought", "store_garden_for_you", "store_garden_hint1",
            "store_garden_badges", "store_garden_hint2", "store_garden_bench", "store_garden_can",
            "store_garden_in", "store_garden_bubble_bought",
            "store_freezer_title", "store_freezer_hint_neutra", "store_freezer_hint_sargento",
            "store_freezer_hint_cheerleader", "store_freezer_none", "store_freezer_more_desc", "store_freezer_buy",
            "store_freezer_buy_desc", "store_freezer_left", "store_freezer_missing", "store_freezer_bought",
            "store_freezer_undo", "store_reward_title", "store_reward_done",
            "store_reward_flower", "store_reward_flower_under", "store_reward_scarf", "store_reward_scarf_under",
            "store_reward_crown", "store_reward_crown_under", "store_reward_gold", "store_reward_gold_under",
            "store_reward_special", "store_reward_special_under",
        )

    private val ficheroEn = "src/main/res/values/strings_tienda.xml"
    private val ficheroEs = "src/main/res/values-es/strings_tienda.xml"

    private fun recursos(idioma: String): Resources {
        val base = RuntimeEnvironment.getApplication() as Context
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(idioma))
        return base.createConfigurationContext(config).resources
    }

    private fun Resources.str(nombre: String): String =
        getString(getIdentifier(nombre, "string", RuntimeEnvironment.getApplication().packageName))

    private fun valores(ruta: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(ruta))
        val nodos = doc.getElementsByTagName("string")
        return (0 until nodos.length).associate { i ->
            val e = nodos.item(i) as Element
            e.getAttribute("name") to e.textContent
        }
    }

    @Test
    fun `every store key exists in values and values-es and nothing else is in the file`() {
        val en = valores(ficheroEn)
        val es = valores(ficheroEs)
        assertEquals(claves.toSet(), en.keys)
        assertEquals(claves.toSet(), es.keys)
    }

    @Test
    fun `the Spanish literals match the mock`() {
        val res = recursos("es")
        assertEquals("Tienda de Habi", res.str("store_title"))
        assertEquals(
            "Cuesta %1\$d bitos y tienes %2\$d: te faltan %3\$d. Cada hábito hecho da 1 y un día completo, 3 más.",
            res.str("store_bar_missing"),
        )
        assertEquals("Sin nada puesto. Toca una pieza para probártela.", res.str("store_slot_empty"))
        assertEquals(
            "Ya tienes todo lo que se vende. Lo demás llega con las rachas.",
            res.str("store_all_bought"),
        )
        assertEquals("Un congelador", res.str("store_freezer_buy"))
        assertEquals("Deshacer", res.str("store_freezer_undo"))
    }

    @Test
    fun `the English literals match the mock`() {
        val res = recursos("en")
        assertEquals("Habi's store", res.str("store_title"))
        assertEquals(
            "It costs %1\$d bitos and you have %2\$d: %3\$d short. Each habit done earns 1, and a perfect day earns 3 more.",
            res.str("store_bar_missing"),
        )
        assertEquals("Afternoon", res.str("store_light_afternoon"))
        assertEquals("Wearing", res.str("store_label_worn"))
        assertEquals("Take it off", res.str("store_take_off"))
        assertEquals("Remove it", res.str("store_take_off_object"))
        assertEquals("Freezer bought", res.str("store_freezer_bought"))
    }

    @Test
    fun `no store text has an em dash, an emoji or a gendered word for Habi`() {
        val generoHabi = Regex("Habi\\S*[^.!?]*\\b(lista|listo|contenta|contento|sola|solo|feliz)\\b", RegexOption.IGNORE_CASE)
        listOf(valores(ficheroEn), valores(ficheroEs)).forEach { mapa ->
            mapa.values.forEach { texto ->
                assertFalse(texto, texto.contains('—'))
                assertFalse(texto, texto.codePoints().anyMatch { it in 0x2600..0x27BF || it in 0x1F300..0x1FAFF })
                assertFalse(texto, generoHabi.containsMatchIn(texto))
            }
        }
    }

    @Test
    fun `the freezer hints keep the exclamation rules`() {
        val es = valores(ficheroEs)
        assertFalse(es.getValue("store_freezer_hint_neutra").contains('!'))
        assertFalse(es.getValue("store_freezer_hint_sargento").contains('!'))
        val cheer = es.getValue("store_freezer_hint_cheerleader")
        assertEquals(1, cheer.count { it == '¡' })
        assertEquals(1, cheer.count { it == '!' })
        assertTrue(cheer.startsWith("¡"))
    }
}
