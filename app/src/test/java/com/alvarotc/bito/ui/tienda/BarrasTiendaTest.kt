package com.alvarotc.bito.ui.tienda

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.alvarotc.bito.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BarrasTiendaTest {
    private fun recursos(idioma: String): Resources {
        val base = RuntimeEnvironment.getApplication() as Context
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(idioma))
        return base.createConfigurationContext(config).resources
    }

    private val es = recursos("es")
    private val en = recursos("en")
    private val octubre = LocalDate.of(2025, 10, 20)

    private fun barra(
        prueba: PruebaTienda?,
        poseidos: Set<String> = emptySet(),
        saldo: Int = 0,
        mejorRacha: Int = 0,
        hoy: LocalDate = octubre,
        res: Resources = es,
    ) = BarrasTienda.barraDe(prueba, poseidos, saldo, mejorRacha, hoy, res)

    /** La frase que la barra acaba mostrando, como la pinta `BarraPruebaView`. */
    private fun frase(
        barra: BarraPrueba?,
        res: Resources = es,
    ): String =
        when (barra) {
            is BarraPrueba.Comprar -> {
                val nota = barra.nota as TextoBarra.Plano?
                if (nota != null) {
                    res.getString(nota.res, *nota.args.toTypedArray())
                } else {
                    res.getString(R.string.store_bar_price, barra.precio, barra.quedan)
                }
            }
            is BarraPrueba.SinSaldo -> TextoPrecio.sinSaldo(res, barra.precio, barra.tienes)
            is BarraPrueba.NoSeCompra -> (barra.texto as TextoBarra.Plano).let { res.getString(it.res, *it.args.toTypedArray()) }
            null -> error("sin barra")
        }

    @Test
    fun `piece with balance`() {
        val b = barra(PruebaTienda.Pieza("head-gorro-lana"), saldo = 140)
        assertEquals(BarraPrueba.Comprar(es.getString(R.string.item_head_gorro_lana), 60, 80, null, false), b)
        assertEquals("60 bitos. Te quedarían 80.", frase(b))
    }

    @Test
    fun `no balance says how many are missing`() {
        val b = barra(PruebaTienda.Pieza("head-copa"), saldo = 50)
        assertEquals(BarraPrueba.SinSaldo(es.getString(R.string.item_head_copa), 120, 50, false), b)
        assertEquals(
            "Cuesta 120 bitos y tienes 50: te faltan 70. Cada hábito hecho da 1 y un día completo, 3 más.",
            frase(b),
        )
    }

    @Test
    fun `a garden object without balance says how many are missing`() {
        val b = barra(PruebaTienda.Objeto("garden-banco"), saldo = 52)
        assertEquals(BarraPrueba.SinSaldo(es.getString(R.string.store_garden_bench), 80, 52, true), b)
        assertEquals(
            "Cuesta 80 bitos y tienes 52: te faltan 28. Cada hábito hecho da 1 y un día completo, 3 más.",
            frase(b),
        )
    }

    @Test
    fun `a garden object with balance stays where you see it`() {
        val b = barra(PruebaTienda.Objeto("garden-banco"), saldo = 140)
        assertEquals("80 bitos. Te quedarían 60. Se queda donde lo ves.", frase(b))
        assertTrue((b as BarraPrueba.Comprar).objeto)
    }

    @Test
    fun `an owned garden object has no bar`() {
        assertNull(barra(PruebaTienda.Objeto("garden-banco"), poseidos = setOf("garden-banco"), saldo = 140))
    }

    @Test
    fun `a locked exclusive says its streak and how many are missing`() {
        val b = barra(PruebaTienda.Pieza("head-corona"), saldo = 500, mejorRacha = 31)
        assertEquals(
            BarraPrueba.NoSeCompra(
                es.getString(R.string.item_head_corona),
                MotivoNoSeCompra.RACHA,
                TextoBarra.Plano(R.string.store_bar_streak, listOf(100, 31, 69)),
                false,
            ),
            b,
        )
        assertEquals("Llega con una racha de 100 días. Tu mejor racha es de 31: te faltan 69. No se compra.", frase(b))
    }

    @Test
    fun `an exclusive earned has no bar`() {
        assertNull(barra(PruebaTienda.Pieza("head-flor"), poseidos = setOf("head-flor"), mejorRacha = 31))
    }

    @Test
    fun `a costume in season mentions the end date`() {
        val b = barra(PruebaTienda.Pieza("costume-calabaza"), saldo = 210)
        assertEquals(
            "150 bitos. Te quedarían 60. A la venta hasta el 2 de noviembre; luego es tuyo todo el año.",
            frase(b),
        )
        assertEquals(
            "150 bitos. You'd have 60 left. On sale until November 2; after that it's yours all year.",
            frase(barra(PruebaTienda.Pieza("costume-calabaza"), saldo = 210, res = en), en),
        )
    }

    @Test
    fun `a costume out of season mentions the window and cannot be bought`() {
        val reno = barra(PruebaTienda.Pieza("costume-reno"), saldo = 500)
        assertEquals(MotivoNoSeCompra.TEMPORADA, (reno as BarraPrueba.NoSeCompra).motivo)
        assertEquals("A la venta del 1 de diciembre al 6 de enero. Una vez comprado se queda todo el año.", frase(reno))
        assertEquals(
            "On sale from December 1 to January 6. Once bought, it's yours all year.",
            frase(barra(PruebaTienda.Pieza("costume-reno"), saldo = 500, res = en), en),
        )
        val calabaza = barra(PruebaTienda.Pieza("costume-calabaza"), saldo = 500, hoy = LocalDate.of(2025, 12, 1))
        assertEquals("A la venta del 15 de octubre al 2 de noviembre. Una vez comprado se queda todo el año.", frase(calabaza))
        val dino = barra(PruebaTienda.Pieza("costume-dinosaurio"), saldo = 500, hoy = LocalDate.of(2025, 6, 1))
        assertEquals("A la venta en las dos semanas antes del Miércoles de Ceniza. Una vez comprado se queda todo el año.", frase(dino))
    }

    @Test
    fun `a costume bought out of season has no bar`() {
        assertNull(barra(PruebaTienda.Pieza("costume-reno"), poseidos = setOf("costume-reno"), saldo = 500))
    }

    @Test
    fun `the carnival bar ends the day before Ash Wednesday`() {
        // Pascua 2025: 20 de abril. Miércoles de Ceniza: 5 de marzo. Última noche a la venta: 4 de marzo.
        val b = barra(PruebaTienda.Pieza("costume-dinosaurio"), saldo = 210, hoy = LocalDate.of(2025, 3, 1))
        assertEquals(
            "150 bitos. Te quedarían 60. A la venta hasta el 4 de marzo; luego es tuyo todo el año.",
            frase(b),
        )
    }

    @Test
    fun `a special cannot be bought and names its badge`() {
        val b = barra(PruebaTienda.Especial("streak-7"))
        assertEquals(
            BarraPrueba.NoSeCompra(
                "Farolillo",
                MotivoNoSeCompra.LOGRO,
                TextoBarra.Plano(R.string.store_bar_badge, listOf("Primera llama", "racha de 7 en cualquier hábito")),
                true,
            ),
            b,
        )
        assertEquals("Llega con el logro Primera llama: racha de 7 en cualquier hábito. No se compra.", frase(b))
    }

    @Test
    fun `the same sentence in both languages for no balance`() {
        val b = barra(PruebaTienda.Objeto("garden-banco"), saldo = 52)
        assertEquals(
            "It costs 80 bitos and you have 52: 28 short. Each habit done earns 1, and a perfect day earns 3 more.",
            frase(b, en),
        )
    }

    @Test
    fun `no trial no bar`() {
        assertNull(barra(null))
    }
}
