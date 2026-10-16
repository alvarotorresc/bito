package com.alvarotc.bito.ui.sesion

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.habla.AvisosAceptadosRepository
import com.alvarotc.bito.data.habla.CatalogoRecursos
import com.alvarotc.bito.data.habla.FuentePropuestasHabi
import com.alvarotc.bito.data.habla.LoGordoRepository
import com.alvarotc.bito.data.habla.MemoriaRepository
import com.alvarotc.bito.data.habla.PermisosAvisos
import com.alvarotc.bito.data.habla.PropuestaVigenteStore
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.sesion.BancoSesion
import com.alvarotc.bito.sesion.MotorSesionAndroid
import com.alvarotc.bito.ui.notifications.AvisosHabiNulo
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.buildTodayUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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

/** El descanso con el motor de verdad, la fuente de Habi de verdad y Room en memoria. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "es-w412dp-h915dp")
class DescansoContenidoTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoSesion
    private lateinit var motor: MotorSesionAndroid
    private lateinit var fuente: FuentePropuestasHabi
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private var tarjetas by mutableStateOf(emptyList<HabitCardUi>())
    private val marcadas = mutableListOf<String>()
    private val saltos = mutableListOf<Float>()

    @Before
    fun setUp() {
        banco = BancoSesion(tmp.root)
        banco.permisoNoMolestar(false)
        motor = banco.motor()
        val ctx = banco.context
        fuente =
            FuentePropuestasHabi(
                estado = banco.domainState.observe(),
                ajustes = banco.settings.settings,
                recordatorios = flowOf(emptyMap()),
                memoria = MemoriaRepository(banco.db) { banco.reloj.pared },
                vigentes = PropuestaVigenteStore(banco.db),
                gordo = LoGordoRepository(banco.db) { banco.reloj.pared },
                aceptados = AvisosAceptadosRepository(banco.db),
                catalogo = { CatalogoRecursos { ctx.resources } },
                avisos = AvisosHabiNulo,
                permisos = { PermisosAvisos(notificaciones = true, alarmasExactas = true) },
                now = { banco.reloj.pared },
                zone = { banco.zona },
                registrar = { id, cantidad -> banco.acciones.log(id, cantidad) },
                despachador = banco.hilos,
            )
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun habito(
        id: String,
        nombre: String,
    ) = runBlocking {
        banco.habits.create(habitEntity(id = id, name = nombre, metric = Metric.CHECK, target = 1, unit = null, createdOnDay = DAY_ZERO))
    }

    private fun refrescar() =
        runBlocking {
            val orden = banco.habits.observeHabits().first().associate { it.id to it.sortOrder }
            tarjetas = buildTodayUiState(banco.domainState.snapshot(), orden, banco.hoy).cards
        }

    private fun avanzar(ms: Long) {
        banco.reloj.avanzar(ms)
        runBlocking { motor.tic() }
    }

    /** Una sesión con Estirar pendiente, ya en el descanso corto tras el bloque 1. */
    private fun enElDescanso(
        bloques: Int = 4,
        descansoSolo: Boolean = true,
    ) = runBlocking {
        banco.tasks.create(taskEntity(id = "t1", title = "Informe", dueKind = DueKind.DATE, dueDay = banco.hoy))
        motor.preparar(Destino.Tarea("t1"))
        motor.cambiarBorrador(motor.borrador.value!!.copy(bloques = bloques, descansoSolo = descansoSolo))
        motor.empezar()
        // Respirar para entrar dura 60 s.
        avanzar(MIN)
        avanzar(25 * MIN)
    }

    private fun montar() {
        refrescar()
        val apoyo =
            ApoyoDescanso(
                propuesta = { fuente.propuesta(Ambito.DESCANSO, descanso = it) },
                tarjetas = { tarjetas },
                marcar = { id ->
                    marcadas += id
                    runBlocking { fuente.responder(id, com.alvarotc.bito.domain.propuestas.Respuesta.HECHO) }
                    refrescar()
                },
            )
        compose.setContent {
            ConSistema(sonido, tacto) {
                CompositionLocalProvider(
                    LocalMotorSesion provides motor,
                    LocalApoyoDescanso provides apoyo,
                    LocalSaltoHabi provides { saltos += it },
                ) {
                    // Como la tierra: las piezas en columna.
                    androidx.compose.foundation.layout.Column { DescansoContenido() }
                }
            }
        }
        compose.waitUntil(
            10_000,
        ) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("tarjeta-habi")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun texto(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            ?.joinToString("") { it.text }
            .orEmpty()

    @Test
    fun `the break shows the clock, the phrase, what comes after and the skip`() {
        habito("estirar", "Estirar")
        enElDescanso()
        montar()
        assertEquals("5:00", texto("d-t"))
        assertEquals("de descanso", texto("d-de"))
        assertEquals("Después: foco de 25 min, bloque 2 de 4", texto("d-luego"))
        assertEquals("Saltar el descanso", texto("d-saltar"))
        assertTrue(texto("d-txt").isNotEmpty() || compose.onNodeWithTag("tarjeta-habi").fetchSemanticsNode() != null)
        compose.onNodeWithTag("d-fila").assertIsDisplayed()
    }

    @Test
    fun `the row is today's real row and marking waters`() {
        habito("estirar", "Estirar")
        enElDescanso()
        montar()
        compose.waitUntil(
            10_000,
        ) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("circulo-estirar")).fetchSemanticsNodes().isNotEmpty() }
        val frase =
            fuente.let {
                runBlocking {
                    it.propuesta(Ambito.DESCANSO, descanso = contextoDelBloque1()).first {
                            p ->
                        p != null
                    }!!.texto
                }
            }
        compose.onNodeWithTag("circulo-estirar").performClick()
        compose.waitUntil(10_000) { tarjetas.firstOrNull { it.id == "estirar" }?.doneToday == true }
        assertTrue(Snd.GORGOTEO in sonido.snds())
        assertTrue(Haptica.TICKS in tacto.hapticas)
        assertEquals(1, marcadas.size)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf(0.24f, 0.11f), saltos)
        // La tarjeta conserva su frase tras marcar.
        val despues = runBlocking { fuente.propuesta(Ambito.DESCANSO, descanso = contextoDelBloque1()).first { p -> p != null }!!.texto }
        assertEquals(frase, despues)
        // Un segundo toque sobre la marcada no deshace ni vuelve a registrar.
        compose.onNodeWithTag("circulo-estirar").performClick()
        compose.waitForIdle()
        assertEquals(1, marcadas.size)
    }

    @Test
    fun `arriving live Habi jumps 0,24`() {
        enElDescanso()
        montar()
        assertEquals(listOf(0.24f), saltos)
    }

    @Test
    fun `opening mid break there is no arrival jump`() {
        enElDescanso()
        avanzar(10_000)
        montar()
        assertEquals(emptyList<Float>(), saltos)
    }

    @Test
    fun `no row without pending`() {
        enElDescanso()
        montar()
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("d-fila")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("tarjeta-habi").assertIsDisplayed()
    }

    @Test
    fun `after the last block says the round is done`() {
        enElDescanso(bloques = 4)
        // Bloques 2, 3 y 4 con sus descansos hasta el largo.
        avanzar(5 * MIN)
        for (b in 2..3) {
            avanzar(25 * MIN)
            avanzar(5 * MIN)
        }
        avanzar(25 * MIN)
        assertEquals(Tramo.Descanso(4, largo = true, minutos = 15), motor.instante.value?.actual)
        montar()
        assertEquals("de descanso largo", texto("d-de"))
        assertEquals("Ronda de 4 bloques hecha. Después, cerramos la sesión.", texto("d-luego"))
    }

    @Test
    fun `waiting break shows start the break`() {
        habito("estirar", "Estirar")
        enElDescanso(descansoSolo = false)
        assertTrue(motor.instante.value!!.esperandoDescanso)
        montar()
        assertEquals("5:00", texto("d-t"))
        compose.onNodeWithTag("d-empezar").assertIsDisplayed()
        compose.onNodeWithTag("d-empezar").performClick()
        compose.waitUntil(10_000) { !motor.instante.value!!.esperandoDescanso }
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("d-empezar")).fetchSemanticsNodes().size)
    }

    @Test
    fun `skipping goes to the next focus with a tick`() {
        enElDescanso()
        montar()
        compose.onNodeWithTag("d-saltar").performClick()
        compose.waitUntil(10_000) { motor.instante.value?.actual == Tramo.Foco(2, 25) }
        assertTrue(Haptica.TICK in tacto.hapticas)
    }

    private fun contextoDelBloque1() =
        com.alvarotc.bito.domain.propuestas.ContextoDescanso(motor.registro.value!!.id, 1, largo = false, minutos = 5, excluir = null)

    private companion object {
        const val MIN = 60_000L
    }
}
