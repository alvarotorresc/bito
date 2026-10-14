package com.alvarotc.bito.ui.inicio

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
@Config(sdk = [35])
class PildoraTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoInicio

    @Before
    fun setUp() {
        banco = BancoInicio(tmp).also { it.preparar() }
    }

    @After
    fun tearDown() = banco.cerrar()

    @Test
    @Config(qualifiers = "es-w360dp-h640dp")
    fun `pill and plus fit at 360dp with font scale 1,3`() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                ConSistema { banco.Inicio() }
            }
        }
        rule.waitForIdle()
        val pildora = rule.onNodeWithTag("pildora").getBoundsInRoot()
        val mas = rule.onNodeWithTag("boton-crear").getBoundsInRoot()
        assertTrue("la píldora no pisa al +", pildora.right <= mas.left)
        assertTrue("el + no se sale", mas.right <= 360.dp)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the pill is centered when there is room and keeps its size`() {
        rule.setContent { ConSistema { banco.Inicio() } }
        rule.waitForIdle()
        val pildora = rule.onNodeWithTag("pildora").getBoundsInRoot()
        assertTrue(kotlin.math.abs((pildora.left + pildora.right).value / 2f - 411f / 2f) < 1f)
        assertTrue(kotlin.math.abs(pildora.width.value - 236f) < 1f)
        assertTrue(kotlin.math.abs(pildora.height.value - 56f) < 1f)
    }

    @Test
    @Config(qualifiers = "es-w360dp-h640dp")
    fun `header keeps balance and gear whole and tappable at 360dp with font scale 1,3`() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                ConSistema { banco.Inicio() }
            }
        }
        rule.waitForIdle()
        val saldo = rule.onNodeWithTag("cabecera-saldo")
        val ajustes = rule.onNodeWithTag("cabecera-ajustes")
        saldo.assertIsDisplayed()
        ajustes.assertIsDisplayed()
        val a = saldo.getBoundsInRoot()
        val b = ajustes.getBoundsInRoot()
        assertTrue("el saldo entero dentro de la ventana", a.left >= 0.dp && a.right <= b.left)
        assertTrue("el engranaje entero dentro de la ventana", b.right <= 360.dp)
        saldo.performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hoja-saldo").assertExists()
    }
}
