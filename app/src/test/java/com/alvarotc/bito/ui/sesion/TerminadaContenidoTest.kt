package com.alvarotc.bito.ui.sesion

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.habla.CatalogoRecursos
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.ResultadoSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.sesion.BancoSesion
import com.alvarotc.bito.sesion.Grabadores
import com.alvarotc.bito.sesion.MINUTO
import com.alvarotc.bito.sesion.MotorSesionAndroid
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.premios.RegistroAnclas
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
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
import org.robolectric.annotation.Config

/** Terminada con el motor real y el reloj falso: lo que se ve, lo que cierra y lo que cuenta el libro. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp", application = Application::class)
class TerminadaContenidoTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoSesion
    private lateinit var g: Grabadores
    private lateinit var motor: MotorSesionAndroid

    private val sonido = com.alvarotc.bito.ui.sistema.SonidoGrabado()
    private val tacto = com.alvarotc.bito.ui.sistema.TactoGrabado()
    private val anclas = RegistroAnclas()
    private val retenido = BloqueRetenido()
    private val saltos = mutableListOf<Float>()
    private var salidas = 0
    private val avisos = mutableListOf<String>()
    private val sonda = SondaTerminada()

    @Before
    fun setUp() {
        banco = BancoSesion(tmp.root)
        banco.permisoNoMolestar(false)
        g = Grabadores()
        motor = banco.motor(g)
        runBlocking {
            banco.tasks.create(taskEntity(id = "t1", title = "Informe", dueKind = DueKind.DATE, dueDay = banco.hoy))
            banco.habits.create(
                habitEntity(id = "h1", name = "Leer", metric = Metric.CHECK, target = 1, createdOnDay = banco.hoy),
            )
        }
    }

    @After
    fun tearDown() = banco.cerrar()

    /** Una sesión directa de [destino] (los cuatro bloques de 25 por defecto) llevada hasta Terminada. */
    private fun terminada(
        destino: Destino?,
        bloques: Int = 4,
    ) = runBlocking {
        motor.preparar(destino)
        motor.cambiarBorrador(motor.borrador.value!!.copy(entrada = Entrada.DIRECTO, bloques = bloques))
        assertNull(motor.empezar())
        banco.reloj.avanzar((bloques * 25 + (bloques - 1) * 5 + if (bloques % 4 == 0) 15 else 0) * MINUTO)
        motor.tic()
        assertNull(motor.instante.value?.actual)
    }

    private fun montar(
        reducido: Boolean = false,
        motorAMontar: MotorSesionAndroid = motor,
    ) {
        val sesion =
            SesionInicio(
                motor = motorAMontar,
                aire = com.alvarotc.bito.sesion.AireMudo,
                ajustes = banco.settings,
                objetivos = { o ->
                    when (o) {
                        is Objetivo.Tarea -> banco.tasks.task(o.id)?.let { DatosObjetivo(it.title, it.firstStep) }
                        is Objetivo.Habito -> banco.habits.habit(o.id)?.let { DatosObjetivo(it.name, null) }
                        Objetivo.Libre -> null
                    }
                },
                catalogo = CatalogoRecursos { ApplicationProvider.getApplicationContext<Context>().resources },
            )
        compose.setContent {
            ConSistema(sonido, tacto, reducido) {
                CompositionLocalProvider(
                    LocalMotorSesion provides motorAMontar,
                    LocalSalidaSesion provides SalidaSesion({ salidas++ }, { avisos += it }),
                    LocalBloqueRetenido provides retenido,
                    LocalSaltoHabi provides { a -> saltos += a },
                    LocalSesionInicio provides sesion,
                    LocalAnclas provides anclas,
                ) {
                    Column { TerminadaContenido(sonda = sonda) }
                }
            }
        }
    }

    private fun existe(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /**
     * El motor cierra en sus hilos y la corrutina del botón vuelve al principal cuando acaba (suena `asentar` o
     * vibra `low` justo antes de devolver): se espera a esa señal y se dan dos fotogramas para que el retorno llegue.
     */
    private fun esperarCierre() {
        compose.waitUntil(10_000) {
            motor.registro.value == null && (Snd.ASENTAR in g.sonido.sonidos || Haptica.LOW in g.tacto.hapticas)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the texts for a task a habit and a free session`() {
        terminada(Destino.Tarea("t1"))
        montar()
        compose.onNodeWithTag("t-preg").assertTextEquals("Informe: ¿has acabado?")
        compose.onNodeWithTag("t-luego").assertTextEquals("Sigo luego")
        compose.onNodeWithTag("t-si").assertTextEquals("Sí, terminado")
        compose.onNodeWithTag("t-otra").assertTextEquals("Otra de 5 min")
    }

    @Test
    fun `a habit names the habit in the question`() {
        terminada(Destino.Habito("h1"))
        montar()
        compose.onNodeWithTag("t-preg").assertTextEquals("Leer: ¿has acabado?")
        assertTrue(existe("t-si"))
    }

    @Test
    fun `a free session has no question and says Listo`() {
        runBlocking { banco.tasks.markDone("t1", banco.hoy, banco.reloj.pared) }
        terminada(null)
        assertEquals(Objetivo.Libre, motor.registro.value?.plan?.objetivo)
        montar()
        assertFalse(existe("t-preg"))
        assertFalse(existe("t-luego"))
        compose.onNodeWithTag("t-si").assertTextEquals("Listo")
        compose.onNodeWithTag("t-otra").assertTextEquals("Otra de 5 min")
    }

    @Test
    fun `four default blocks show 1 h 40 min`() {
        terminada(Destino.Tarea("t1"))
        montar()
        compose.onNodeWithTag("t-min").assertTextEquals("1 h 40 min")
        compose.onNodeWithText("de foco").assertExists()
    }

    @Test
    fun `the figure uses the real focus minutes and at least one`() {
        terminada(Destino.Tarea("t1"), bloques = 1)
        montar()
        compose.onNodeWithTag("t-min").assertTextEquals("25 min")
    }

    @Test
    fun `yes finished flies the ledger's coins and returns at 1500 ms with its notice`() {
        terminada(Destino.Tarea("t1"))
        montar()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_000)
        assertNotNull("el botón es el origen de las monedas de la tarea", anclas.posiciones[ClavesAncla.tarea("t1")])
        saltos.clear()

        compose.onNodeWithTag("t-si").performClick()
        compose.mainClock.advanceTimeByFrame()
        esperarCierre()
        compose.mainClock.advanceTimeByFrame()

        assertEquals("Habi salta 0,38", listOf(0.38f), saltos)
        assertEquals(BloqueSesion.TERMINADA, retenido.bloque)
        val dicho = textoDe("t-preg")
        assertTrue(dicho, listOf("Hecha", "Una menos", "Terminada").any { dicho.startsWith(it) })
        compose.mainClock.advanceTimeBy(160)
        assertEquals("los botones se funden", 0f, sonda.botonesAlfa, 0.001f)

        compose.mainClock.advanceTimeBy(1_200)
        assertEquals(0, salidas)
        assertTrue(avisos.isEmpty())
        assertEquals(BloqueSesion.TERMINADA, retenido.bloque)

        compose.mainClock.advanceTimeBy(300)
        assertEquals(1, salidas)
        assertEquals(listOf("Informe, hecho."), avisos)
        assertNull(retenido.bloque)
        val libro = runBlocking { banco.domainState.snapshot().pointsLedger.single { it.reason == PointsReason.TASK_DONE } }
        assertEquals("las monedas son las del libro", 5, libro.delta)
        assertTrue(Snd.ASENTAR in g.sonido.sonidos)
        assertTrue(Haptica.CONFIRM in g.tacto.hapticas)
    }

    @Test
    fun `on time pays five and late pays three`() {
        runBlocking {
            banco.tasks.create(taskEntity(id = "t2", title = "Vieja", dueKind = DueKind.DATE, dueDay = banco.hoy - 3))
        }
        terminada(Destino.Tarea("t2"))
        montar()
        compose.onNodeWithTag("t-si").performClick()
        esperarCierre()

        val tarde = runBlocking { banco.domainState.snapshot().pointsLedger.single { it.reason == PointsReason.TASK_DONE } }
        assertEquals(3, tarde.delta)
    }

    @Test
    fun `yes finished on a habit says Hecho and pays the habit`() {
        terminada(Destino.Habito("h1"))
        montar()
        compose.mainClock.autoAdvance = false
        assertNotNull(anclas.posiciones[ClavesAncla.fila("h1")])

        compose.onNodeWithTag("t-si").performClick()
        compose.mainClock.advanceTimeByFrame()
        esperarCierre()
        compose.mainClock.advanceTimeByFrame()
        val texto = textoDe("t-preg")
        assertTrue(texto, texto.startsWith("Hecho") || texto.startsWith("Uno menos"))
        compose.mainClock.advanceTimeBy(1_600)

        assertEquals(listOf("Leer, hecho."), avisos)
        assertTrue(runBlocking { banco.domainState.snapshot().pointsLedger.any { it.reason == PointsReason.HABIT_DONE } })
    }

    @Test
    fun `yes finished on a free session returns with no notice`() {
        runBlocking { banco.tasks.markDone("t1", banco.hoy, banco.reloj.pared) }
        terminada(null)
        assertEquals(Objetivo.Libre, motor.registro.value?.plan?.objetivo)
        montar()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("t-si").performClick()
        compose.mainClock.advanceTimeByFrame()
        esperarCierre()
        compose.mainClock.advanceTimeBy(1_600)

        assertEquals(1, salidas)
        assertTrue(avisos.isEmpty())
    }

    @Test
    fun `later records an attempt and says it stays for later`() {
        terminada(Destino.Tarea("t1"))
        montar()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("t-luego").performClick()
        compose.mainClock.advanceTimeByFrame()
        esperarCierre()
        compose.mainClock.advanceTimeByFrame()

        assertEquals(1, salidas)
        assertEquals(listOf("Queda en tu día para luego."), avisos)
        assertEquals(TaskStatus.OPEN, runBlocking { banco.tasks.task("t1") }?.status)
        assertEquals(1, runBlocking { banco.db.taskEventDao().all() }.count { it.kind == TaskEventKind.ATTEMPT })
        assertTrue(Haptica.LOW in g.tacto.hapticas)
        assertEquals(ResultadoSesion.SIGO_LUEGO.name, runBlocking { banco.db.sesionDao().all() }.single().resultado)
    }

    @Test
    fun `another five goes straight to focus`() {
        terminada(Destino.Tarea("t1"))
        montar()

        compose.onNodeWithTag("t-otra").performClick()
        compose.waitUntil(10_000) { motor.instante.value?.actual is Tramo.Foco }

        assertEquals(Tramo.Foco(1, 5), motor.instante.value?.actual)
        assertEquals(1, motor.registro.value?.plan?.bloques)
        assertEquals(Entrada.DIRECTO, motor.registro.value?.plan?.entrada)
        assertEquals(0, salidas)
    }

    @Test
    fun `the arrival makes Habi jump after 420 ms and reduced does it at once`() {
        terminada(Destino.Tarea("t1"))
        compose.mainClock.autoAdvance = false
        montar()
        compose.mainClock.advanceTimeBy(400)
        assertTrue("antes de 420 ms no salta", saltos.isEmpty())
        compose.mainClock.advanceTimeBy(40)
        assertEquals(listOf(0.38f), saltos)
    }

    @Test
    fun `reduced jumps 0`() {
        terminada(Destino.Tarea("t1"))
        compose.mainClock.autoAdvance = false
        montar(reducido = true)
        compose.mainClock.advanceTimeBy(16)
        assertEquals(listOf(0.38f), saltos)
    }

    @Test
    fun `waiting for the answer survives a process death until the day changes`() {
        terminada(Destino.Tarea("t1"))
        val otro = banco.motor(Grabadores())
        runBlocking { otro.recuperar() }
        assertNotNull("sigue esperando la respuesta", otro.registro.value)
        assertNull(otro.instante.value?.actual)
        montar(motorAMontar = otro)
        compose.onNodeWithTag("t-min").assertTextEquals("1 h 40 min")
        compose.onNodeWithTag("t-preg").assertTextContains("Informe", substring = true)

        banco.reloj.avanzar(24 * 60 * MINUTO)
        val alDiaSiguiente = banco.motor(Grabadores())
        runBlocking { alDiaSiguiente.recuperar() }
        assertNull("al cambiar de día se cierra", alDiaSiguiente.registro.value)
    }

    private fun textoDe(tag: String): String =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .joinToString("") { it.text }
}
