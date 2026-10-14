package com.alvarotc.bito.ui.inicio

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.height
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ScrollJardinTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoInicio

    @Before
    fun setUp() {
        banco = BancoInicio(tmp).also { it.preparar(habitos = 20) }
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun proporcion(): Float = rule.onNodeWithTag("jardin-alto").getBoundsInRoot().height.value / 891f

    @Test
    fun `scrolling the list shrinks the garden from 40 to 18 percent and coming back restores it`() {
        rule.setContent { ConSistema { banco.Inicio() } }
        rule.waitForIdle()
        assertTrue("al principio el 40 %: ${proporcion()}", proporcion() in 0.39f..0.41f)
        repeat(3) { rule.onNodeWithTag("inicio-hoy").performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.1f) } }
        rule.waitForIdle()
        assertTrue("encogido al 18 %: ${proporcion()}", proporcion() in 0.17f..0.19f)
        repeat(8) { rule.onNodeWithTag("inicio-hoy").performTouchInput { swipeDown(startY = height * 0.1f, endY = height * 0.7f) } }
        rule.waitForIdle()
        assertTrue("de vuelta al 40 %: ${proporcion()}", proporcion() in 0.39f..0.41f)
    }
}
