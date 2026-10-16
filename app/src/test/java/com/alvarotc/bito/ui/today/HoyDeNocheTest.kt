package com.alvarotc.bito.ui.today

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.domain.RealHabits
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h640dp")
class HoyDeNocheTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var banco: BancoHoy

    @Before
    fun setUp() {
        banco = BancoHoy(rule, tmp)
        banco.preparar()
        banco.sembrar(RealHabits.makeBed)
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun sellarHoy() = runBlocking { JournalRepository(banco.db).sealDay(banco.hoy, banco.fixedNow) }

    private fun esperarEstado(condicion: (TodayUiState) -> Boolean) {
        rule.waitUntil(5_000) { condicion(banco.vm.uiState.value) }
    }

    @Test
    fun `with the day sealed Habi sleeps with closed eyes and the cap`() {
        sellarHoy()
        banco.montar()
        esperarEstado { !it.loading && it.todaySealed }
        val spec = banco.vm.uiState.value.spec
        assertTrue(spec.closedEyes)
        assertTrue(spec.sleepCap)
    }

    @Test
    fun `an open day keeps Habi awake`() {
        banco.montar()
        esperarEstado { !it.loading }
        val spec = banco.vm.uiState.value.spec
        assertFalse(spec.closedEyes)
        assertFalse(spec.sleepCap)
    }

    @Test
    fun `touching Habi at night shows the sleeping notice`() {
        sellarHoy()
        banco.montar()
        esperarEstado { !it.loading && it.todaySealed }
        banco.vm.alTocarHabi()
        rule.waitUntil(5_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasText("Habi duerme", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Habi duerme", substring = true).assertIsDisplayed()
    }

    @Test
    fun `touching Habi at night ticks without sound`() {
        sellarHoy()
        banco.montar()
        esperarEstado { !it.loading && it.todaySealed }
        banco.tacto.hapticas.clear()
        banco.sonido.sonados.clear()
        banco.vm.alTocarHabi()
        banco.esperar()
        assertTrue(com.alvarotc.bito.ui.sistema.Haptica.TICK in banco.tacto.hapticas)
        assertTrue(banco.sonido.sonados.isEmpty())
    }

    @Test
    fun `touching Habi by day shows no sleeping notice`() {
        banco.montar()
        esperarEstado { !it.loading }
        banco.vm.alTocarHabi()
        banco.esperar()
        rule.onAllNodes(androidx.compose.ui.test.hasText("Habi duerme", substring = true))
            .assertCountEquals(0)
    }
}
