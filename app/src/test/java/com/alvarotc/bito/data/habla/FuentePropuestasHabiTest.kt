package com.alvarotc.bito.data.habla

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.archivedOn
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entryOn
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.CatalogoHabla
import com.alvarotc.bito.domain.habla.CatalogoHablaVacio
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.ElegirCandidata
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.PropuestaVigente
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.habla.idPropuesta
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.domain.testZone
import com.alvarotc.bito.ui.notifications.AvisosHabi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val A_LAS_1430 = 14 * 60 + 30

/**
 * La fuente de Habi con Room en memoria, reloj falso y el catálogo de los recursos. El motor y los
 * hechos son falsos (en este carril los de verdad son provisionales): el motor propone «ayer se
 * escapó» de Leer mientras esté pendiente, la nota del objetivo en la ficha y, si no, un saludo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FuentePropuestasHabiTest {
    private lateinit var db: BitoDatabase
    private lateinit var aceptados: AvisosAceptadosRepository
    private lateinit var vigentes: PropuestaVigenteStore
    private lateinit var memoria: MemoriaRepository
    private lateinit var catalogo: CatalogoHabla

    private var ahora = millisOn(TODAY, 10)
    private val estado = MutableStateFlow(domainState())
    private var replanes = 0
    private val cancelados = mutableListOf<String>()
    private val registros = mutableListOf<Pair<String, Int>>()

    /** Con [vuelta], el motor propone la vuelta con [agua] tras 4 días fuera. */
    private var vuelta = false

    /** Con [conTope], el motor da un saludo en cuanto hoy se ha mostrado una propuesta que cuenta para el tope (§5.2). */
    private var conTope = false

    private val leerFalladoAyer: Habit = RealHabits.readBeforeSleep
    private val agua: Habit = RealHabits.water.copy(step = 2)

    private val avisos =
        object : AvisosHabi {
            override suspend fun replanificar() {
                replanes++
            }

            override suspend fun cancelarAceptados(ids: List<String>) {
                cancelados += ids
            }
        }

    private val motorFalso =
        ElegirCandidata { ambito, h, m, _, habitId ->
            when (ambito) {
                Ambito.HOY ->
                    if (conTope && m.mostradasHoy.any { it.situacion.cuentaTope }) {
                        Candidata(Situacion.SALUDO, null, emptyMap(), emptyList())
                    } else {
                        candidataHoy(h)
                    }
                Ambito.FICHA ->
                    h.habitos.firstOrNull { it.id == habitId }?.let {
                        Candidata(
                            Situacion.FI_OBJETIVO,
                            it.id,
                            mapOf(Hueco.PCT to ValorHueco.Numero(30), Hueco.OBJETIVO to ValorHueco.Texto("4 vasos")),
                            listOf(Respuesta.VALE, Respuesta.HOY_NO),
                        )
                    }
                else -> null
            }
        }

    private fun candidataHoy(h: Hechos): Candidata {
        if (vuelta) {
            return Candidata(
                Situacion.VUELTA,
                agua.id,
                mapOf(Hueco.HABITO to ValorHueco.Texto(agua.name), Hueco.DIAS_FUERA to ValorHueco.Numero(4)),
                listOf(Respuesta.HECHO, Respuesta.HOY_NO),
                destino = Destino.Habito(agua.id),
            )
        }
        val leer = h.habitos.firstOrNull { it.id == leerFalladoAyer.id && !it.hechoHoy }
        return if (leer != null) {
            Candidata(
                Situacion.AYER_SE_ESCAPO,
                leer.id,
                mapOf(Hueco.HABITO to ValorHueco.Texto(leer.nombre), Hueco.HORA to ValorHueco.Hora(A_LAS_1430)),
                listOf(Respuesta.VALE, Respuesta.AHORA, Respuesta.HOY_NO),
                destino = Destino.Habito(leer.id),
                hora = A_LAS_1430,
            )
        } else {
            Candidata(Situacion.SALUDO, null, emptyMap(), emptyList())
        }
    }

    /** Hechos sacados del estado: los hábitos no archivados, hechos si tienen un registro hoy. */
    private val hechosFalsos: (DomainState, Memoria, Contexto, Map<String, Int>) -> Hechos =
        { state, m, ctx, _ ->
            hechos(
                habitos =
                    state.habits.filter { it.status != HabitStatus.ARCHIVED }.map { h ->
                        habito(
                            id = h.id,
                            nombre = h.name,
                            tipo = if (h.metric == Metric.CHECK) TipoHabito.CHECK else TipoHabito.CONTADOR,
                            objetivo = h.target,
                            paso = h.step,
                            hechoHoy = state.entries.any { it.habitId == h.id && it.logicalDay == ctx.hoy },
                        )
                    },
                gordo = m.gordo[ctx.hoy].orEmpty(),
                diasSinActividad = if (vuelta) 4 else 0,
            )
        }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).allowMainThreadQueries().build()
        aceptados = AvisosAceptadosRepository(db)
        vigentes = PropuestaVigenteStore(db)
        memoria = MemoriaRepository(db) { ahora }
        catalogo = CatalogoRecursos { context.resources }
    }

    @After
    fun tearDown() = db.close()

    private fun estadoCon(vararg habitos: Habit): Flow<DomainState> {
        estado.value = domainState(habits = habitos.toList())
        return estado
    }

    private fun fuente(
        estado: Flow<DomainState>,
        nombre: String = "Álvaro",
    ) = FuentePropuestasHabi(
        estado = estado,
        ajustes = flowOf(Settings(userName = nombre, personality = Personality.NEUTRA)),
        recordatorios = flowOf(emptyMap()),
        memoria = memoria,
        vigentes = vigentes,
        gordo = LoGordoRepository(db) { ahora },
        aceptados = aceptados,
        catalogo = { catalogo },
        avisos = avisos,
        permisos = { PermisosAvisos(notificaciones = true, alarmasExactas = true) },
        now = { ahora },
        zone = { testZone },
        registrar = { id, cantidad -> registros += id to cantidad },
        motor = motorFalso,
        hechos = hechosFalsos,
    )

    private fun marcar(h: Habit) {
        estado.value = estado.value.copy(entries = estado.value.entries + entryOn(h, TODAY))
    }

    private suspend fun eventos(tipo: TipoMemoria): List<EventoMemoria> =
        db.hablaDao().todosEventos().mapNotNull { it.aDominio() }.filter { it.tipo == tipo }

    private suspend fun FuentePropuestasHabi.hoy(): Propuesta = propuesta(Ambito.HOY).first { it != null }!!

    @Test
    fun `the current proposal does not change when something else is marked`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer, agua))
            val antes = f.propuesta(Ambito.HOY).first { it != null }!!
            marcar(agua) // otro hábito
            val despues = f.propuesta(Ambito.HOY).first { it != null }!!
            assertTrue(antes.id.startsWith("AYER_SE_ESCAPO:"))
            assertEquals(antes.texto, despues.texto)
            assertEquals(antes.id, despues.id)
        }

    @Test
    fun `answering changes the current proposal`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer))
            val p = f.propuesta(Ambito.HOY).first { it != null }!!
            f.responder(p.id, Respuesta.VALE, p.hora)
            val confirmacion = f.propuesta(Ambito.HOY).first { it?.id != p.id }!!
            assertTrue(confirmacion.id.startsWith("CONFIRMA_VALE:"))
            assertEquals(1, aceptados.pendientes().size)
            val aviso = aceptados.pendientes().single()
            assertEquals("${p.id}:$A_LAS_1430", aviso.id)
            assertEquals(leerFalladoAyer.id, aviso.habitId)
            assertEquals(1, replanes)
            val respuesta = eventos(TipoMemoria.RESPUESTA).single()
            assertEquals(p.id, respuesta.ref)
            assertEquals(Respuesta.VALE.ordinal, respuesta.valor)
        }

    @Test
    fun `the confirmation is the new current proposal`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer, agua))
            val p = f.hoy()
            f.responder(p.id, Respuesta.VALE, p.hora)
            val c1 = f.hoy()
            marcar(agua)
            val c2 = f.hoy()
            assertEquals(c1, c2)
            assertEquals(Situacion.CONFIRMA_VALE, vigentes.leer(Ambito.HOY)!!.situacion)
        }

    @Test
    fun `doing the proposed thing from the list closes the card`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer))
            f.hoy()
            marcar(leerFalladoAyer)
            assertNull(f.propuesta(Ambito.HOY).first())
            val despues = f.propuesta(Ambito.HOY).first()!!
            // Lo que toque después, nunca una frase de premio por lo que se acaba de hacer.
            assertTrue(despues.id, despues.id.startsWith("SALUDO:"))
        }

    @Test
    fun `the morning notice and the card share the current proposal`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer))
            val tarjeta = async { f.propuesta(Ambito.HOY).first { it != null }!! }
            val aviso = async { f.propuesta(Ambito.HOY).first { it != null }!! }
            assertEquals(tarjeta.await().id, aviso.await().id)
            assertEquals(tarjeta.await().texto, aviso.await().texto)
            // Una sola redacción: un único uso de núcleo anotado.
            val usos = memoria.observe(flowOf(TODAY)).first().usos
            assertEquals(1, usos.count { it.situacion != null })
        }

    @Test
    fun `answering a proposal id from another day is ignored`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer))
            val p = f.hoy()

            val deAyer = idPropuesta(Situacion.AYER_SE_ESCAPO, leerFalladoAyer.id, TODAY - 1)
            assertEquals(Efecto.Ninguno, f.responder(deAyer, Respuesta.VALE, A_LAS_1430))

            ahora = millisOn(TODAY + 1, 10)
            assertEquals(Efecto.Ninguno, f.responder(p.id, Respuesta.VALE, p.hora))

            assertTrue(aceptados.pendientes().isEmpty())
            assertTrue(eventos(TipoMemoria.RESPUESTA).isEmpty())
            assertEquals(0, replanes)
            assertEquals(Situacion.AYER_SE_ESCAPO, vigentes.leer(Ambito.HOY)!!.situacion)
        }

    @Test
    fun `a proposal about an archived habit stops being valid`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer, agua))
            val p = f.hoy()
            estado.value = estado.value.copy(habits = listOf(leerFalladoAyer.archivedOn(TODAY), agua))
            val despues = f.hoy()
            assertNotEquals(p.id, despues.id)
            assertTrue(despues.id.startsWith("SALUDO:"))
        }

    @Test
    fun `a stored phrase pointing past the bag is redrafted`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer))
            vigentes.guardar(
                PropuestaVigente(
                    ambito = Ambito.HOY,
                    habitId = "",
                    dia = TODAY,
                    situacion = Situacion.AYER_SE_ESCAPO,
                    ref = leerFalladoAyer.id,
                    trozos = listOf(ClaveTrozo(Bolsa.NU_AYER_ESCAPO, Personality.NEUTRA, 999)),
                    huecos = mapOf(Hueco.HABITO to ValorHueco.Texto("Leer"), Hueco.HORA to ValorHueco.Hora(A_LAS_1430)),
                    acciones = listOf(Respuesta.VALE, Respuesta.AHORA, Respuesta.HOY_NO),
                    destino = null,
                    hora = A_LAS_1430,
                    creadoMillis = ahora,
                ),
            )

            val p = f.hoy()

            val frase = p.texto as TextoHabi.Frase
            assertTrue(frase.trozos.isNotEmpty())
            assertTrue(frase.trozos.all { it.indice < catalogo.trozos(it.bolsa, it.voz).size })
            assertTrue(frase.trozos.none { it.indice == 999 })
        }

    @Test
    fun `other hour with an hour moves the accepted notice`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer))
            val p = f.hoy()
            f.responder(p.id, Respuesta.VALE, p.hora)
            val confirmacion = f.hoy()

            val elegir = f.responder(confirmacion.id, Respuesta.OTRA_HORA)
            assertEquals(Efecto.ElegirHora(10 * 60 + 30, 21 * 60 + 15, A_LAS_1430), elegir)

            assertEquals(Efecto.Ninguno, f.responder(confirmacion.id, Respuesta.OTRA_HORA, 17 * 60))

            val aviso = aceptados.pendientes().single()
            assertEquals(17 * 60, aviso.minuto)
            assertEquals(leerFalladoAyer.id, aviso.habitId)
            assertEquals(listOf("${p.id}:$A_LAS_1430"), cancelados)
            val vigente = vigentes.leer(Ambito.HOY)!!
            assertEquals(Situacion.CONFIRMA_VALE, vigente.situacion)
            assertEquals(17 * 60, vigente.hora)
        }

    @Test
    fun `the objective note opens the editor with the suggested target without saving`() =
        runTest {
            val f = fuente(estadoCon(agua))
            val nota = f.propuesta(Ambito.FICHA, agua.id).first { it != null }!!
            assertTrue(nota.id.startsWith("FI_OBJETIVO:${agua.id}:"))

            val efecto = f.responder(nota.id, Respuesta.VALE)

            assertEquals(Efecto.EditarHabito(agua.id, 4), efecto) // objetivo 8, paso 2: la mitad
            assertEquals(8, estado.value.habits.single().target)
            assertTrue(registros.isEmpty())
            assertTrue(aceptados.pendientes().isEmpty())
        }

    @Test
    fun `saving the big thing makes it current`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer))
            f.hoy()
            val refs = listOf(RefGordo(TipoRefGordo.TAREA, "t1"), RefGordo(TipoRefGordo.HABITO, leerFalladoAyer.id))

            f.guardarGordo(refs)

            assertEquals(refs, LoGordoRepository(db).observe(listOf(TODAY)).first()[TODAY])
            assertTrue(f.hoy().id.startsWith("GORDO_ELEGIDO:"))
            assertEquals(Situacion.GORDO_ELEGIDO, vigentes.leer(Ambito.HOY)!!.situacion)
            assertEquals(1, replanes)
        }

    @Test
    fun `a memory mentioned is written once`() =
        runTest {
            memoria.anotar(EventoMemoria("animo-ayer", TipoMemoria.ANIMO, TODAY - 1, null, 0, ahora))
            val f = fuente(estadoCon(agua))
            val p = f.hoy()
            assertTrue(p.id.startsWith("SALUDO:"))
            marcar(agua)
            f.hoy()
            ahora += 60_000
            f.hoy()

            val menciones = eventos(TipoMemoria.MENCION)
            assertEquals(1, menciones.size)
            assertEquals("animo-ayer", menciones.single().ref)
            val frase = p.texto as TextoHabi.Frase
            assertTrue(frase.trozos.any { it.bolsa == Bolsa.RE_BIEN_AYER })
        }

    @Test
    fun `saying not today leaves the short phrase`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer))
            val p = f.hoy()

            assertEquals(Efecto.Ninguno, f.responder(p.id, Respuesta.HOY_NO))

            val despues = f.hoy()
            assertTrue(despues.id.startsWith("TRAS_HOY_NO:"))
            assertTrue(despues.acciones.isEmpty())
            assertTrue(aceptados.pendientes().isEmpty())
        }

    @Test
    fun `now opens a session with the proposed habit`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer))
            val p = f.hoy()
            assertEquals(Efecto.Sesion(Destino.Habito(leerFalladoAyer.id)), f.responder(p.id, Respuesta.AHORA))
        }

    @Test
    fun `done on the comeback logs the step and remembers the days away`() =
        runTest {
            vuelta = true
            val f = fuente(estadoCon(agua))
            val p = f.hoy()
            assertTrue(p.id.startsWith("VUELTA:"))

            assertEquals(Efecto.Ninguno, f.responder(p.id, Respuesta.HECHO))

            assertEquals(listOf(agua.id to 2), registros)
            val vuelta = eventos(TipoMemoria.VUELTA).single()
            assertEquals(4, vuelta.valor)
        }

    @Test
    fun `with an empty catalogue the proposal stays put`() =
        runTest {
            catalogo = CatalogoHablaVacio
            val f = fuente(estadoCon(leerFalladoAyer, agua))
            val antes = f.hoy()
            val creada = vigentes.leer(Ambito.HOY)!!.creadoMillis
            marcar(agua)
            ahora += 60_000
            val despues = f.hoy()

            assertEquals(antes, despues)
            assertEquals(creada, vigentes.leer(Ambito.HOY)!!.creadoMillis)
        }

    @Test
    fun `the rest says nothing`() =
        runTest {
            assertNull(fuente(estadoCon(agua)).propuesta(Ambito.DESCANSO).first())
        }

    @Test
    fun `the current proposal is kept and counted once against the daily cap`() =
        runTest {
            conTope = true
            val f = fuente(estadoCon(leerFalladoAyer, agua))
            val p = f.hoy()
            assertTrue(p.id.startsWith("AYER_SE_ESCAPO:"))
            marcar(agua)
            ahora += 60_000
            assertEquals(p, f.hoy())
            ahora += 60_000
            assertEquals(p, f.hoy())

            val mostradas = memoria.observe(flowOf(TODAY)).first().mostradasHoy
            assertEquals(listOf(Situacion.AYER_SE_ESCAPO), mostradas.map { it.situacion })
        }

    @Test
    fun `an answer lasts the wait after answering and then the motor decides again`() =
        runTest {
            val f = fuente(estadoCon(leerFalladoAyer))
            val p = f.hoy()
            f.responder(p.id, Respuesta.HOY_NO)
            ahora += 89 * 60_000L
            assertTrue(f.hoy().id.startsWith("TRAS_HOY_NO:"))
            ahora += 60_000L
            assertEquals(p.id, f.hoy().id)
        }
}
