package com.alvarotc.bito.ui.sesion.respirar

import android.content.Context
import android.os.PowerManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.ui.sesion.Encuadre
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class RespirarSueltaTest {
    @get:Rule
    val compose = nuevaRegla()

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var m: Montaje

    @Before
    fun preparar() {
        m = Montaje(carpeta)
    }

    @After
    fun cerrar() {
        m.cerrar()
    }

    private fun montar(preparar: Montaje.() -> Unit = {}) {
        m.preparar()
        m.montar(compose)
        esperar("r-empezar")
    }

    private fun esperar(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun existe(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun sesionesGuardadas() = runBlocking { m.estado.first().breathingSessions }

    private fun recuerdos() = runBlocking { m.db.hablaDao().eventosDesde(0).first() }

    private fun esperarGuardado(n: Int) {
        compose.waitUntil(5_000) { sesionesGuardadas().size >= n }
    }

    private fun empezar() {
        compose.onNodeWithTag("r-empezar").performClick()
        esperar("s-parar")
    }

    private fun respirarHastaElFin(ms: Long) {
        empezar()
        m.avanzar(compose, ms)
        esperar("a-saltar")
    }

    @Test
    fun `choose shows the exact texts and tags and no balance`() {
        montar()
        compose.onNodeWithText("Respirar").assertIsDisplayed()
        compose.onNodeWithTag("r-modo").assertIsDisplayed()
        compose.onNodeWithText("Calmarme").assertIsDisplayed()
        compose.onNodeWithText("Dormir").assertIsDisplayed()
        compose.onNodeWithText("Centrarme").assertIsDisplayed()
        compose.onNodeWithTag("r-ritmo").assertIsDisplayed()
        compose.onNodeWithText("4 dentro · 6 fuera · 2 min").assertIsDisplayed()
        compose.onNodeWithText("Música de fondo").assertIsDisplayed()
        compose.onNodeWithText("Sube al inhalar, se queda al mantener y baja al exhalar").assertIsDisplayed()
        compose.onNodeWithTag("r-mus").assertIsDisplayed()
        compose.onNodeWithTag("r-empezar").assertIsDisplayed()
        esperar("r-semana")
        compose.onNodeWithTag("r-semana").assertTextEquals("Esta semana: 0 sesiones · 0 min")
        assertEquals(Encuadre.SUELTA_ELEGIR, m.camara.destino)
        // Saldo: ninguno. Música de la cabecera: solo en marcha.
        assertTrue(existe("ses-atras"))
        assertFalse(existe("ses-saldo"))
        assertFalse(existe("ses-musica"))
    }

    @Test
    fun `rhythm rolls with each mode`() {
        montar()
        compose.onNodeWithText("Dormir").performClick()
        m.avanzar(compose, 400)
        compose.onNodeWithText("4 dentro · 7 mantén · 8 fuera · 6 ciclos").assertIsDisplayed()
        compose.onNodeWithText("Centrarme").performClick()
        m.avanzar(compose, 400)
        compose.onNodeWithText("4 dentro · 4 mantén · 4 fuera · 4 mantén · 2 min").assertIsDisplayed()
        compose.onNodeWithText("Calmarme").performClick()
        m.avanzar(compose, 400)
        compose.onNodeWithText("4 dentro · 6 fuera · 2 min").assertIsDisplayed()
        // Elegir un modo no escribe nada hasta que se empieza.
        assertEquals(BreathingMode.CALM, m.ajustes().breathingLastMode)
    }

    @Test
    fun `music switch saves the setting`() {
        montar()
        assertFalse(m.ajustes().breathingMusicEnabled)
        compose.onNodeWithTag("r-mus").performClick()
        compose.waitUntil(5_000) { m.ajustes().breathingMusicEnabled }
        compose.onNodeWithTag("r-mus").performClick()
        compose.waitUntil(5_000) { !m.ajustes().breathingMusicEnabled }
    }

    @Test
    fun `start confirms, saves the mode, drives the air and shows the running state`() {
        montar()
        compose.onNodeWithText("Dormir").performClick()
        empezar()
        assertEquals(listOf(Haptica.SEG, Haptica.CONFIRM), m.tacto.hapticas.filter { it == Haptica.SEG || it == Haptica.CONFIRM })
        assertEquals(1, m.aire.arranques.size)
        val a = m.aire.arranques.single()
        assertEquals(BreathingMode.SLEEP, a.modo)
        assertEquals(6, a.ciclos)
        assertEquals(m.elapsed.get(), a.ancla)
        assertEquals(Encuadre.SUELTA_MARCHA, m.camara.destino)
        compose.waitUntil(5_000) { m.ajustes().breathingLastMode == BreathingMode.SLEEP }
        compose.onNodeWithTag("s-fase", useUnmergedTree = true).assertIsDisplayed()
        assertFalse(existe("e-fase"))
        assertFalse(existe("e-dots"))
        compose.onNodeWithTag("s-ciclo", useUnmergedTree = true).assertTextEquals("Ciclo 1 de 6. Quedan ")
        compose.onNodeWithTag("s-resta", useUnmergedTree = true).assertTextEquals("1:54")
        compose.onNodeWithText("Parar").assertIsDisplayed()
        assertNotNull(m.respiracion.value)
        assertTrue(existe("ses-musica"))
        assertFalse(existe("ses-saldo"))
        compose.activity.window.attributes.flags.let { assertTrue(it and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) }
    }

    @Test
    fun `starting free breathing asks to stay alive and stopping releases it`() {
        montar()
        empezar()
        assertEquals(listOf(true to m.aire.arranques.single().ancla + 120_000L), m.fuera.llamadas)
        m.avanzar(compose, 12_000)
        compose.onNodeWithTag("s-parar").performClick()
        assertEquals(listOf(true, false), m.fuera.llamadas.map { it.first })
        assertEquals(null, m.fuera.llamadas.last().second)
        assertEquals(1, m.aire.paradas)
        esperarGuardado(1)
    }

    @Test
    fun `the clock counts down from the anchor and the cycle advances`() {
        montar()
        empezar()
        m.avanzar(compose, 10_000)
        compose.onNodeWithTag("s-ciclo", useUnmergedTree = true).assertTextEquals("Ciclo 2 de 12. Quedan ")
        compose.onNodeWithTag("s-resta", useUnmergedTree = true).assertTextEquals("1:50")
        m.avanzar(compose, 100)
        compose.onNodeWithTag("s-resta", useUnmergedTree = true).assertTextEquals("1:50")
    }

    @Test
    fun `Sleep's six cycles end in how are you`() {
        montar { runBlocking { settings.update { it.copy(breathingLastMode = BreathingMode.SLEEP) } } }
        compose.onNodeWithText("Dormir").assertIsDisplayed()
        respirarHastaElFin(114_000)
        compose.onNodeWithText("¿Cómo estás ahora?").assertIsDisplayed()
        assertEquals(Encuadre.COMO_ESTAS, m.camara.destino)
        esperarGuardado(1)
        val s = sesionesGuardadas().single()
        assertTrue(s.completed)
        assertEquals(114, s.durationSeconds)
        assertEquals(BreathingMode.SLEEP, s.mode)
        assertEquals(false, m.fuera.llamadas.last().first)
        assertNull(m.respiracion.value)
        assertFalse(existe("ses-saldo"))
        assertFalse(existe("ses-musica"))
    }

    @Test
    fun `stop under 10 s goes back without saving`() {
        montar()
        empezar()
        m.avanzar(compose, 9_000)
        compose.onNodeWithTag("s-parar").performClick()
        esperar("r-empezar")
        assertTrue(sesionesGuardadas().isEmpty())
        assertFalse(existe("a-saltar"))
        assertEquals(Encuadre.SUELTA_ELEGIR, m.camara.destino)
        assertEquals(1, m.aire.paradas)
    }

    @Test
    fun `stop over 10 s saves and asks`() {
        montar()
        empezar()
        m.avanzar(compose, 12_500)
        compose.onNodeWithTag("s-parar").performClick()
        esperar("a-saltar")
        esperarGuardado(1)
        val s = sesionesGuardadas().single()
        assertFalse(s.completed)
        assertEquals(12, s.durationSeconds)
        assertEquals(Haptica.LOW, m.tacto.hapticas.last { it == Haptica.LOW })
    }

    @Test
    fun `going to another app stops and saves`() {
        montar()
        empezar()
        m.avanzar(compose, 20_000)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitForIdle()
        esperarGuardado(1)
        assertEquals(20, sesionesGuardadas().single().durationSeconds)
        assertEquals(1, m.aire.paradas)
        assertEquals(false, m.fuera.llamadas.last().first)
    }

    @Test
    fun `turning the screen off keeps breathing and asks to stay alive`() {
        montar()
        empezar()
        m.avanzar(compose, 20_000)
        val energia = m.context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(energia).turnScreenOn(false)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitForIdle()
        assertEquals(0, m.aire.paradas)
        assertTrue(sesionesGuardadas().isEmpty())
        assertEquals(listOf(true), m.fuera.llamadas.map { it.first })
        assertNotNull(m.respiracion.value)
        // Sigue contando: al volver, lo que queda sale del ancla.
        m.avanzar(compose, 30_000)
        assertEquals(0, m.aire.paradas)
    }

    @Test
    fun `again starts without choosing`() {
        montar()
        respirarHastaElFin(120_000)
        compose.onNodeWithText("Saltar").performClick()
        esperar("sf-otra")
        compose.onNodeWithTag("sf-otra").performClick()
        esperar("s-parar")
        assertEquals(2, m.aire.arranques.size)
        assertEquals(BreathingMode.CALM, m.aire.arranques.last().modo)
        assertFalse(existe("r-empezar"))
        // «Otra vez» confirma igual que «Empezar» (`empezarSuelta()` del mock hace `B.haptica('confirm')`).
        assertEquals(2, m.tacto.hapticas.count { it == Haptica.CONFIRM })
    }

    @Test
    fun `Sleep at 7_5 s shows Mantén and Habi full on screen`() {
        montar { runBlocking { settings.update { it.copy(breathingLastMode = BreathingMode.SLEEP) } } }
        empezar()
        m.avanzar(compose, 7_500)
        compose.onNodeWithTag("s-fase").assertTextContains("Mantén", substring = true)
        assertEquals(1f, m.respiracion.value!!.invoke(), 0.001f)
    }

    @Test
    fun `no balance in any of these states`() {
        montar()
        assertFalse(existe("ses-saldo"))
        empezar()
        assertFalse(existe("ses-saldo"))
        m.avanzar(compose, 120_000)
        esperar("a-saltar")
        assertFalse(existe("ses-saldo"))
        compose.onNodeWithTag("a-saltar").performClick()
        esperar("sf-listo")
        assertFalse(existe("ses-saldo"))
    }

    @Test
    fun `back goes home from choose and from the end, stops while running and skips the question`() {
        montar()
        compose.onNodeWithTag("ses-atras").performClick()
        assertEquals(1, m.volvioAHoy)
        assertEquals(Encuadre.HOY, m.camara.destino)
        assertTrue(m.sonido.snds().contains(Snd.ASCENSO))
        empezar()
        m.avanzar(compose, 15_000)
        compose.onNodeWithTag("ses-atras").performClick()
        esperar("a-saltar")
        compose.onNodeWithTag("ses-atras").performClick()
        esperar("sf-listo")
        assertTrue(recuerdos().isEmpty())
    }

    @Test
    fun `the music button in the header follows the setting while running`() {
        montar()
        empezar()
        compose.onNodeWithTag("ses-musica").performClick()
        assertEquals(listOf(true), m.aire.musicas)
        compose.waitUntil(5_000) { m.ajustes().breathingMusicEnabled }
    }

    @Test
    fun `leaving the screen while running releases everything`() {
        montar()
        empezar()
        m.avanzar(compose, 15_000)
        compose.activityRule.scenario.close()
        assertEquals(1, m.aire.paradas)
        assertEquals(false, m.fuera.llamadas.last().first)
        assertNull(m.respiracion.value)
        esperarGuardado(1)
    }

    @Test
    fun `Habi stretches with the fill of the anchor clock`() {
        montar()
        empezar()
        assertEquals(0f, m.respiracion.value!!.invoke(), 0.001f)
        m.avanzar(compose, 2_000)
        // Inhalar 4 s con la curva (1 - cos(pi p)) / 2: a mitad, 0,5.
        assertEquals(0.5f, m.respiracion.value!!.invoke(), 0.02f)
        m.avanzar(compose, 2_000)
        assertEquals(1f, m.respiracion.value!!.invoke(), 0.02f)
    }

    @Test
    fun `the end lines carry no phase tags, so the context's phase is harmless`() {
        val catalogo = com.alvarotc.bito.data.habla.CatalogoRecursos { m.context.resources }
        val fases =
            setOf(
                com.alvarotc.bito.domain.habla.Etiqueta.DESPIERTA,
                com.alvarotc.bito.domain.habla.Etiqueta.ESPERANDO,
                com.alvarotc.bito.domain.habla.Etiqueta.DURMIENDO,
            )
        for (bolsa in listOf(
            com.alvarotc.bito.domain.habla.Bolsa.RE_FIN_RESPIRAR,
            com.alvarotc.bito.domain.habla.Bolsa.CE_MEJOR,
            com.alvarotc.bito.domain.habla.Bolsa.CE_IGUAL,
            com.alvarotc.bito.domain.habla.Bolsa.CE_CUESTA,
        )) {
            for (voz in com.alvarotc.bito.domain.model.Personality.entries) {
                val trozos = catalogo.trozos(bolsa, voz)
                assertTrue("$bolsa $voz vacía", trozos.isNotEmpty())
                assertTrue("$bolsa $voz lleva etiquetas de fase", trozos.none { t -> t.etiquetas.any { it in fases } })
            }
        }
    }

    /** El estilo con que se pinta el texto del botón con ese tag (su semántica fusiona la del texto). */
    private fun estiloDe(tag: String): androidx.compose.ui.text.TextStyle {
        val resultado = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(resultado)
        return resultado.single().layoutInput.style
    }

    @Test
    fun `Empezar and Listo are the dark mock pill`() {
        montar()
        assertEquals(16f, estiloDe("r-empezar").fontSize.value, 0f)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF4EAD8), estiloDe("r-empezar").color)

        empezar()
        m.avanzar(compose, 120_000)
        esperar("a-saltar")
        compose.onNodeWithTag("a-saltar").performClick()
        esperar("sf-listo")

        assertEquals(16f, estiloDe("sf-listo").fontSize.value, 0f)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF4EAD8), estiloDe("sf-listo").color)
    }
}
