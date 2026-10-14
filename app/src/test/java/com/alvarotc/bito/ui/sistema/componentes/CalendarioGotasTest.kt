package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.HeatmapDay
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class CalendarioGotasTest {
    @get:Rule
    val rule = createComposeRule()

    private val octubre = YearMonth.of(2026, 10)
    private val agosto = YearMonth.of(2026, 8)

    private fun dia(d: Int) = LocalDate.of(2026, 10, d).toEpochDay().toInt()

    /** Octubre de 2026 con hoy el 13: 3 y 8 congelados, el 5 sin hacer, el resto de lo pasado hecho. */
    private fun dias(): List<HeatmapDay> =
        (1..31).map { d ->
            val dot =
                when {
                    d == 3 || d == 8 -> DayDot.FROZEN
                    d == 5 -> DayDot.FAILED
                    d < 13 -> DayDot.FULFILLED
                    d == 13 -> DayDot.PENDING
                    else -> DayDot.OFF
                }
            HeatmapDay(dia(d), dot, isToday = d == 13)
        }

    @Test
    fun `months start on Monday and always have six rows`() {
        val celdas = CalendarioGotasLogica.celdas(YearMonth.of(2026, 10)) // 1 de octubre de 2026 es jueves
        assertEquals(42, celdas.size)
        assertEquals(listOf(null, null, null), celdas.take(3))
        assertEquals(LocalDate.of(2026, 10, 1), celdas[3])
        assertEquals(LocalDate.of(2026, 10, 31), celdas[33])
        assertNull(celdas[34])
        // Un mes que empieza en lunes (junio de 2026) no deja huecos antes.
        assertEquals(LocalDate.of(2026, 6, 1), CalendarioGotasLogica.celdas(YearMonth.of(2026, 6))[0])
    }

    @Test
    fun `five states by shape`() {
        assertEquals(EstadoGota.HECHO, CalendarioGotasLogica.estadoDe(HeatmapDay(1, DayDot.FULFILLED, false)))
        assertEquals(EstadoGota.HECHO, CalendarioGotasLogica.estadoDe(HeatmapDay(1, DayDot.ACTIVITY, false)))
        assertEquals(EstadoGota.SIN_HACER, CalendarioGotasLogica.estadoDe(HeatmapDay(1, DayDot.FAILED, false)))
        assertEquals(EstadoGota.SIN_HACER, CalendarioGotasLogica.estadoDe(HeatmapDay(1, DayDot.EMPTY, false)))
        assertEquals(EstadoGota.CONGELADO, CalendarioGotasLogica.estadoDe(HeatmapDay(1, DayDot.FROZEN, false)))
        assertEquals(EstadoGota.HOY, CalendarioGotasLogica.estadoDe(HeatmapDay(1, DayDot.PENDING, true)))
        assertEquals(EstadoGota.HOY, CalendarioGotasLogica.estadoDe(HeatmapDay(1, DayDot.FULFILLED, true)))
        assertEquals(EstadoGota.NUMERO, CalendarioGotasLogica.estadoDe(HeatmapDay(1, DayDot.OFF, false)))
        assertEquals(EstadoGota.NUMERO, CalendarioGotasLogica.estadoDe(HeatmapDay(1, DayDot.PAUSED, false)))
    }

    @Test
    fun `month wheel stops at the creation month and the current month`() {
        val creado = YearMonth.of(2026, 8)
        val actual = YearMonth.of(2026, 10)
        assertFalse(CalendarioGotasLogica.puedeAtras(creado, creado))
        assertTrue(CalendarioGotasLogica.puedeAtras(actual, creado))
        assertFalse(CalendarioGotasLogica.puedeDelante(actual, actual))
        assertTrue(CalendarioGotasLogica.puedeDelante(creado, actual))
    }

    @Test
    fun `arrows change month and stop at the limits`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        var mes by mutableStateOf(octubre)
        var atras = 0
        var delante = 0
        rule.setContent {
            ConSistema(sonido, tacto) {
                CalendarioGotas(
                    mes = mes,
                    dias = dias(),
                    primerMes = agosto,
                    ultimoMes = octubre,
                    alMesAnterior = {
                        atras++
                        mes = mes.minusMonths(1)
                    },
                    alMesSiguiente = { delante++ },
                    alTocarDia = {},
                )
            }
        }
        rule.onNodeWithTag("mes-siguiente").assertIsNotEnabled()
        rule.onNodeWithTag("mes-siguiente").performClick()
        assertEquals(0, delante)

        rule.onNodeWithTag("mes-anterior").performClick()
        assertEquals(1, atras)
        assertEquals(listOf(Snd.PASO_MES_ATRAS), sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)

        rule.onNodeWithTag("mes-anterior").performClick()
        assertEquals(2, atras)
        rule.waitForIdle()
        // En agosto (el mes de creación) la flecha de atrás ya no responde.
        rule.onNodeWithTag("mes-anterior").assertIsNotEnabled()
        rule.onNodeWithTag("mes-anterior").performClick()
        assertEquals(2, atras)
    }

    @Test
    fun `a past day calls back, a frozen one wobbles and does not`() {
        val tacto = TactoGrabado()
        val tocados = mutableListOf<Int>()
        rule.setContent {
            ConSistema(tacto = tacto) {
                CalendarioGotas(octubre, dias(), agosto, octubre, {}, {}, { tocados += it })
            }
        }
        rule.onNodeWithTag("dia-${dia(12)}").performClick()
        assertEquals(listOf(dia(12)), tocados)
        rule.onNodeWithTag("dia-${dia(13)}").performClick()
        assertEquals(listOf(dia(12), dia(13)), tocados)

        rule.onNodeWithTag("dia-${dia(8)}").performClick()
        assertEquals(2, tocados.size)
        assertEquals(listOf(Haptica.LOW), tacto.hapticas)

        // Un día por venir no es un botón.
        assertEquals(0, rule.onAllNodesWithTagCount("dia-${dia(20)}", clickable = true))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(
        tag: String,
        clickable: Boolean,
    ): Int {
        val nodos = onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes()
        return nodos.count { n ->
            clickable == n.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        }
    }

    @Test
    fun `a past pending day is drawn as a number but still calls back, a day off does not`() {
        val tocados = mutableListOf<Int>()
        val conPendiente = dias().map { if (it.day == dia(11)) it.copy(dot = DayDot.PENDING) else it }
        rule.setContent {
            ConSistema { CalendarioGotas(octubre, conPendiente, agosto, octubre, {}, {}, { tocados += it }) }
        }
        assertEquals(EstadoGota.NUMERO, CalendarioGotasLogica.estadoDe(conPendiente.first { it.day == dia(11) }))
        rule.onNodeWithTag("dia-${dia(11)}").performClick()
        assertEquals(listOf(dia(11)), tocados)
        rule.onNodeWithContentDescription("11 de octubre, pendiente").assertExists()
        // Los días en pausa y los que aún no existen no responden.
        assertFalse(CalendarioGotasLogica.respondeAlToque(HeatmapDay(1, DayDot.OFF, false)))
        assertFalse(CalendarioGotasLogica.respondeAlToque(HeatmapDay(1, DayDot.PAUSED, false)))
        assertTrue(CalendarioGotasLogica.respondeAlToque(HeatmapDay(1, DayDot.PENDING, false)))
    }

    @Test
    fun `not interactive ignores taps on days`() {
        val tocados = mutableListOf<Int>()
        var atras = 0
        rule.setContent {
            ConSistema {
                CalendarioGotas(octubre, dias(), agosto, octubre, { atras++ }, {}, { tocados += it }, interactivo = false)
            }
        }
        rule.onNodeWithTag("dia-${dia(12)}").performClick()
        rule.onNodeWithTag("dia-${dia(13)}").performClick()
        assertTrue(tocados.isEmpty())
        // Las flechas sí responden.
        rule.onNodeWithTag("mes-anterior").performClick()
        assertEquals(1, atras)
    }

    @Test
    fun `a swipe of more than 48 dp goes to the previous month`() {
        var atras = 0
        rule.setContent {
            ConSistema {
                CalendarioGotas(octubre, dias(), agosto, octubre, { atras++ }, {}, {})
            }
        }
        rule.onNodeWithTag("calendario-gotas").performTouchInput { swipeRight() }
        assertEquals(1, atras)
    }

    @Test
    fun `each day is described with its date and state`() {
        rule.setContent { ConSistema { CalendarioGotas(octubre, dias(), agosto, octubre, {}, {}, {}) } }
        assertNotNull(rule.onNodeWithContentDescription("12 de octubre, hecho").fetchSemanticsNode())
        rule.onNodeWithContentDescription("8 de octubre, congelado").assertExists()
        rule.onNodeWithContentDescription("5 de octubre, sin hacer").assertExists()
        rule.onNodeWithContentDescription("13 de octubre, hoy, sin marcar").assertExists()
    }
}
