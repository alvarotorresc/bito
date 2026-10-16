package com.alvarotc.bito.ui.inicio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.CandidatoGordo
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.SubtituloGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Accion
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.garden.GardenScene
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.premios.TipoVeta
import com.alvarotc.bito.ui.premios.Veta
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.componentes.CalendarioGotas
import com.alvarotc.bito.ui.today.HojaLoGordo
import com.alvarotc.bito.ui.today.TarjetaHabi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContratosInicioTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the pill marks the tab that q selects and its tabs fire their callbacks`() {
        var q = 0f
        val pulsados = mutableListOf<String>()
        rule.setContent {
            ConSistema { Pildora({ q }, { pulsados += "hoy" }, { pulsados += "camino" }) }
        }
        rule.onNodeWithTag("pildora").assertIsDisplayed()
        rule.onNodeWithTag("pildora-hoy").assertIsSelected()
        rule.onNodeWithTag("pildora-camino").assertIsNotSelected().performClick()
        assertEquals(listOf("camino"), pulsados)
    }

    @Test
    fun `the header exposes balance and settings as tappable targets`() {
        val pulsados = mutableListOf<String>()
        rule.setContent {
            ConSistema { CabeceraHoy(20000, 42, { pulsados += "saldo" }, { pulsados += "ajustes" }) }
        }
        rule.onNodeWithTag("cabecera-saldo").performClick()
        rule.onNodeWithTag("cabecera-ajustes").performClick()
        assertEquals(listOf("saldo", "ajustes"), pulsados)
    }

    @Test
    fun `a vein shows its type tag and a proposal card shows only when there is one`() {
        var propuesta: Propuesta? = null
        rule.setContent {
            ConSistema {
                Veta(TipoVeta.DIA, "Día", "Frase", 5, { Box(Modifier.size(8.dp).testTag("arte")) })
                TarjetaHabi(propuesta, {})
            }
        }
        rule.onNodeWithTag("veta-DIA").assertIsDisplayed()
        rule.onNodeWithTag("arte").assertIsDisplayed()
        rule.onNodeWithTag("tarjeta-habi").assertDoesNotExist()
    }

    @Test
    fun `the habi card renders the proposal text`() {
        val p =
            Propuesta("p", TextoHabi(R.string.base_tab_hoy), listOf(Accion(Respuesta.VALE, TextoHabi(R.string.hoy_vale), principal = true)))
        rule.setContent { ConSistema { TarjetaHabi(p, {}) } }
        rule.onNodeWithTag("tarjeta-habi").assertIsDisplayed()
    }

    @Test
    fun `the calendar shows its month`() {
        rule.setContent {
            ConSistema {
                CalendarioGotas(YearMonth.of(2026, 10), emptyList(), YearMonth.of(2026, 1), YearMonth.of(2026, 10), {}, {}, {})
            }
        }
        rule.onNodeWithTag("calendario-gotas").assertIsDisplayed()
    }

    @Test
    fun `the shared edge without scopes does not throw nor change the size`() {
        rule.setContent { Box(Modifier.size(30.dp).cantoCompartido("h1").testTag("caja")) }
        rule.onNodeWithTag("caja").assertWidthIsEqualTo(30.dp)
    }

    private val candidatos =
        listOf(
            CandidatoGordo(RefGordo(TipoRefGordo.TAREA, "t1"), "Llamar al banco", SubtituloGordo.TareaHoy),
            CandidatoGordo(RefGordo(TipoRefGordo.HABITO, "h1"), "Leer", SubtituloGordo.Habito),
        )

    @Test
    fun `the big thing sheet composes nothing closed and saves the chosen when open`() {
        var abierta by mutableStateOf(false)
        val guardados = mutableListOf<List<RefGordo>>()
        val elegidos = listOf(RefGordo(TipoRefGordo.TAREA, "t1"))
        rule.setContent {
            ConSistema {
                HojaLoGordo(abierta, candidatos, sugerencia = null, elegidos = elegidos, alGuardar = { guardados += it }, alCerrar = {})
            }
        }
        rule.onNodeWithTag("hoja-gordo").assertDoesNotExist()

        abierta = true
        rule.waitForIdle()
        rule.onNodeWithTag("hoja-gordo").assertExists()
        rule.onNodeWithTag("hoja-gordo-guardar").fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        rule.waitForIdle()
        assertEquals(listOf(elegidos), guardados)
    }

    @Test
    fun `the garden takes the night as a lambda`() {
        rule.setContent {
            ConSistema {
                GardenScene(
                    GardenState.INITIAL,
                    HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
                    Modifier.width(400.dp).testTag("escena"),
                    animated = false,
                    noche = { 0f },
                )
            }
        }
        rule.onNodeWithTag("escena").assertIsDisplayed()
    }
}
