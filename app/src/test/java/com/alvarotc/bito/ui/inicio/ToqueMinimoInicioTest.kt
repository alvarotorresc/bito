package com.alvarotc.bito.ui.inicio

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Medidas
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Con la medida real del texto (gráficos nativos): la zona pulsable no puede depender del alto de la línea. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ToqueMinimoInicioTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoInicio

    @Before
    fun setUp() {
        banco = BancoInicio(tmp)
    }

    @After
    fun tearDown() = banco.cerrar()

    @Test
    fun `the balance and your path link are at least 44 dp tall to tap`() {
        banco.preparar()
        rule.setContent { ConSistema { banco.Inicio() } }
        rule.waitForIdle()
        val saldo = rule.onNodeWithTag("cabecera-saldo").getBoundsInRoot().height
        val camino = rule.onNodeWithTag("tu-camino").getBoundsInRoot().height
        assertTrue("saldo: $saldo", saldo >= Medidas.toqueMinimo)
        assertTrue("Tu camino: $camino", camino >= Medidas.toqueMinimo)
    }
}
