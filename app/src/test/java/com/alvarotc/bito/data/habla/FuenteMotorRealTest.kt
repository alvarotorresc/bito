package com.alvarotc.bito.data.habla

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entryOn
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.ElegirCandidata
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.FaseHabi
import com.alvarotc.bito.domain.habla.Motor
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.testZone
import com.alvarotc.bito.ui.notifications.AvisosHabi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * La fuente con el motor y los hechos de verdad (lo que solo se ve al juntar m1 y m2): Room en memoria,
 * reloj falso y el catálogo de los recursos. Leer y Meditar se escaparon ayer tras cumplirse los tres
 * días anteriores, así que pasadas las 13:00 (sin lo gordo que elegir) sale «ayer se escapó» con hora.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FuenteMotorRealTest {
    private lateinit var db: BitoDatabase
    private lateinit var aceptados: AvisosAceptadosRepository
    private lateinit var catalogo: CatalogoRecursos

    private var ahora = millisOn(TODAY, 13, 30)
    private val leer: Habit = RealHabits.readBeforeSleep
    private val meditar: Habit = RealHabits.meditate
    private val estado = MutableStateFlow(escapados(leer, meditar))
    private val contextos = mutableListOf<Contexto>()

    private val avisos =
        object : AvisosHabi {
            override suspend fun replanificar() = Unit

            override suspend fun cancelarAceptados(ids: List<String>) = Unit
        }

    /** El motor de verdad, apuntando el contexto con que se le llama. */
    private val motor =
        ElegirCandidata { ambito, h, m, ctx, habitId ->
            contextos += ctx
            Motor.elegir(ambito, h, m, ctx, habitId)
        }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).allowMainThreadQueries().build()
        aceptados = AvisosAceptadosRepository(db)
        catalogo = CatalogoRecursos { context.resources }
    }

    @After
    fun tearDown() = db.close()

    private fun escapados(
        vararg habitos: Habit,
        sellados: List<Int> = emptyList(),
    ): DomainState =
        domainState(
            habits = habitos.toList(),
            entries = habitos.flatMap { h -> (2..4).map { entryOn(h, TODAY - it) } },
            sealedDays = sellados,
        )

    private fun fuente(corteDia: Int = 0) =
        FuentePropuestasHabi(
            estado = estado,
            ajustes = flowOf(Settings(userName = "Álvaro", personality = Personality.NEUTRA, dayCutoffMinutes = corteDia)),
            recordatorios = flowOf(emptyMap()),
            memoria = MemoriaRepository(db) { ahora },
            vigentes = PropuestaVigenteStore(db),
            gordo = LoGordoRepository(db) { ahora },
            aceptados = aceptados,
            catalogo = { catalogo },
            avisos = avisos,
            permisos = { PermisosAvisos(notificaciones = true, alarmasExactas = true) },
            now = { ahora },
            zone = { testZone },
            motor = motor,
        )

    private fun prueba(cuerpo: suspend TestScope.() -> Unit) = runTest(timeout = 20.seconds, testBody = cuerpo)

    private suspend fun FuentePropuestasHabi.hoy(): Propuesta? = propuesta(Ambito.HOY).first()

    private suspend fun avisosDeHoy(): List<AvisoAceptado> = aceptados.observeDe(TODAY).first()

    private fun situacion(p: Propuesta?): String? = p?.id?.substringBefore(':')

    // A1: el tope de dos avisos cuenta lo mismo en el motor que en el plan (sin los cancelados).

    @Test
    fun `vale, another hour and back keeps one notice and the next offer still has an hour`() =
        prueba {
            val f = fuente()
            val primera = f.hoy()!!
            assertEquals(Situacion.AYER_SE_ESCAPO.name, situacion(primera))
            val minuto = primera.hora!!
            f.responder(primera.id, Respuesta.VALE)
            val confirma = f.hoy()!!
            assertEquals(Situacion.CONFIRMA_VALE.name, situacion(confirma))
            f.responder(confirma.id, Respuesta.OTRA_HORA, minuto + 30)
            val otra = f.hoy()!!
            f.responder(otra.id, Respuesta.OTRA_HORA, minuto)
            val vuelta = f.hoy()!!
            assertEquals(Situacion.CONFIRMA_VALE.name, situacion(vuelta))
            assertEquals(minuto, vuelta.hora)

            val vivos = avisosDeHoy().filter { it.estado != EstadoAviso.CANCELADO }
            assertEquals(listOf(minuto), vivos.map { it.minuto })
            assertEquals(EstadoAviso.PENDIENTE, vivos.single().estado)

            // Pasada la espera, la siguiente propuesta con botones aún puede ofrecer hora: solo suena un aviso.
            ahora += 91 * 60_000L
            val siguiente = f.hoy()!!
            assertTrue(siguiente.id, situacion(siguiente) == Situacion.AYER_SE_ESCAPO.name)
            assertNotNull(siguiente.hora)
            assertTrue(siguiente.acciones.any { it.respuesta == Respuesta.VALE })
        }

    // A5: con el día sellado y sin vigente de noche, Hoy da la noche (nada que el motor proponga se pierde).

    @Test
    fun `a sealed day without a night proposal gives the night card`() =
        prueba {
            estado.value = escapados(leer, meditar, sellados = listOf(TODAY))
            ahora = millisOn(TODAY, 22)
            val p = fuente().hoy()
            assertEquals(Situacion.NOCHE.name, situacion(p))
            assertTrue(p!!.acciones.isEmpty())
        }

    // A6: lo que caduca 30 min antes de la noche ya no se propone desde ahí.

    @Test
    fun `twenty minutes before the night the card is not empty`() =
        prueba {
            ahora = millisOn(TODAY, 21, 10)
            val p = fuente().hoy()
            assertNotNull(p)
            val s = situacion(p)
            assertTrue(s, s !in setOf(Situacion.AYER_SE_ESCAPO.name, Situacion.RACHA_CERCA.name, Situacion.DIA_FLOJO.name))
        }

    // A7: la fase y «Todo hecho» cuentan lo pendiente igual (un límite limpio sin sellar ya no espera nada).

    @Test
    fun `a clean limit alone leaves Habi waiting and offers everything done`() =
        prueba {
            val limite = RealHabits.water.copy(id = "limite", direction = Direction.AT_MOST, target = 3)
            estado.value = domainState(habits = listOf(limite))
            ahora = millisOn(TODAY, 18)
            val p = fuente().hoy()
            assertEquals(Situacion.TODO_HECHO.name, situacion(p))
            assertEquals(FaseHabi.ESPERANDO, contextos.last().fase)
        }

    // A12: la fuente no deja crear un tercer aviso aceptado, aunque la propuesta naciera con hora.

    @Test
    fun `vale on the card with two notices already accepted does not create a third`() =
        prueba {
            val f = fuente()
            val p = f.hoy()!!
            assertNotNull(p.hora)
            // Mientras la tarjeta seguía con su hora, se aceptaron dos desde otro sitio (la ficha, un aviso).
            aceptados.crear(AvisoAceptado("x:1", TODAY, "otro1", 16 * 60, 0, EstadoAviso.PENDIENTE, "x", ahora))
            aceptados.crear(AvisoAceptado("y:1", TODAY, "otro2", 17 * 60, 0, EstadoAviso.PENDIENTE, "y", ahora))
            f.responder(p.id, Respuesta.VALE)
            assertEquals(2, avisosDeHoy().count { it.estado != EstadoAviso.CANCELADO })
        }

    @Test
    fun `vale on the note of the habit page with two notices already accepted does not create a third`() =
        prueba {
            val f = fuente()
            val nota = f.propuesta(Ambito.FICHA, leer.id).first()!!
            assertEquals(Situacion.FI_HOY_FALTA.name, situacion(nota))
            aceptados.crear(AvisoAceptado("x:1", TODAY, "otro1", 16 * 60, 0, EstadoAviso.PENDIENTE, "x", ahora))
            aceptados.crear(AvisoAceptado("y:1", TODAY, "otro2", 17 * 60, 0, EstadoAviso.LANZADO, "y", ahora))
            f.responder(nota.id, Respuesta.VALE)
            assertEquals(2, avisosDeHoy().count { it.estado != EstadoAviso.CANCELADO })
        }

    @Test
    fun `another hour after the notice already rang does not make a third`() =
        prueba {
            val f = fuente()
            val p = f.hoy()!!
            f.responder(p.id, Respuesta.VALE)
            val confirma = f.hoy()!!
            // El aviso ya sonó (no se cancela al cambiar la hora) y se aceptó otro desde la ficha.
            aceptados.cambiarEstado(avisosDeHoy().single().id, EstadoAviso.LANZADO)
            aceptados.crear(AvisoAceptado("y:1", TODAY, "otro2", 17 * 60, 0, EstadoAviso.PENDIENTE, "y", ahora))
            f.responder(confirma.id, Respuesta.OTRA_HORA, p.hora!! + 30)
            assertEquals(2, avisosDeHoy().count { it.estado != EstadoAviso.CANCELADO })
        }

    // A9: la fuente apunta qué día duro mostró, para que Hoy lo sepa tras reiniciar aunque la tarjeta cambie.

    @Test
    fun `showing a hard day leaves its id in the memory`() =
        prueba {
            MemoriaRepository(db) { ahora }.anotar(
                EventoMemoria(
                    "animo",
                    TipoMemoria.ANIMO,
                    TODAY - 1,
                    null,
                    2,
                    ahora,
                ),
            )
            val p = fuente().hoy()!!
            assertEquals(Situacion.DIA_DURO.name, situacion(p))
            val mostradas =
                db.hablaDao().todosEventos().mapNotNull { it.aDominio() }.filter { it.tipo == TipoMemoria.MOSTRADA }
            assertEquals(listOf(p.id), mostradas.map { it.ref })
        }
}
