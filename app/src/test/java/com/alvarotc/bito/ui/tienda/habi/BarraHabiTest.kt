package com.alvarotc.bito.ui.tienda.habi

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.ui.tienda.AccionesGrabadas
import com.alvarotc.bito.ui.tienda.BarrasTienda
import com.alvarotc.bito.ui.tienda.PanelFalso
import com.alvarotc.bito.ui.tienda.PruebaTienda
import com.alvarotc.bito.ui.tienda.TiendaContenido
import com.alvarotc.bito.ui.tienda.estadoTienda
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Locale

private val OCTUBRE = LocalDate.of(2025, 10, 20)

/** Monta la tienda en la pestaña Habi con la barra que decide `BarrasTienda` en el idioma pedido. */
internal class BancoBarra(
    private val rule: ComposeContentTestRule,
    private val idioma: String,
) {
    val acciones = AccionesGrabadas()
    private val cfg =
        Configuration(ApplicationProvider.getApplicationContext<Context>().resources.configuration).apply {
            setLocale(Locale.forLanguageTag(idioma))
        }
    private val ctx = ApplicationProvider.getApplicationContext<Context>().createConfigurationContext(cfg)
    val res: Resources get() = ctx.resources

    fun montar(
        prueba: PruebaTienda,
        saldo: Int,
        poseidos: Set<String> = emptySet(),
        mejorRacha: Int = 0,
        hoy: LocalDate = OCTUBRE,
    ) {
        val barra = BarrasTienda.barraDe(prueba, poseidos, saldo, mejorRacha, hoy, res)
        val estado = estadoTienda(prueba = prueba, barra = barra, saldo = saldo).copy(poseidos = poseidos, hoy = hoy)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides cfg) {
                ConSistemaLigero {
                    TiendaContenido(estado, acciones, emptyFlow(), alSalir = {}, paneles = { PanelFalso(it.name) })
                }
            }
        }
        rule.waitForIdle()
    }

    fun nota(texto: String) = rule.onNodeWithTag("barra-nota").assertIsDisplayedWith(texto)

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsDisplayedWith(texto: String) {
        assertIsDisplayed()
        val real =
            fetchSemanticsNode().config
                .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                ?.joinToString("") { it.text }
        assertEquals(texto, real)
    }

    /** Monta la tienda entera con el panel real de Habi, para leer los textos de los botones. */
    fun montarReal(
        prueba: PruebaTienda,
        saldo: Int,
    ) {
        val barra = BarrasTienda.barraDe(prueba, emptySet(), saldo, 0, OCTUBRE, res)
        val estado = estadoTienda(prueba = prueba, barra = barra, saldo = saldo).copy(hoy = OCTUBRE)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides cfg) {
                ConSistemaLigero { TiendaContenido(estado, acciones, emptyFlow(), alSalir = {}) }
            }
        }
        rule.waitForIdle()
    }

    fun texto(tag: String): String? =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            ?.joinToString("") { it.text }

    fun hay(tag: String) = rule.onNodeWithTag(tag).assertIsDisplayed()

    fun noHay(tag: String) = rule.onNodeWithTag(tag).assertDoesNotExist()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarraHabiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val banco get() = BancoBarra(rule, "es")

    @Test
    fun `a piece with balance offers Buy for 60 and Not now`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("head-gorro-lana"), saldo = 140)
        b.nota("60 bitos. Te quedarían 80.")
        b.hay("barra-comprar")
        b.hay("barra-ahora-no")
        b.noHay("barra-quitar")
        rule.onNodeWithTag("barra-comprar").performClick()
        assertEquals(listOf("head-gorro-lana"), b.acciones.compras)
        rule.onNodeWithTag("barra-ahora-no").performClick()
        assertEquals(1, b.acciones.quitadas)
    }

    @Test
    fun `the buttons read exactly Comprar por 60, Ahora no and Quitárselo on the real panel`() {
        val b = banco
        b.montarReal(PruebaTienda.Pieza("head-gorro-lana"), saldo = 140)
        assertEquals("Comprar por 60", b.texto("barra-comprar"))
        assertEquals("Ahora no", b.texto("barra-ahora-no"))
    }

    @Test
    fun `Take it off reads Quitárselo on the real panel`() {
        val b = banco
        b.montarReal(PruebaTienda.Pieza("head-copa"), saldo = 50)
        assertEquals("Quitárselo", b.texto("barra-quitar"))
    }

    @Test
    fun `without balance there is only Take it off`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("head-copa"), saldo = 50)
        b.nota("Cuesta 120 bitos y tienes 50: te faltan 70. Cada hábito hecho da 1 y un día completo, 3 más.")
        b.noHay("barra-comprar")
        rule.onNodeWithTag("barra-quitar").performClick()
        assertEquals(1, b.acciones.quitadas)
        assertEquals(emptyList<String>(), b.acciones.compras)
    }

    @Test
    fun `a locked exclusive says its streak and has no buy button`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("head-corona"), saldo = 500, mejorRacha = 31)
        b.nota("Llega con una racha de 100 días. Tu mejor racha es de 31: te faltan 69. No se compra.")
        b.noHay("barra-comprar")
        b.hay("barra-quitar")
    }

    @Test
    fun `an exclusive earned has no bar`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("head-flor"), saldo = 500, poseidos = setOf("head-flor"), mejorRacha = 31)
        b.noHay("barra")
    }

    @Test
    fun `a costume in season offers Buy for 150 with the end date`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("costume-calabaza"), saldo = 210)
        b.nota("150 bitos. Te quedarían 60. A la venta hasta el 2 de noviembre; luego es tuyo todo el año.")
        b.hay("barra-comprar")
        b.hay("barra-ahora-no")
    }

    @Test
    fun `a costume out of season shows the window and only Take it off`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("costume-reno"), saldo = 500)
        b.nota("A la venta del 1 de diciembre al 6 de enero. Una vez comprado se queda todo el año.")
        b.noHay("barra-comprar")
        b.hay("barra-quitar")
    }

    @Test
    fun `the dinosaur out of season names the carnival window`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("costume-dinosaurio"), saldo = 500, hoy = LocalDate.of(2025, 6, 1))
        b.nota("A la venta en las dos semanas antes del Miércoles de Ceniza. Una vez comprado se queda todo el año.")
    }

    @Test
    fun `a costume bought out of season has no bar`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("costume-reno"), saldo = 500, poseidos = setOf("costume-reno"))
        b.noHay("barra")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarraHabiInglesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val banco get() = BancoBarra(rule, "en")

    @Test
    fun `a piece with balance in English`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("head-gorro-lana"), saldo = 140)
        b.nota("60 bitos. You'd have 80 left.")
        b.hay("barra-comprar")
        b.hay("barra-ahora-no")
    }

    @Test
    fun `the buttons read exactly Buy for 60, Not now and Take it off on the real panel`() {
        val b = banco
        b.montarReal(PruebaTienda.Pieza("head-gorro-lana"), saldo = 140)
        assertEquals("Buy for 60", b.texto("barra-comprar"))
        assertEquals("Not now", b.texto("barra-ahora-no"))
    }

    @Test
    fun `Take it off reads exactly that on the real panel in English`() {
        val b = banco
        b.montarReal(PruebaTienda.Pieza("head-copa"), saldo = 50)
        assertEquals("Take it off", b.texto("barra-quitar"))
    }

    @Test
    fun `without balance in English`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("head-copa"), saldo = 50)
        b.nota("It costs 120 bitos and you have 50: 70 short. Each habit done earns 1, and a perfect day earns 3 more.")
        b.noHay("barra-comprar")
    }

    @Test
    fun `a locked exclusive in English`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("head-corona"), saldo = 500, mejorRacha = 31)
        b.nota("It comes with a 100-day streak. Your best is 31: 69 to go. Not for sale.")
    }

    @Test
    fun `a costume in season in English`() {
        val b = banco
        b.montar(PruebaTienda.Pieza("costume-calabaza"), saldo = 210)
        b.nota("150 bitos. You'd have 60 left. On sale until November 2; after that it's yours all year.")
    }
}
