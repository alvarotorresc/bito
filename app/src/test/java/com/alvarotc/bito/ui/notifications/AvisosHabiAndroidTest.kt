package com.alvarotc.bito.ui.notifications

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habla.AvisosAceptadosRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.habla.idPropuesta
import com.alvarotc.bito.domain.habla.tarea
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.propuestas.Accion
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.inicio.resolverTextoHabi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Los avisos de Habi sobre Android con un plan falso y una fuente falsa (en este carril `Hechos.de` y el motor
 * siguen provisionales). Las alarmas se leen de `ShadowAlarmManager` y las notificaciones de la bandeja.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@Suppress("DEPRECATION") // ShadowAlarmManager.ScheduledAlarm#operation no tiene otro acceso.
class AvisosHabiAndroidTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)

    private lateinit var db: BitoDatabase
    private lateinit var repo: AvisosAceptadosRepository

    private val hora = 14 * 60 + 30
    private val vigente =
        Propuesta(
            id = idPropuesta(Situacion.AYER_SE_ESCAPO, "h1", TODAY),
            texto = TextoHabi(R.string.habla_gordo_aviso, listOf("Leer")),
            acciones =
                listOf(
                    Accion(Respuesta.VALE, TextoHabi(R.string.habla_vale), principal = true),
                    Accion(Respuesta.AHORA, TextoHabi(R.string.habla_ahora)),
                    Accion(Respuesta.HOY_NO, TextoHabi(R.string.task_not_today)),
                ),
            destino = Destino.Habito("h1"),
            hora = hora,
        )

    private var hechosHoy: Hechos = hechos(habitos = listOf(habito()))
    private var ahoraMin = 8 * 60 + 30
    private var horaNoche = 21 * 60 + 30

    /** Responde como la fuente de Habi: un «Vale» a la vigente de hoy crea su aviso; cualquier otro id, nada. */
    private inner class FuenteFalsa : FuentePropuestas {
        val respuestas = mutableListOf<Triple<String, Respuesta, Int?>>()

        override fun propuesta(
            ambito: Ambito,
            habitId: String?,
        ): Flow<Propuesta?> = flowOf(if (ambito == Ambito.HOY) vigente else null)

        override suspend fun responder(
            id: String,
            respuesta: Respuesta,
            hora: Int?,
        ): Efecto {
            respuestas += Triple(id, respuesta, hora)
            if (id == vigente.id && respuesta == Respuesta.VALE && hora != null) {
                repo.crear(AvisoAceptado("$id:$hora", TODAY, "h1", hora, 0, EstadoAviso.PENDIENTE, id, 1L))
            }
            return Efecto.Ninguno
        }

        override suspend fun guardarGordo(refs: List<RefGordo>) = Unit
    }

    private val fuente = FuenteFalsa()

    private val avisos by lazy {
        AvisosHabiAndroid(
            context,
            contenedor = { error("sin contenedor en los tests") },
            plan = {
                val c = ctx(ahoraMin = ahoraMin, horaNoche = horaNoche)
                PlanDeHoy(hechosHoy, Memoria(avisos = repo.observeDe(TODAY).first()), c)
            },
            fuente = { fuente },
            aceptados = { repo },
        )
    }

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        repo = AvisosAceptadosRepository(db)
        NotificationChannels.ensure(context)
        shadowOf(manager).setNotificationsEnabled(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        db.close()
        ShadowAlarmManager.reset()
    }

    private data class Alarma(val kind: String?, val key: String?, val millis: Long, val exacta: Boolean)

    private fun alarmas(): List<Alarma> =
        shadowOf(alarmManager).scheduledAlarms.map { a ->
            val i = shadowOf(a.operation).savedIntent
            Alarma(
                i.getStringExtra(ReminderScheduler.EXTRA_KIND),
                i.getStringExtra(ReminderScheduler.EXTRA_KEY),
                a.triggerAtTime,
                a.windowLengthMs == ShadowAlarmManager.WINDOW_EXACT,
            )
        }

    private fun alarma(
        kind: SlotKind,
        key: String,
    ): Alarma? = alarmas().singleOrNull { it.kind == kind.name && it.key == key }

    private suspend fun aceptado(
        id: String = "a1",
        minuto: Int = hora,
        dia: Int = TODAY,
        estado: EstadoAviso = EstadoAviso.PENDIENTE,
        pospuesto: Int = 0,
        creado: Long = 1L,
    ) = repo.crear(AvisoAceptado(id, dia, "h1", minuto, pospuesto, estado, "p", creado))

    private fun accion(
        action: String,
        extras: Intent.() -> Unit,
    ) = Intent(action).apply(extras)

    private fun publicada(id: Int): Notification? = shadowOf(manager).getNotification(id)

    private fun publicarFalsa(id: Int) =
        manager.notify(
            id,
            NotificationCompat.Builder(context, NotificationChannels.HABI).setSmallIcon(R.drawable.ic_stat_habi).build(),
        )

    private fun Notification.titulo() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    // Planificar

    @Test
    fun `the morning and the night are scheduled from the plan`() =
        runTest(dispatcher) {
            ahoraMin = 7 * 60

            avisos.replanificar()

            assertEquals(millisOn(TODAY, 8, 30), alarma(SlotKind.HABI_MANANA, "manana")?.millis)
            assertEquals(millisOn(TODAY, 21, 30), alarma(SlotKind.HABI_NOCHE, "noche")?.millis)
        }

    @Test
    fun `a sealed day moves the night to tomorrow and clears its notice`() =
        runTest(dispatcher) {
            ahoraMin = 20 * 60
            avisos.replanificar()
            assertEquals(millisOn(TODAY, 21, 30), alarma(SlotKind.HABI_NOCHE, "noche")?.millis)
            publicarFalsa(Notifier.NOCHE_ID)

            hechosHoy = hechos(habitos = listOf(habito()), diaSellado = true)
            avisos.replanificar()

            assertEquals(millisOn(TODAY + 1, 21, 30), alarma(SlotKind.HABI_NOCHE, "noche")?.millis)
            assertNull(publicada(Notifier.NOCHE_ID))
        }

    @Test
    fun `an accepted notice for a habit already done loses its alarm and its reminder`() =
        runTest(dispatcher) {
            ahoraMin = 12 * 60
            aceptado()
            avisos.replanificar()
            assertNotNull(alarma(SlotKind.ACEPTADO, "a1"))
            publicarFalsa(Notifier.aceptadoId(0))

            hechosHoy = hechos(habitos = listOf(habito(hechoHoy = true)))
            avisos.replanificar()

            assertNull(alarma(SlotKind.ACEPTADO, "a1"))
            assertNull(publicada(Notifier.aceptadoId(0)))
        }

    @Test
    fun `an accepted notice left from another day loses its alarm`() =
        runTest(dispatcher) {
            ahoraMin = 12 * 60
            aceptado(id = "ayer", dia = TODAY - 1)
            ReminderScheduler.scheduleSlot(context, Slot(SlotKind.ACEPTADO, "ayer", hora), millisOn(TODAY - 1, 9, 0), ctx().zona)
            assertNotNull(alarma(SlotKind.ACEPTADO, "ayer"))

            avisos.replanificar()

            assertNull(alarma(SlotKind.ACEPTADO, "ayer"))
        }

    @Test
    fun `after boot pending accepted notices are rescheduled`() =
        runTest(dispatcher) {
            ahoraMin = 12 * 60
            aceptado(id = "hoy")
            aceptado(id = "ayer", dia = TODAY - 1)
            assertTrue(alarmas().isEmpty())

            BootReceiver.reprogramar(context, settings(), HabitsRepository(db), avisos)

            assertEquals(millisOn(TODAY, 14, 30), alarma(SlotKind.ACEPTADO, "hoy")?.millis)
            assertNull(alarma(SlotKind.ACEPTADO, "ayer"))
        }

    @Test
    fun `cancelling accepted notices before restore removes their alarms`() =
        runTest(dispatcher) {
            ahoraMin = 12 * 60
            aceptado(id = "a1")
            aceptado(id = "a2", minuto = 16 * 60, creado = 2L)
            avisos.replanificar()
            assertNotNull(alarma(SlotKind.ACEPTADO, "a1"))

            avisos.cancelarAceptados(listOf("a1", "a2"))

            assertNull(alarma(SlotKind.ACEPTADO, "a1"))
            assertNull(alarma(SlotKind.ACEPTADO, "a2"))
            assertNotNull(alarma(SlotKind.HABI_NOCHE, "noche"))
        }

    @Test
    fun `without exact alarm permission accepted notices use the inexact fallback`() =
        runTest(dispatcher) {
            ahoraMin = 12 * 60
            aceptado()
            avisos.replanificar()
            assertEquals(true, alarma(SlotKind.ACEPTADO, "a1")?.exacta)

            ShadowAlarmManager.setCanScheduleExactAlarms(false)
            avisos.replanificar()

            val sinPermiso = assertNotNull(alarma(SlotKind.ACEPTADO, "a1"))
            assertEquals(false, sinPermiso.exacta)
            assertEquals(millisOn(TODAY, 14, 30), sinPermiso.millis)
        }

    // Evaluar al sonar

    @Test
    fun `the morning notice carries the current proposal`() =
        runTest(dispatcher) {
            val outcome = assertIs<ReminderUseCase.Outcome.Manana>(avisos.evaluar(SlotKind.HABI_MANANA, "manana"))

            assertEquals(vigente, outcome.propuesta)
            assertEquals(Slot(SlotKind.HABI_MANANA, "manana", 8 * 60 + 30), outcome.slot)
            assertEquals("Good morning.", resolverTextoHabi(outcome.titulo, context.resources))
        }

    @Test
    fun `the morning title names the big thing or the suggestion`() =
        runTest(dispatcher) {
            val informe = tarea(id = "t1", titulo = "El informe")
            hechosHoy = hechos(habitos = listOf(habito()), tareas = listOf(informe), gordo = listOf(RefGordo(TipoRefGordo.TAREA, "t1")))
            val conGordo = assertIs<ReminderUseCase.Outcome.Manana>(avisos.evaluar(SlotKind.HABI_MANANA, "manana"))

            hechosHoy = hechos(habitos = listOf(habito()), tareas = listOf(informe), sugerenciaGordo = RefGordo(TipoRefGordo.TAREA, "t1"))
            val conSugerencia = assertIs<ReminderUseCase.Outcome.Manana>(avisos.evaluar(SlotKind.HABI_MANANA, "manana"))

            assertEquals("Today's big thing: El informe.", resolverTextoHabi(conGordo.titulo, context.resources))
            assertEquals("Today's big thing: El informe.", resolverTextoHabi(conSugerencia.titulo, context.resources))
        }

    @Test
    fun `the morning title while away is the absence one`() =
        runTest(dispatcher) {
            hechosHoy = hechos(habitos = listOf(habito()), diasSinActividad = 2)

            val outcome = assertIs<ReminderUseCase.Outcome.Manana>(avisos.evaluar(SlotKind.HABI_MANANA, "manana"))

            assertEquals("I'm here whenever you like.", resolverTextoHabi(outcome.titulo, context.resources))
        }

    @Test
    fun `a morning alarm that arrives late still speaks`() =
        runTest(dispatcher) {
            ahoraMin = 9 * 60

            assertIs<ReminderUseCase.Outcome.Manana>(avisos.evaluar(SlotKind.HABI_MANANA, "manana"))
        }

    @Test
    fun `the night notice counts the day and keeps quiet once sealed`() =
        runTest(dispatcher) {
            ahoraMin = horaNoche
            hechosHoy = hechos(habitos = listOf(habito(id = "h1", hechoHoy = true), habito(id = "h2")))

            val noche = assertIs<ReminderUseCase.Outcome.Noche>(avisos.evaluar(SlotKind.HABI_NOCHE, "noche"))
            val cuerpo = assertIs<TextoHabi.Frase>(noche.cuerpo)
            assertEquals(ValorHueco.Numero(1), cuerpo.huecos[Hueco.HECHOS])
            assertEquals(ValorHueco.Numero(2), cuerpo.huecos[Hueco.TOTAL])
            assertEquals("Before bed.", resolverTextoHabi(noche.titulo, context.resources))

            hechosHoy = hechos(habitos = listOf(habito()), diaSellado = true)
            val sellado = assertIs<ReminderUseCase.Outcome.Silent>(avisos.evaluar(SlotKind.HABI_NOCHE, "noche"))
            assertEquals(Slot(SlotKind.HABI_NOCHE, "noche", horaNoche), sellado.slot)
        }

    @Test
    fun `the night body names the big thing left undone`() =
        runTest(dispatcher) {
            ahoraMin = horaNoche
            val informe = tarea(id = "t1", titulo = "El informe")
            hechosHoy = hechos(habitos = listOf(habito()), tareas = listOf(informe), gordo = listOf(RefGordo(TipoRefGordo.TAREA, "t1")))

            val noche = assertIs<ReminderUseCase.Outcome.Noche>(avisos.evaluar(SlotKind.HABI_NOCHE, "noche"))

            assertEquals(ValorHueco.Lista(listOf("El informe")), assertIs<TextoHabi.Frase>(noche.cuerpo).huecos[Hueco.TAREA])
        }

    @Test
    fun `an accepted alarm that is no longer planned stays quiet`() =
        runTest(dispatcher) {
            ahoraMin = hora
            aceptado()
            hechosHoy = hechos(habitos = listOf(habito(hechoHoy = true)))

            assertEquals(ReminderUseCase.Outcome.Stale, avisos.evaluar(SlotKind.ACEPTADO, "a1"))
        }

    // Acciones desde el aviso

    @Test
    fun `Vale replaces the morning with the accepted one and schedules its alarm`() =
        runTest(dispatcher) {
            Notifier.showManana(context, vigente, TextoHabi(R.string.habla_ahora), ctx().voz, hechosHoy.animoSemana)

            avisos.accion(
                accion(AvisoHabiReceiver.ACTION_VALE) {
                    putExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID, vigente.id)
                    putExtra(AvisoHabiReceiver.EXTRA_HORA, hora)
                },
            )

            assertEquals(listOf<Triple<String, Respuesta, Int?>>(Triple(vigente.id, Respuesta.VALE, hora)), fuente.respuestas)
            val n = assertNotNull(publicada(Notifier.MANANA_ID))
            assertEquals("I'll remind you at 14:30.", n.titulo())
            assertTrue(n.actions.isNullOrEmpty())
            assertEquals(millisOn(TODAY, 14, 30), alarma(SlotKind.ACEPTADO, "${vigente.id}:$hora")?.millis)
        }

    @Test
    fun `a stale morning action only clears the notification`() =
        runTest(dispatcher) {
            Notifier.showManana(context, vigente, TextoHabi(R.string.habla_ahora), ctx().voz, hechosHoy.animoSemana)
            val deAyer = idPropuesta(Situacion.AYER_SE_ESCAPO, "h1", TODAY - 1)

            avisos.accion(
                accion(AvisoHabiReceiver.ACTION_VALE) {
                    putExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID, deAyer)
                    putExtra(AvisoHabiReceiver.EXTRA_HORA, hora)
                },
            )

            assertEquals(listOf<Triple<String, Respuesta, Int?>>(Triple(deAyer, Respuesta.VALE, hora)), fuente.respuestas)
            assertNull(publicada(Notifier.MANANA_ID))
            assertTrue(alarmas().none { it.kind == SlotKind.ACEPTADO.name })
        }

    @Test
    fun `a third accepted notice of the day is not taken`() =
        runTest(dispatcher) {
            aceptado(id = "a1", estado = EstadoAviso.LANZADO)
            aceptado(id = "a2", minuto = 12 * 60, creado = 2L)
            Notifier.showManana(context, vigente, TextoHabi(R.string.habla_ahora), ctx().voz, hechosHoy.animoSemana)

            avisos.accion(
                accion(AvisoHabiReceiver.ACTION_VALE) {
                    putExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID, vigente.id)
                    putExtra(AvisoHabiReceiver.EXTRA_HORA, hora)
                },
            )

            assertTrue(fuente.respuestas.isEmpty())
            assertNull(publicada(Notifier.MANANA_ID))
            assertEquals(2, repo.observeDe(TODAY).first().size)
        }

    @Test
    fun `cancelled notices do not use up the daily cap`() =
        runTest(dispatcher) {
            aceptado(id = "a1", estado = EstadoAviso.CANCELADO)
            aceptado(id = "a2", minuto = 12 * 60, estado = EstadoAviso.CANCELADO, creado = 2L)

            avisos.accion(
                accion(AvisoHabiReceiver.ACTION_VALE) {
                    putExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID, vigente.id)
                    putExtra(AvisoHabiReceiver.EXTRA_HORA, hora)
                },
            )

            assertEquals(listOf<Triple<String, Respuesta, Int?>>(Triple(vigente.id, Respuesta.VALE, hora)), fuente.respuestas)
            assertEquals(millisOn(TODAY, 14, 30), alarma(SlotKind.ACEPTADO, "${vigente.id}:$hora")?.millis)
        }

    @Test
    fun `OK then another time twice keeps the last notice scheduled`() =
        runTest(dispatcher) {
            ahoraMin = 9 * 60
            aceptado(id = "vale", estado = EstadoAviso.CANCELADO)
            aceptado(id = "otra1", minuto = 15 * 60, estado = EstadoAviso.CANCELADO, creado = 2L)
            aceptado(id = "otra2", minuto = 16 * 60, creado = 3L)

            avisos.replanificar()

            assertEquals(millisOn(TODAY, 16, 0), alarma(SlotKind.ACEPTADO, "otra2")?.millis)
            assertNull(alarma(SlotKind.ACEPTADO, "vale"))
        }

    @Test
    fun `Not today answers the proposal and clears the morning`() =
        runTest(dispatcher) {
            Notifier.showManana(context, vigente, TextoHabi(R.string.habla_ahora), ctx().voz, hechosHoy.animoSemana)

            avisos.accion(
                accion(AvisoHabiReceiver.ACTION_HOY_NO) {
                    putExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID, vigente.id)
                    putExtra(AvisoHabiReceiver.EXTRA_RESPUESTA, Respuesta.HOY_NO.name)
                },
            )

            assertEquals(listOf<Triple<String, Respuesta, Int?>>(Triple(vigente.id, Respuesta.HOY_NO, null)), fuente.respuestas)
            assertNull(publicada(Notifier.MANANA_ID))
        }

    @Test
    fun `later twice and then no later`() =
        runTest(dispatcher) {
            aceptado()
            val masTarde = accion(AvisoHabiReceiver.ACTION_MAS_TARDE) { putExtra(AvisoHabiReceiver.EXTRA_AVISO_ID, "a1") }

            ahoraMin = hora
            assertTrue(assertIs<ReminderUseCase.Outcome.Aceptado>(avisos.evaluar(SlotKind.ACEPTADO, "a1")).masTarde)
            publicarFalsa(Notifier.aceptadoId(0))
            avisos.accion(masTarde)
            assertNull(publicada(Notifier.aceptadoId(0)))
            assertEquals(millisOn(TODAY, 15, 0), alarma(SlotKind.ACEPTADO, "a1")?.millis)

            ahoraMin = 15 * 60
            assertTrue(assertIs<ReminderUseCase.Outcome.Aceptado>(avisos.evaluar(SlotKind.ACEPTADO, "a1")).masTarde)
            avisos.accion(masTarde)
            assertEquals(millisOn(TODAY, 15, 30), alarma(SlotKind.ACEPTADO, "a1")?.millis)

            ahoraMin = 15 * 60 + 30
            val tercera = assertIs<ReminderUseCase.Outcome.Aceptado>(avisos.evaluar(SlotKind.ACEPTADO, "a1"))
            assertEquals(false, tercera.masTarde)
            assertEquals("Leer", tercera.nombre)
            assertEquals(QuickTarget("h1", "Leer", amount = 1, isCheck = true), tercera.target)
            val guardado = repo.observeDe(TODAY).first().single()
            assertEquals(2, guardado.pospuesto)
            assertEquals(EstadoAviso.PENDIENTE, guardado.estado)
        }

    @Test
    fun `Tomorrow on the night notice takes it away`() =
        runTest(dispatcher) {
            publicarFalsa(Notifier.NOCHE_ID)

            avisos.accion(Intent(AvisoHabiReceiver.ACTION_MANANA))

            assertNull(publicada(Notifier.NOCHE_ID))
        }

    private fun settings(): SettingsRepository =
        SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
            ) { File(tmp.root, "avisos.preferences_pb") },
        )
}
