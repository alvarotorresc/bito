package com.alvarotc.bito.ui.premios

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.domain.EtiquetaMovimiento
import com.alvarotc.bito.domain.FechaMovimiento
import com.alvarotc.bito.domain.Movimiento
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
class HojaSaldoTest {
    @get:Rule val rule = createComposeRule()

    private val hoy = LocalDate.of(2025, 10, 13).toEpochDay().toInt()
    private val dia2Octubre = LocalDate.of(2025, 10, 2).toEpochDay().toInt()
    private var tienda = 0
    private var cerrada = 0

    private fun montar(
        movimientos: List<Movimiento> = emptyList(),
        economia: EconomyConfig = EconomyConfig(),
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema { HojaSaldo(true, 140, movimientos, economia, alIrTienda = { tienda++ }, alCerrar = { cerrada++ }) }
        }
        rule.mainClock.advanceTimeBy(600)
    }

    @Test
    fun `five movements with their labels and dates`() {
        montar(
            listOf(
                Movimiento("1", 1, EtiquetaMovimiento.Habito("Leer"), FechaMovimiento.HOY, hoy),
                Movimiento("2", 3, EtiquetaMovimiento.DiaCompleto, FechaMovimiento.AYER, hoy - 1),
                Movimiento("3", 5, EtiquetaMovimiento.Racha(7, null), FechaMovimiento.FECHA, dia2Octubre),
                Movimiento("4", -100, EtiquetaMovimiento.Congelador, FechaMovimiento.FECHA, dia2Octubre),
                Movimiento("5", 3, EtiquetaMovimiento.Tarea(null), FechaMovimiento.FECHA, dia2Octubre),
            ),
        )
        listOf("Leer", "Día completo", "Un congelador", "Tarea borrada", "hoy", "ayer", "el 2 de octubre", "−100", "+5").forEach {
            assertTrue("falta «$it»", rule.onAllNodesWithText(it, substring = true).fetchSemanticsNodes().isNotEmpty())
        }
        assertEquals("tres fechas «el 2 de octubre»", 3, rule.onAllNodesWithText("el 2 de octubre").fetchSemanticsNodes().size)
    }

    @Test
    fun `only the latest five movements are listed`() {
        montar(List(7) { Movimiento("$it", 1, EtiquetaMovimiento.Habito("Hábito $it"), FechaMovimiento.HOY, hoy) })
        rule.onNodeWithText("Hábito 4").assertExists()
        rule.onNodeWithText("Hábito 5").assertDoesNotExist()
    }

    @Test
    fun `rules take their numbers from the economy`() {
        montar(economia = EconomyConfig(perfectWeekPoints = 12, taskOnTimePoints = 9))
        rule.onNodeWithText("+12").assertExists()
        rule.onNodeWithText("(9 si es dentro de su plazo)", substring = true).assertExists()
    }

    @Test
    fun `go to the store closes and navigates after 160 ms, side by side with close`() {
        montar()
        val tiendaBotón = rule.onNodeWithTag("saldo-tienda")
        val cerrar = rule.onNodeWithTag("saldo-cerrar")
        assertEquals(tiendaBotón.getUnclippedBoundsInRoot().top, cerrar.getUnclippedBoundsInRoot().top)
        assertTrue(tiendaBotón.getUnclippedBoundsInRoot().right <= cerrar.getUnclippedBoundsInRoot().left)
        rule.onNodeWithText("Ir a la tienda").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals("el retardo de 160 ms aún no ha pasado", 0, tienda)
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, tienda)
        rule.mainClock.advanceTimeBy(400)
        assertEquals("la hoja avisa de que se cerró", 1, cerrada)
    }

    @Test
    fun `close closes without navigating`() {
        montar()
        rule.onNodeWithText("Cerrar").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(1, cerrada)
        assertEquals(0, tienda)
    }
}
