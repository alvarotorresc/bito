package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.After
import org.junit.Assert.assertEquals
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
class CompraCongeladorTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private val t by lazy { TiendaReal(rule, tmp.root) }

    @After
    fun cerrar() = t.cerrar()

    /** Toca «Un congelador» y avanza hasta que la moneda sale. */
    private fun comprarYDespegar() {
        t.comprarDeUnToque()
        t.avanzarHasta { t.vuelo.activas.isNotEmpty() }
    }

    /** Avanza hasta que la moneda llega; devuelve los ms que voló. */
    private fun volarHastaLlegar(): Int {
        var ms = 0
        while (t.vuelo.activas.isNotEmpty()) {
            check(ms < 1500) { "la moneda no llega" }
            t.avanzar(16)
            ms += 16
        }
        return ms
    }

    private fun centro(c: androidx.compose.ui.geometry.Rect) =
        androidx.compose.ui.geometry.Offset(
            (c.left + c.right) / 2,
            (c.top + c.bottom) / 2,
        )

    @Test
    fun `a purchase flies the coin 520 ms to the new slot, rolls the balance 240 ms, plays GASTAR and then CAMPANILLA`() {
        t.abrir(saldo = 140, congeladores = 2)
        t.comprarDeUnToque()
        t.avanzarHasta { t.vuelo.activas.isNotEmpty() }
        val moneda = t.vuelo.activas.single()
        assertEquals("sale con el gasto", listOf(Snd.GASTAR), t.sonido.snds())
        assertEquals(listOf(Haptica.CONFIRM), t.tacto.hapticas)

        t.avanzar(48)
        assertTrue("el saldo rueda: la cifra vieja y la nueva a la vez", t.textos("140").fetchSemanticsNodes().isNotEmpty())
        assertEquals(listOf(Snd.GASTAR), t.sonido.snds())

        val ms = 48 + volarHastaLlegar()
        assertTrue("vuela unos 520 ms y no $ms", ms in 496..560)
        assertEquals(listOf(Snd.GASTAR, Snd.CAMPANILLA), t.sonido.snds())
        assertEquals("la campanilla no vibra", listOf(Haptica.CONFIRM), t.tacto.hapticas)
        assertEquals(1, t.textos("40").fetchSemanticsNodes().size)
        assertEquals(0, t.textos("140").fetchSemanticsNodes().size)

        t.avanzar(400)
        assertEquals(3, t.copos().fetchSemanticsNodes().size)
        val tercero = centro(t.caja("tc-copo", 2))
        assertEquals("llega al hueco del copito nuevo (x)", tercero.x, moneda.destino.x, 2f)
        assertEquals("llega al hueco del copito nuevo (y)", tercero.y, moneda.destino.y, 2f)
    }

    @Test
    fun `Habi neither speaks nor jumps`() {
        t.abrir(saldo = 140, congeladores = 2)
        comprarYDespegar()
        volarHastaLlegar()
        t.avanzar(600)
        assertEquals("sin salto", 0, t.armario.cuenta)
        assertNull(t.armario.amplitud)
        assertEquals("solo el gasto y la campanilla", listOf(Snd.GASTAR, Snd.CAMPANILLA), t.sonido.snds())
    }

    @Test
    fun `the snowflake rises 16 dp from 0,9 turning from -30 to 0 in 320 ms`() {
        t.abrir(saldo = 140, congeladores = 2)
        comprarYDespegar()
        volarHastaLlegar()
        t.avanzar(16)
        val dp = rule.density.density
        val alto = t.caja("tc-copo", 2).height / dp
        assertTrue("arranca a 0,9 de escala: $alto dp", alto < 44f * 0.97f)
        val abajo = (centro(t.caja("tc-copo", 2)).y - centro(t.caja("tc-copo", 0)).y) / dp
        assertTrue("arranca más abajo que sus vecinos, hasta 16 dp: $abajo dp", abajo in 8f..16f)
        t.avanzar(48)
        val luego = (centro(t.caja("tc-copo", 2)).y - centro(t.caja("tc-copo", 0)).y) / dp
        assertTrue("sube: $luego dp", luego < abajo - 1f)
        t.avanzar(400)
        assertEquals(44f, t.caja("tc-copo", 2).height / dp, 0.5f)
        assertEquals(centro(t.caja("tc-copo", 0)).y, centro(t.caja("tc-copo", 2)).y, 0.5f)
    }

    @Test
    fun `the count rolls from 2 to 3`() {
        t.abrir(saldo = 140, congeladores = 2)
        comprarYDespegar()
        t.avanzar(200)
        assertEquals("durante el vuelo sigue en 2", 0, t.textos("3").fetchSemanticsNodes().size)
        assertEquals(2, t.copos().fetchSemanticsNodes().size)
        volarHastaLlegar()
        t.avanzar(64)
        assertEquals("rueda: el 2 se va y el 3 entra", 1, t.textos("3").fetchSemanticsNodes().size)
        assertEquals(1, t.textos("2").fetchSemanticsNodes().size)
        t.avanzar(300)
        assertEquals(0, t.textos("2").fetchSemanticsNodes().size)
    }

    @Test
    fun `the line relays to the no balance one and the button drops to 0,5 in 200 ms`() {
        t.abrir(saldo = 140, congeladores = 2)
        val lleno = t.colores.canto.toArgb()
        comprarYDespegar()
        t.avanzar(200)
        rule.onNodeWithTag("tc-linea").assertTextEquals("100 bitos. Te quedarían 40.")
        assertTrue("el botón sigue entero hasta que llega", cerca(lleno, t.pixel(t.fotograma(), "tc-buy", 186f, 6f), 8))
        volarHastaLlegar()
        t.avanzar(100)
        val mitad = t.pixel(t.fotograma(), "tc-buy", 186f, 6f)
        assertTrue("a medias del 200 ms", !cerca(lleno, mitad, 8))
        t.avanzar(400)
        rule.onNodeWithTag("tc-linea").assertTextEquals("Te faltan 60 bitos. Cada hábito hecho da 1 y un día completo, 3 más.")
        // El botón se apoya en la veta fría, no en blanco: a 0,5 es la mitad de su color y la mitad de lo que tiene detrás.
        val foto = t.fotograma()
        val medio = t.pixel(foto, "tc-buy", 186f, 6f)
        val fondo = t.pixel(foto, "tc-buy", -8f, 30f)
        val esperado =
            android.graphics.Color.rgb(
                mezcla(lleno shr 16 and 255, fondo shr 16 and 255),
                mezcla(lleno shr 8 and 255, fondo shr 8 and 255),
                mezcla(lleno and 255, fondo and 255),
            )
        assertTrue("a 0,5: esperado ${Integer.toHexString(esperado)} y es ${Integer.toHexString(medio)}", cerca(esperado, medio, 10))
    }

    private fun mezcla(
        a: Int,
        b: Int,
    ) = (a + b) / 2

    @Test
    fun `buying the eighth turns the seventh into +2 and the pill settles 0,94 to 1 in 160 ms`() {
        t.abrir(saldo = 140, congeladores = 7)
        assertEquals(7, t.copos().fetchSemanticsNodes().size)
        comprarYDespegar()
        val moneda = t.vuelo.activas.single()
        volarHastaLlegar()
        t.avanzar(16)
        assertEquals(6, t.copos().fetchSemanticsNodes().size)
        assertEquals(1, t.textos("+2").fetchSemanticsNodes().size)
        val primera = t.caja("tc-mas").height / rule.density.density
        assertTrue("la pastilla arranca a 0,94: $primera dp", primera < 44f * 0.97f)
        assertEquals("la moneda iba al hueco del séptimo", centro(t.caja("tc-mas")).x, moneda.destino.x, 2f)
        t.avanzar(220)
        assertEquals(44f, t.caja("tc-mas").height / rule.density.density, 0.5f)
    }

    @Test
    fun `with eleven the coin flies to the pill and +5 rolls to +6`() {
        t.abrir(saldo = 140, congeladores = 11)
        assertEquals(1, t.textos("+5").fetchSemanticsNodes().size)
        comprarYDespegar()
        val moneda = t.vuelo.activas.single()
        t.avanzar(200)
        assertEquals("durante el vuelo sigue en +5", 0, t.textos("+6").fetchSemanticsNodes().size)
        volarHastaLlegar()
        t.avanzar(64)
        assertEquals("rueda +5 a +6", 1, t.textos("+6").fetchSemanticsNodes().size)
        assertEquals(1, t.textos("+5").fetchSemanticsNodes().size)
        assertEquals(centro(t.caja("tc-mas")).x, moneda.destino.x, 2f)
        assertEquals(centro(t.caja("tc-mas")).y, moneda.destino.y, 2f)
        t.avanzar(300)
        assertEquals(0, t.textos("+5").fetchSemanticsNodes().size)
    }

    @Test
    fun `a double tap pays once`() {
        t.abrir(saldo = 340, congeladores = 0)
        comprarYDespegar()
        t.avanzar(100)
        rule.onNodeWithTag("tc-buy").performClick()
        t.avanzar(60)
        rule.onNodeWithTag("tc-buy").performClick()
        t.avanzar(1500)
        assertEquals("un solo cobro", 1, t.compradas())
        assertEquals(240, t.saldoEnLibro())
        assertEquals(listOf(Snd.GASTAR, Snd.CAMPANILLA), t.sonido.snds())
    }

    @Test
    fun `leaving mid-flight leaves no coin and the purchase stays final`() {
        t.abrir(saldo = 140, congeladores = 2)
        comprarYDespegar()
        t.avanzar(160)
        assertTrue(t.vuelo.activas.isNotEmpty())
        t.panelVisible = false
        t.avanzar(16)
        assertTrue("ninguna moneda a medias", t.vuelo.activas.isEmpty())
        t.avanzar(2000)
        assertTrue(t.vuelo.activas.isEmpty())
        assertNull("sin aviso", t.avisos.actual)
        assertEquals("la compra queda firme", 3, t.compradas())
        assertEquals(40, t.saldoEnLibro())
        assertTrue("sin campanilla de algo que no llegó", Snd.CAMPANILLA !in t.sonido.snds())
    }

    @Test
    fun `with exactly 100 the purchase works and the button goes to 0,5`() {
        t.abrir(saldo = 100, congeladores = 0)
        rule.onNodeWithTag("tc-linea").assertTextEquals("100 bitos. Te quedarían 0.")
        comprarYDespegar()
        volarHastaLlegar()
        t.avanzar(500)
        assertEquals(1, t.compradas())
        assertEquals(0, t.saldoEnLibro())
        rule.onNodeWithTag("tc-linea").assertTextEquals("Te faltan 100 bitos. Cada hábito hecho da 1 y un día completo, 3 más.")
        val lleno = t.colores.canto.toArgb()
        assertTrue("atenuado", !cerca(lleno, t.pixel(t.fotograma(), "tc-buy", 186f, 6f), 8))
    }

    @Test
    fun `reduced fades 150 ms without an arc`() {
        t.abrir(saldo = 140, congeladores = 2, reducido = true)
        comprarYDespegar()
        val moneda = t.vuelo.activas.single()
        t.avanzar(64)
        assertEquals("sin arco: siempre en el destino", moneda.destino.x, moneda.posicion(150f).x, 0.5f)
        assertEquals(moneda.destino.y, moneda.posicion(150f).y, 0.5f)
        val ms = 64 + volarHastaLlegar()
        assertTrue("se funde en unos 150 ms y no $ms", ms in 130..200)
        t.avanzar(16)
        val c = t.caja("tc-copo", 2)
        assertEquals("el copito no sube", t.caja("tc-copo", 0).top, c.top, 0.5f)
        assertEquals("ni se encoge", 44f, c.height / rule.density.density, 0.5f)
    }
}
