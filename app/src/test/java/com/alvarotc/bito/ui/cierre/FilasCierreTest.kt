package com.alvarotc.bito.ui.cierre

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.HabitCardUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FilasCierreTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val eventos = mutableListOf<String>()

    private fun carta(
        kind: CardKind,
        direction: Direction = Direction.AT_LEAST,
        progress: Int = 0,
        target: Int = 1,
        step: Int = 1,
        failed: Boolean = false,
    ) = HabitCardUi("h", "Hábito", kind, progress, target, "vasos", step, direction, Period.DAY, false, failed, 0)

    private fun montar(
        card: HabitCardUi,
        decidida: Boolean = false,
    ) {
        rule.setContent {
            ConSistema(sonido, tacto) {
                FilaCierre(
                    card = card,
                    decidida = decidida,
                    alHecho = { eventos += "hecho" },
                    alSumar = { eventos += "sumar:$it" },
                    alFijar = { eventos += "fijar:$it" },
                    alExacto = { eventos += "exacto" },
                    alDecidir = { eventos += "decidir" },
                    alRecaer = { eventos += "recaer" },
                    alDeshacer = { eventos += "deshacer" },
                )
            }
        }
    }

    private fun hay(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a check has Done and I did not`() {
        montar(carta(CardKind.CHECK))
        rule.onNodeWithTag("cierre-hecho-h").assertIsDisplayed()
        rule.onNodeWithTag("cierre-no-h").assertIsDisplayed()
        rule.onNodeWithTag("cierre-hecho-h").performClick()
        rule.onNodeWithTag("cierre-no-h").performClick()
        assertEquals(listOf("hecho", "decidir"), eventos)
        assertEquals(listOf(Snd.GORGOTEO, Snd.HUNDIR), sonido.snds())
        assertEquals(listOf(Haptica.TICKS, Haptica.LOW), tacto.hapticas)
    }

    @Test
    fun `a counter adds its step, types the exact total or stays as it is`() {
        montar(carta(CardKind.COUNTER, progress = 6, target = 8, step = 2))
        rule.onNodeWithTag("cierre-mas-h").performClick()
        rule.onNodeWithTag("cierre-exacto-h").performClick()
        rule.onNodeWithTag("cierre-asi-h").performClick()
        assertEquals(listOf("sumar:2", "exacto", "decidir"), eventos)
        // 6 + 2 llega al objetivo: la gota; sin pasar de largo no hay otros botones de duración.
        assertEquals(Snd.GORGOTEO, sonido.snds().first())
        rule.onAllNodesWithTag("cierre-mas5-h").assertCountEquals(0)
        rule.onAllNodesWithTag("cierre-objetivo-h").assertCountEquals(0)
    }

    @Test
    fun `a duration also has plus 5, plus 15 and plus the target`() {
        montar(carta(CardKind.DURATION, progress = 0, target = 30, step = 10))
        rule.onNodeWithTag("cierre-mas5-h").performClick()
        rule.onNodeWithTag("cierre-mas15-h").performClick()
        rule.onNodeWithTag("cierre-objetivo-h").performClick()
        assertEquals(listOf("sumar:5", "sumar:15", "fijar:30"), eventos)
    }

    @Test
    fun `quitting for good has a clean day and a relapse that asks`() {
        montar(carta(CardKind.ABSTINENCE, Direction.ZERO, target = 0))
        rule.onNodeWithTag("cierre-limpio-h").performClick()
        rule.onNodeWithTag("cierre-recaido-h").performClick()
        assertEquals(listOf("decidir", "recaer"), eventos)
        assertEquals(Snd.GORGOTEO, sonido.snds().first())
    }

    @Test
    fun `a limit has plus one and within the limit`() {
        montar(carta(CardKind.COUNTER, Direction.AT_MOST, progress = 3, target = 3))
        rule.onNodeWithTag("cierre-mas-h").performClick()
        rule.onNodeWithTag("cierre-dentro-h").performClick()
        assertEquals(listOf("sumar:1", "decidir"), eventos)
        // Pasarse del límite hunde el agua sin rojo: vaciar y el golpe bajo.
        assertEquals(Snd.VACIAR, sonido.snds().first())
        assertEquals(Haptica.LOW, tacto.hapticas.first())
    }

    @Test
    fun `a decided row is dimmed and only offers to undo`() {
        montar(carta(CardKind.CHECK), decidida = true)
        rule.onNodeWithTag("cierre-atenuada-h").assertIsDisplayed()
        assertEquals(false, hay("cierre-hecho-h"))
        rule.onNodeWithTag("cierre-deshacer-h").performClick()
        assertEquals(listOf("deshacer"), eventos)
        assertEquals(listOf(Snd.BROTE), sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)
    }

    @Test
    fun `an undecided row is not dimmed`() {
        montar(carta(CardKind.CHECK))
        assertEquals(false, hay("cierre-atenuada-h"))
    }
}
