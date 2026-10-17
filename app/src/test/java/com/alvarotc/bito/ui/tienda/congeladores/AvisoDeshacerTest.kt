package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.data.freezerUseEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvisoDeshacerTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private val t by lazy { TiendaReal(rule, tmp.root) }

    @After
    fun cerrar() = t.cerrar()

    /** Compra uno y deja que llegue el copito: el aviso sale en ese momento. */
    private fun comprarYAterrizar() {
        t.comprarDeUnToque()
        t.avanzarHasta { t.vuelo.activas.isNotEmpty() }
        t.avanzarHasta { t.vuelo.activas.isEmpty() }
        t.avanzarHasta(500) { t.avisos.actual != null }
    }

    @Test
    fun `the notice appears when the snowflake lands with the text and the action`() {
        t.abrir(saldo = 140, congeladores = 2)
        t.comprarDeUnToque()
        t.avanzarHasta { t.vuelo.activas.isNotEmpty() }
        t.avanzar(300)
        assertNull("durante el vuelo no hay aviso", t.avisos.actual)
        t.avanzarHasta { t.vuelo.activas.isEmpty() }
        t.avanzarHasta(500) { t.avisos.actual != null }
        assertEquals("Congelador comprado", t.avisos.actual?.texto)
        assertEquals("Deshacer", t.avisos.actual?.accion)
        t.avanzar(400)
        rule.onNodeWithTag("aviso").assertIsDisplayed()
        rule.onNodeWithText("Deshacer", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `it lasts 4000 ms`() {
        t.abrir(saldo = 140, congeladores = 2)
        comprarYAterrizar()
        t.avanzar(3900)
        assertNotNull(t.avisos.actual)
        t.avanzar(200)
        assertNull(t.avisos.actual)
    }

    @Test
    fun `undo returns the coins and the freezer once`() {
        t.abrir(saldo = 140, congeladores = 2)
        comprarYAterrizar()
        t.avanzar(400)
        assertEquals(3, t.compradas())
        assertEquals(40, t.saldoEnLibro())
        rule.onNodeWithText("Deshacer", useUnmergedTree = true).performClick()
        t.avanzar(600)
        t.vm.deshacerCongelador()
        t.avanzar(600)
        assertEquals("el congelador vuelve a la tienda", 2, t.compradas())
        assertEquals("y los bitos, una sola vez", 140, t.saldoEnLibro())
        assertEquals(2, t.copos().fetchSemanticsNodes().size)
    }

    @Test
    fun `the balance rolls back and the last snowflake fades 150 ms and plays HUNDIR and LOW`() {
        t.abrir(saldo = 140, congeladores = 2)
        comprarYAterrizar()
        t.avanzar(600)
        t.sonido.sonados.clear()
        t.tacto.hapticas.clear()
        val lleno = t.colores.hielo.toArgb()
        rule.onNodeWithText("Deshacer", useUnmergedTree = true).performClick()
        t.avanzarHasta { Snd.HUNDIR in t.sonido.snds() }
        assertEquals("el toque del aviso y la vuelta atrás", listOf(Haptica.TICK, Haptica.LOW), t.tacto.hapticas)
        assertEquals("el último copito aún está", 3, t.copos().fetchSemanticsNodes().size)
        t.avanzar(48)
        val foto = t.fotograma()
        val c = t.pixel(foto, "tc-copo", 6f, 22f, indice = 2)
        assertTrue("a medio fundido, ni entero ni blanco", !cerca(lleno, c, 6) && !cerca(android.graphics.Color.WHITE, c, 6))
        assertEquals("el saldo rueda de vuelta: la cifra vieja y la nueva", 1, t.textos("40").fetchSemanticsNodes().size)
        t.avanzar(300)
        assertEquals(2, t.copos().fetchSemanticsNodes().size)
        assertEquals(0, t.textos("40").fetchSemanticsNodes().size)
        assertEquals(listOf(Snd.BROTE, Snd.HUNDIR), t.sonido.snds())
    }

    @Test
    fun `a second purchase replaces the notice and undoing only returns the last`() {
        t.abrir(saldo = 340, congeladores = 0)
        comprarYAterrizar()
        t.avanzar(400)
        assertNotNull(t.avisos.actual)
        t.comprarDeUnToque()
        t.avanzar(16)
        assertNull("el aviso viejo se va al empezar la compra nueva", t.avisos.actual)
        t.avanzarHasta { t.vuelo.activas.isNotEmpty() }
        t.avanzarHasta { t.vuelo.activas.isEmpty() }
        t.avanzarHasta(500) { t.avisos.actual != null }
        t.avanzar(400)
        assertEquals(2, t.compradas())
        rule.onNodeWithText("Deshacer", useUnmergedTree = true).performClick()
        t.avanzar(800)
        assertEquals("solo se deshace la última", 1, t.compradas())
        assertEquals(240, t.saldoEnLibro())
        assertEquals(1, t.copos().fetchSemanticsNodes().size)
    }

    @Test
    fun `after the notice expires the purchase is final`() {
        t.abrir(saldo = 140, congeladores = 2)
        comprarYAterrizar()
        t.avanzar(4400)
        assertNull(t.avisos.actual)
        assertEquals(3, t.compradas())
        assertEquals(40, t.saldoEnLibro())
        assertEquals(3, t.copos().fetchSemanticsNodes().size)
    }

    @Test
    fun `undo after using that freezer is refused and says nothing wrong`() {
        t.abrir(saldo = 140, congeladores = 0)
        comprarYAterrizar()
        t.avanzar(400)
        t.ejecutar {
            t.db.habitDao().upsert(habitEntity(id = "h1"))
            t.db.freezerUseDao().insert(freezerUseEntity(id = "uso-1"))
        }
        t.sonido.sonados.clear()
        rule.onNodeWithText("Deshacer", useUnmergedTree = true).performClick()
        t.avanzar(3000)
        assertEquals("sigue comprado", 1, t.compradas())
        assertEquals(40, t.saldoEnLibro())
        assertFalse("sin hundir ni vibrar", Snd.HUNDIR in t.sonido.snds())
        assertTrue(t.tacto.hapticas.none { it == Haptica.LOW })
        assertEquals("el contador vuelve al dato real: usado, ya no queda ninguno", 0, t.copos().fetchSemanticsNodes().size)
    }
}
