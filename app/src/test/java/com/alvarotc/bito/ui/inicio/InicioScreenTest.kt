package com.alvarotc.bito.ui.inicio

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class InicioScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoInicio
    private val contexto = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        banco = BancoInicio(tmp)
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun montar(
        reducido: Boolean = false,
        habitos: Int = 0,
        creado: String? = null,
    ) {
        banco.preparar(habitos)
        rule.setContent { ConSistema(reducido = reducido) { banco.Inicio(creado = creado) } }
        rule.waitForIdle()
    }

    private fun esperarAsentado() = rule.waitForIdle()

    private fun volverAHoy() {
        rule.onNodeWithTag("pildora-hoy").performClick()
        esperarAsentado()
    }

    @Test
    fun `starts on Today with the pill on Today`() {
        montar()
        rule.onNodeWithTag("pildora-hoy").assertIsSelected()
        rule.onNodeWithTag("inicio-hoy").assertExists()
        rule.onNodeWithTag("inicio-camino").assertDoesNotExist()
    }

    @Test
    fun `the pill opens Camino and goes back to Today`() {
        montar()
        rule.onNodeWithTag("pildora-camino").performClick()
        esperarAsentado()
        rule.onNodeWithTag("inicio-camino").assertExists()
        rule.onNodeWithTag("inicio-hoy").assertDoesNotExist()
        rule.onNodeWithTag("pildora-camino").assertIsSelected()
        volverAHoy()
        rule.onNodeWithTag("inicio-hoy").assertExists()
        rule.onNodeWithTag("inicio-camino").assertDoesNotExist()
    }

    @Test
    fun `your path link calls what it is given and carries its tag`() {
        var pulsado = 0
        rule.setContent { ConSistema { EnlaceTuCamino({ pulsado++ }) } }
        rule.onNodeWithTag("tu-camino").performClick()
        assertEquals(1, pulsado)
    }

    @Test
    fun `back on Camino returns to Today`() {
        montar()
        rule.onNodeWithTag("pildora-camino").performClick()
        esperarAsentado()
        rule.onNodeWithTag("inicio-camino").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        esperarAsentado()
        rule.onNodeWithTag("inicio-hoy").assertExists()
        rule.onNodeWithTag("pildora-hoy").assertIsSelected()
    }

    @Test
    fun `with reduced motion the change is instant after 150 ms`() {
        montar(reducido = true)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("pildora-camino").performClick()
        rule.mainClock.advanceTimeBy(160)
        rule.onNodeWithTag("inicio-camino").assertExists()
    }

    @Test
    fun `tapping the pill mid transition reverses without a jump`() {
        montar()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("pildora-camino").performClick()
        rule.mainClock.advanceTimeBy(120)
        rule.onNodeWithTag("pildora-hoy").performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithTag("inicio-hoy").assertExists()
        rule.onNodeWithTag("pildora-hoy").assertIsSelected()
    }

    @Test
    fun `Camino survives going to another route and coming back`() {
        banco.preparar()
        var pantalla by mutableStateOf("inicio")
        rule.setContent {
            ConSistema {
                val holder = rememberSaveableStateHolder()
                if (pantalla == "inicio") {
                    holder.SaveableStateProvider("inicio") { banco.Inicio() }
                } else {
                    holder.SaveableStateProvider("otra") { Box(Modifier.testTag("otra")) }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("pildora-camino").performClick()
        esperarAsentado()
        rule.onNodeWithTag("inicio-camino").assertExists()
        pantalla = "otra"
        esperarAsentado()
        rule.onNodeWithTag("otra").assertExists()
        rule.onNodeWithTag("inicio-camino").assertDoesNotExist()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            pantalla = "inicio"
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeByFrame()
        // Sin animar: en el primer fotograma ya es Camino y la lista no está.
        rule.onNodeWithTag("inicio-camino").assertExists()
        rule.onNodeWithTag("inicio-hoy").assertDoesNotExist()
        rule.onNodeWithTag("pildora-camino").assertIsSelected()
    }

    @Test
    fun `gear goes to settings, balance opens your bitos, plus opens the create sheet`() {
        montar()
        rule.onNodeWithTag("cabecera-ajustes").performClick()
        assertTrue(banco.llamadas.contains("ajustes"))
        rule.onNodeWithTag("cabecera-saldo").performClick()
        esperarAsentado()
        rule.onNodeWithTag("hoja-saldo").assertExists()
    }

    @Test
    fun `plus opens the create sheet and a habit navigates once the sheet is gone`() {
        montar()
        rule.onNodeWithTag("boton-crear").performClick()
        esperarAsentado()
        rule.onNodeWithTag("create-choice-sheet").assertExists()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText(contexto.getString(R.string.tasks2_crear_habito)).performClick()
        rule.mainClock.advanceTimeBy(40)
        assertTrue("no navega con la hoja aún cerrándose", banco.llamadas.none { it == "crearHabito" })
        rule.mainClock.advanceTimeBy(800)
        assertEquals(1, banco.llamadas.count { it == "crearHabito" })
    }

    @Test
    fun `the plus moves up while a notice shows`() {
        val avisos = AvisoEstado()
        banco.preparar()
        rule.setContent {
            ConSistema { CompositionLocalProvider(LocalAvisosInicio provides avisos) { banco.Inicio() } }
        }
        rule.waitForIdle()
        val antes = rule.onNodeWithTag("boton-crear").getBoundsInRoot().top
        val ambito = CoroutineScope(banco.dispatcher)
        ambito.launch { avisos.mostrar("Hecho") }
        rule.waitForIdle()
        val durante = rule.onNodeWithTag("boton-crear").getBoundsInRoot().top
        assertTrue("el + sube mientras hay aviso", durante < antes)
        banco.dispatcher.scheduler.advanceTimeBy(Duraciones.AVISO + 200L)
        rule.waitForIdle()
        assertEquals(antes.value, rule.onNodeWithTag("boton-crear").getBoundsInRoot().top.value, 0.5f)
    }

    private fun frase(): String {
        val spec = banco.hoy.uiState.value.spec
        return contexto.getString(HabiVoice.bubbleRes(spec.mood, spec.personality))
    }

    @Test
    fun `coming back from creating a habit shows Habi's line even after the signal is cleared`() {
        banco.preparar()
        banco.sembrar("make-bed", "Hacer la cama")
        var creado by mutableStateOf<String?>("make-bed")
        rule.setContent { ConSistema { banco.Inicio(creado = creado, alCreadoVisto = { creado = null }) } }
        rule.waitForIdle()
        rule.onNodeWithText(frase()).assertExists()
        creado = null
        rule.waitForIdle()
        rule.onNodeWithText(frase()).assertExists()
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(Duraciones.GLOBITO + 500L)
        rule.onNodeWithText(frase()).assertDoesNotExist()
    }

    @Test
    fun `a created id of a task that does not touch today says nothing`() {
        banco.preparar()
        banco.sembrar("make-bed", "Hacer la cama")
        rule.setContent { ConSistema { banco.Inicio(creado = "tarea-de-otro-dia") } }
        rule.waitForIdle()
        rule.onNodeWithText(frase()).assertDoesNotExist()
    }

    @Test
    fun `a created id that arrives while Today is loading still makes Habi talk`() {
        val pausado = StandardTestDispatcher(TestScope().testScheduler)
        banco.preparar(dispatcherVM = pausado)
        rule.setContent { ConSistema { banco.Inicio(creado = "make-bed") } }
        rule.waitForIdle()
        banco.sembrar("make-bed", "Hacer la cama")
        pausado.scheduler.advanceUntilIdle()
        rule.waitForIdle()
        rule.onNodeWithText(frase()).assertExists()
    }

    @Test
    fun `the progress of Camino fades the list before half and the stats after`() {
        assertEquals(1f, progresoCamino(0f).alfaLista, 0f)
        assertEquals(0f, progresoCamino(0.5f).alfaLista, 0f)
        assertEquals(0f, progresoCamino(0.5f).alfaEstadisticas, 0f)
        assertEquals(1f, progresoCamino(1f).alfaEstadisticas, 0f)
        assertEquals(0f, progresoCamino(0.55f).alfaTitulo, 0f)
        assertEquals(1f, progresoCamino(1f).alfaTitulo, 0f)
        assertEquals(0f, progresoCamino(0.25f).alfaCabecera, 0f)
    }

    @Test
    fun `the garden edge goes from 40 percent to 18 percent and then to the Camino height`() {
        val alto = 1000f
        val escena = 300f
        val extra = 150f
        assertEquals(400f, bordeJardin(0f, 0f, alto, escena, extra, 0f), 0.01f)
        assertEquals(180f, bordeJardin(0f, 1f, alto, escena, extra, 0f), 0.01f)
        assertEquals(600f, bordeJardin(1f, 0f, alto, escena, extra, 0f), 0.01f)
        // Con poco alto, el escenario más 150 dp manda sobre el 60 %.
        assertEquals(450f, bordeJardin(1f, 0f, 600f, escena, extra, 0f), 0.01f)
        assertEquals(1f, escalaJardin(1f, 0f, alto, escena), 0.001f)
        assertEquals(0.88f * (400f / 300f), escalaJardin(0f, 1f, alto, escena), 0.001f)
    }
}
