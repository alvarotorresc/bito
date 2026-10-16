package com.alvarotc.bito.ui.cierre

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.settings.MarcaLibro
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.inicio.BancoInicio
import com.alvarotc.bito.ui.premios.Momento
import com.alvarotc.bito.ui.premios.TipoVeta
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * «Buenas noches» y la cadena del cierre (§7.5) con el reloj de pruebas de Compose, el cierre real y la cola real de
 * celebraciones. Nadie pinta las celebraciones aquí: el test las termina a mano, como lo haría `PremiosEnInicio`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class CadenaCierreTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoInicio
    private val noches = mutableIntStateOf(0)
    private val estado = EstadoCierre(Animatable(1f), Animatable(0f))

    @Before
    fun setUp() {
        banco = BancoInicio(tmp)
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun hay(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(tag: String) = rule.waitUntil(5_000) { hay(tag) }

    /** Como el Inicio con el cierre abierto: Hoy visible y la cola retenida. */
    private fun montar(reducido: Boolean = false) {
        banco.celebraciones.enHoy(true)
        // Sembrar un hábito desbloquea «primer hábito»: abrir el cierre lo da por visto y la cola respira.
        banco.celebraciones.retenerCierre(true)
        avanzarCola()
        rule.setContent {
            ConSistema(banco.sonido, banco.tacto, reducido) {
                CierreContenido(
                    estado,
                    banco.cierre,
                    alCerrar = {},
                    alBuenasNoches = { noches.intValue++ },
                    celebraciones = banco.celebraciones,
                )
            }
        }
        esperar("cierre-buenas-noches")
        rule.waitForIdle()
    }

    /** La cola de celebraciones respira en el reloj del banco, no en el de Compose. */
    private fun avanzarCola() {
        banco.dispatcher.scheduler.advanceTimeBy(1_000)
        banco.dispatcher.scheduler.runCurrent()
    }

    private val logroNuevo get() = banco.fixedNow + 1_000

    private fun logrosVistos() = runBlocking { banco.ajustes.settings.first().badgesSeenUntilMillis } >= logroNuevo

    private fun sellado() = banco.cierre.uiState.value.sellado

    private fun marca() = runBlocking { banco.ajustes.settings.first().celebratedLedgerMark }

    /** Lo que el sello celebrará: un día perfecto en el libro y un logro nuevo, guardados mientras el cierre está abierto. */
    private fun ganadoEstaNoche() =
        runBlocking {
            banco.db.badgeDao().insert(BadgeEntity("perfect-day-1", logroNuevo))
            banco.db.pointsLedgerDao().insert(
                pointsLedgerEntity("d", 3, PointsReason.PERFECT_DAY, "day:${banco.hoyLogico}", banco.hoyLogico, 10),
            )
        }

    private fun buenasNochesHastaLaCadena() {
        rule.onNodeWithTag("cierre-buenas-noches").performClick()
        rule.mainClock.advanceTimeBy(2_600)
        rule.waitUntil(5_000) { sellado() }
        esperar("cierre-hasta-manana")
    }

    @Test
    fun `good night darkens for 2400 ms and seals at 1300 ms`() {
        banco.preparar(habitos = 1)
        montar()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("cierre-buenas-noches").performClick()
        rule.mainClock.advanceTimeByFrame()
        assertTrue(Snd.ANOCHECER in banco.sonido.snds())
        assertTrue(Haptica.RISE in banco.tacto.hapticas)

        rule.mainClock.advanceTimeBy(1_100)
        rule.waitForIdle()
        assertFalse("a los 1,1 s aún no se ha sellado", sellado())
        assertTrue(estado.noche.value > 0f && estado.noche.value < 1f)

        rule.mainClock.advanceTimeBy(300)
        rule.waitUntil(5_000) { sellado() }
        rule.mainClock.advanceTimeBy(100)
        assertTrue("brota el sello", hay("cierre-sello"))
        assertFalse("«Hasta mañana» espera a que acabe de anochecer", hay("cierre-hasta-manana"))

        rule.mainClock.advanceTimeBy(1_200)
        rule.waitForIdle()
        assertEquals(1f, estado.noche.value, 0.001f)
        assertTrue(hay("cierre-hasta-manana"))
        assertEquals("aún no se vuelve a Hoy", 0, noches.intValue)
    }

    @Test
    fun `with reduced motion the night still takes 2400 ms`() {
        banco.preparar(habitos = 1)
        montar(reducido = true)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("cierre-buenas-noches").performClick()
        rule.mainClock.advanceTimeBy(1_200)
        assertTrue(estado.noche.value > 0f && estado.noche.value < 1f)
        rule.mainClock.advanceTimeBy(1_400)
        rule.waitForIdle()
        assertEquals(1f, estado.noche.value, 0.001f)
        assertTrue(Snd.ANOCHECER in banco.sonido.snds())
    }

    @Test
    fun `the chain is held until the night is over and then plays in order`() {
        banco.preparar(habitos = 1, marcaCero = true)
        montar()
        ganadoEstaNoche()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertNull("retenida mientras el cierre está abierto", banco.celebraciones.actual.value)

        buenasNochesHastaLaCadena()
        val dia = banco.celebraciones.actual.value as Momento.Veta
        assertEquals(TipoVeta.DIA, dia.tipo)
        banco.celebraciones.terminado(dia)
        avanzarCola()
        rule.waitUntil(5_000) { banco.celebraciones.actual.value is Momento.Logro }
    }

    @Test
    fun `a layer sprouts when its moment ends and keeps what the night won`() {
        banco.preparar(habitos = 1, marcaCero = true)
        montar()
        ganadoEstaNoche()
        rule.waitForIdle()
        buenasNochesHastaLaCadena()
        assertFalse("el logro aún no ha sonado", hay("cierre-sello-logros"))

        banco.celebraciones.terminado(banco.celebraciones.actual.value!!) // el día perfecto
        avanzarCola()
        rule.waitUntil(5_000) { banco.celebraciones.actual.value is Momento.Logro }
        val brotes = banco.sonido.snds().count { it == Snd.BROTE }
        banco.celebraciones.terminado(banco.celebraciones.actual.value!!) // el logro: ya visto en ajustes
        avanzarCola()
        esperar("cierre-sello-logros")
        assertTrue(banco.sonido.snds().count { it == Snd.BROTE } > brotes)
        rule.waitUntil(5_000) { logrosVistos() }
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        assertTrue("el sello guarda lo ganado aunque ya esté visto", hay("cierre-sello-logros"))
    }

    @Test
    fun `see you tomorrow in the middle of the chain notes the rest and goes back to Today`() {
        banco.preparar(habitos = 1, marcaCero = true)
        montar()
        ganadoEstaNoche()
        rule.waitForIdle()
        buenasNochesHastaLaCadena()
        assertTrue(banco.celebraciones.actual.value is Momento.Veta)

        rule.onNodeWithTag("cierre-hasta-manana").performClick()
        rule.waitForIdle()
        assertEquals(1, noches.intValue)
        assertTrue(Snd.AMBIENTE in banco.sonido.snds())
        assertTrue(Haptica.LOW in banco.tacto.hapticas)
        assertNull("lo que suena termina", banco.celebraciones.actual.value)
        banco.celebraciones.retenerCierre(false) // la cámara baja
        avanzarCola()
        rule.waitUntil(5_000) { logrosVistos() }
        val ultima = runBlocking { banco.db.pointsLedgerDao().all() }.maxOf { MarcaLibro(it.createdAtMillis, it.id) }
        rule.waitUntil(5_000) { marca() == ultima }
        assertNull("nada de lo apuntado sale en Hoy", banco.celebraciones.actual.value)
    }

    @Test
    fun `back once the day is sealed is see you tomorrow`() {
        banco.preparar(habitos = 1, marcaCero = true)
        montar()
        ganadoEstaNoche()
        rule.waitForIdle()
        buenasNochesHastaLaCadena()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { noches.intValue == 1 }
        rule.waitUntil(5_000) { logrosVistos() }
    }

    @Test
    fun `each moment has its layer in the seal`() {
        assertEquals(CapaSello.RACHAS, capaDe(Momento.Veta(TipoVeta.RACHA, 5, racha = 7, lote = 1)))
        assertEquals(CapaSello.LOGROS, capaDe(Momento.Logro("first-habit", 1L, 1)))
        assertEquals(CapaSello.RACHAS, capaDe(Momento.Etapa(0, false, 1)))
        assertEquals(CapaSello.RACHAS, capaDe(Momento.Especial(GardenSpecial.LANTERN, 1)))
        assertNull("el día perfecto ya está en el título", capaDe(Momento.Veta(TipoVeta.DIA, 3, dia = 1, lote = 1)))
        assertNull(capaDe(Momento.Monedas("fila:h0", 1, 1, 1)))
    }

    /**
     * Regresión C1 de la pieza 3: al arrancar en frío Hoy está en carga con el jardín de relleno
     * (`GardenState.INITIAL`); si contara como el anterior, el jardín de verdad (con su especial) se celebraría de nuevo.
     */
    @Test
    fun `a cold start after the close does not celebrate the garden again`() {
        val enCarga = StandardTestDispatcher()
        banco.preparar(habitos = 1, dispatcherVM = enCarga)
        val especial = GardenSpecial.entries.first()
        runBlocking { banco.db.badgeDao().insert(BadgeEntity(especial.badgeId, banco.fixedNow)) }
        rule.setContent { ConSistema(banco.sonido, banco.tacto) { banco.Inicio(enPrimerPlano = true) } }
        rule.waitForIdle()
        assertTrue("Hoy sigue en carga", banco.hoy.uiState.value.loading)

        enCarga.scheduler.advanceUntilIdle()
        rule.waitUntil(5_000) { !banco.hoy.uiState.value.loading }
        rule.waitForIdle()
        avanzarCola()
        assertTrue(especial in banco.hoy.uiState.value.garden.specials)
        val delJardin = banco.celebraciones.enCola.filter { it is Momento.Especial || it is Momento.Etapa }
        assertEquals(emptyList<Momento>(), delJardin)
    }
}
