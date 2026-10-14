package com.alvarotc.bito.ui.today

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import com.alvarotc.bito.domain.RealHabits
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La entrada escalonada y el brote solo ocurren una vez: una fila que vuelve por scroll aparece ya entera. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h640dp")
class HoyListaEntradaTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var banco: BancoHoy

    @Before
    fun setUp() {
        banco = BancoHoy(rule, tmp)
        banco.preparar()
        banco.sembrar(*(1..15).map { RealHabits.makeBed.copy(id = "h$it", name = "Hábito $it") }.toTypedArray())
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun entrando(tag: String): Boolean? = rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(EntrandoKey)

    @Test
    fun `rows enter staggered on load and a row that scrolls out and back appears at once`() {
        banco.montar()
        banco.esperar(3000)
        assertEquals(false, entrando("fila-h1"))
        rule.onNodeWithTag("hoy-lista").performScrollToIndex(16)
        banco.esperar(50)
        rule.onNodeWithTag("hoy-lista").performScrollToIndex(0)
        rule.waitForIdle()
        assertTrue("fila-h1 no repite la entrada", entrando("fila-h1") == false)
    }

    @Test
    fun `the created row sprouts once and not again when it comes back`() {
        banco.montar(creado = "h15")
        banco.esperar(4000)
        rule.onNodeWithTag("hoy-lista").performScrollToIndex(0)
        banco.esperar(100)
        rule.onNodeWithTag("hoy-lista").performScrollToIndex(17)
        rule.waitForIdle()
        assertEquals("fila-h15 no vuelve a brotar", false, entrando("fila-h15"))
    }
}
