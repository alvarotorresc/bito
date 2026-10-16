package com.alvarotc.bito.ui.sesion

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ContextWrapper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.AppStartup
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.ResultadoSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.sesion.FondoGrabado
import com.alvarotc.bito.sesion.MotorSesionAndroid
import com.alvarotc.bito.sesion.SonidoGrabado
import com.alvarotc.bito.sesion.TactoGrabado
import com.alvarotc.bito.sesion.fuera.AlarmaSesion
import com.alvarotc.bito.sesion.fuera.PuertaFuera
import com.alvarotc.bito.sesion.fuera.ServicioSesion
import com.alvarotc.bito.sesion.fuera.SesionReceiver
import com.alvarotc.bito.ui.AppVisibility
import com.alvarotc.bito.ui.BitoNavHost
import com.alvarotc.bito.ui.NavRequests
import com.alvarotc.bito.ui.garden.LocalSondaJardin
import com.alvarotc.bito.ui.garden.SondaJardin
import com.alvarotc.bito.ui.inicio.EntradaSesion
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier
import com.alvarotc.bito.ui.notifications.ReminderReceiver
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.theme.BitoTheme
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File
import java.time.ZoneId

private const val MINUTO = 60_000L
private const val SEGUNDO = 1_000L
private const val ESPERA_MAXIMA = 1_500

/**
 * El recorrido de la sesión de punta a punta (plan de la pieza 5, Tarea 22): `BitoNavHost` con el `AppContainer` real de la
 * app, el reloj de Robolectric movido a mano (`elapsedRealtime`, con el contador de arranques fijo: dentro de un arranque manda
 * ese reloj), `ShadowAlarmManager` y `ShadowNotificationManager`, y los receptores lanzados a mano en cada frontera con la app
 * fuera. Lo esperado sale de la spec (§4, §7, §10, §11, §12, §15) y del mock, no de lo que hace hoy el código.
 *
 * Cómo se monta, y lo que esto no es:
 * - El reloj de pared de Robolectric no se puede mover: la pared va en tiempo real y el tiempo de la sesión sale de
 *   `elapsedRealtime`, que sí. Por eso el contador de arranques se fija en `Settings.Global.BOOT_COUNT`.
 * - La base de datos del contenedor es un archivo de la aplicación de prueba, no una en memoria: `AppContainer` no acepta otra.
 * - Se entra en Preparar con `EntradaSesion.pedir(Destino.Tarea)`, que es justo lo que emite «Sesión de 25 min» de la tarjeta
 *   de Habi; sacar esa tarjeta depende de la hora real del día.
 * - `ServicioSesion` no lo arranca Robolectric solo: el test lo crea con el aviso que la app le pasó, como haría Android.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class SesionE2ETest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val app get() = ApplicationProvider.getApplicationContext<BitoApp>()
    private lateinit var container: AppContainer
    private val sonda = SondaJardin()
    private val servicios = mutableListOf<ServiceController<ServicioSesion>>()
    private val contenedoresAparte = mutableListOf<AppContainer>()
    private lateinit var fondo: FondoGrabado
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()

    /** El `elapsedRealtime` de Empezar: el cero de la sesión. Los tiempos del test son «ms desde Empezar». */
    private var cero = 0L

    private val nm get() = app.getSystemService(NotificationManager::class.java)
    private val alarmas get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before
    fun setUp() {
        // La app de prueba arranca AppStartup con sus colectores; aquí se apagan (un contenedor, el de la app).
        AppStartup.quiesceForTests()
        AppVisibility.visible = true
        container = app.container
        fondo = conFondoGrabado(container)
        shadowOf(nm).setNotificationsEnabled(true)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        NotificationChannels.ensure(app)
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 7)
        ServicioSesion.vivo.set(false)
        runBlocking {
            container.settings.update {
                it.copy(
                    onboardingDone = true,
                    sessionBackground = SessionBackground.RAIN,
                    sessionEntry = Entrada.RESPIRAR,
                    sessionDnd = true,
                )
            }
            container.tasks.create(
                taskEntity(id = "t1", title = "Informe", dueKind = DueKind.DATE, dueDay = hoy(), createdOnDay = hoy()),
            )
            container.habits.create(
                habitEntity(id = "h1", name = "Estirar", metric = Metric.CHECK, target = 1, unit = null, createdOnDay = hoy() - 3),
            )
        }
    }

    @After
    fun tearDown() {
        container.cerrarSesion()
        contenedoresAparte.forEach { it.cerrarSesion() }
        servicios.clear()
        ServicioSesion.vivo.set(false)
        AppVisibility.visible = false
        NavRequests.consume()
        EntradaSesion.consumir()
        AppStartup.quiesceForTests()
    }

    // ───────────────────────── escenario 1: el recorrido de punta a punta ─────────────────────────

    @Test
    fun `the whole session goes from the prepare sheet to Today with the app out in the middle`() {
        montar()
        hasta { hay("inicio") }
        EntradaSesion.pedir(Destino.Tarea("t1"))

        // 1. Preparar con la tarea, 25 min, 4 bloques, Para entrar «Respirar 1 min», Lluvia y No molestar (con permiso).
        hasta { hay("p-empezar") }
        val plan = container.motorSesion.borrador.value!!
        assertEquals(Objetivo.Tarea("t1"), plan.objetivo)
        assertEquals(25, plan.minutosFoco)
        assertEquals(4, plan.bloques)
        assertEquals(Entrada.RESPIRAR, plan.entrada)
        assertEquals(SessionBackground.RAIN, plan.fondo)
        assertTrue("No molestar encendido: hay permiso", plan.noMolestar)
        compose.onNodeWithTag("p-fondo").assertTextContains("Lluvia", substring = true)

        // 2. Empezar → Respirar para entrar: Habi con los ojos cerrados, el tono sonando, No molestar puesto, aviso fijo y una alarma.
        cero = SystemClock.elapsedRealtime()
        var marca = marcaSonidos()
        pulsar("p-empezar")
        hasta { hay("ses-b-entrar") }
        assertSuena(marca, Snd.BROTE, Haptica.CONFIRM, "Empezar")
        val registro = leerRegistro()!!
        assertEquals("el cero de la sesión es el de Empezar", cero.toDouble(), registro.anclaElapsed.toDouble(), SEGUNDO.toDouble())
        cero = registro.anclaElapsed
        assertEquals("el filtro de antes queda guardado", NotificationManager.INTERRUPTION_FILTER_ALL, registro.filtroPrevio)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        hasta { container.aire.sonando.value }
        hasta { sonda.ojosCerradosHabi() == 1f }
        assertAviso(titulo = "Informe", texto = "En marcha")
        assertAlarma(cero + MINUTO)
        // El tono suena y la app se puede ir: lo sostiene el servicio (§11.2).
        arrancarServicio()
        assertTrue("servicio mediaPlayback con el tono", ServicioSesion.vivo.get())

        // 3. Tras 6 respiraciones (60 s) Foco 1: ojos abiertos, la lluvia suena, aviso «En marcha» y la siguiente frontera.
        irA(MINUTO + SEGUNDO)
        marca = marcaSonidos()
        tic()
        hasta { hay("ses-b-foco") }
        assertSuena(marca, Snd.ALEJAR, Haptica.TICK, "Respirar pasa a Foco")
        hasta { sonda.ojosCerradosHabi() == 0f }
        hasta { fondo.sonando.value == SessionBackground.RAIN }
        assertFalse(container.aire.sonando.value)
        assertAviso(titulo = "Informe", texto = "En marcha")
        assertAlarma(cero + 26 * MINUTO)
        arrancarServicio()
        assertTrue("el servicio sigue", ServicioSesion.vivo.get())

        // 4. La app a segundo plano. En la frontera, el receptor: Descanso, el fondo calla, el servicio sigue.
        segundoPlano()
        irA(26 * MINUTO + SEGUNDO)
        marca = marcaSonidos()
        lanzarReceptor()
        hasta { textoAviso() == "Descanso" }
        assertSuena(marca, Snd.CAMPANILLA, Haptica.CLICK, "fin del bloque 1")
        assertNull("el fondo calla en el descanso", fondo.sonando.value)
        assertTrue("el servicio se mantiene en los descansos", ServicioSesion.vivo.get())
        assertNull("ningún receptor arranca el servicio", shadowOf(app).peekNextStartedService())
        assertAlarma(cero + 31 * MINUTO)

        // 5. Siguiente frontera: vuelve el bloque 2 y con él la lluvia.
        irA(31 * MINUTO + SEGUNDO)
        marca = marcaSonidos()
        lanzarReceptor()
        hasta { textoAviso() == "En marcha" }
        assertSuena(marca, Snd.BROTE, Haptica.RISE, "vuelta al foco")
        assertEquals("el fondo vuelve con el foco", SessionBackground.RAIN, fondo.sonando.value)
        assertTrue(ServicioSesion.vivo.get())
        assertNull("ningún receptor arranca el servicio", shadowOf(app).peekNextStartedService())
        assertAlarma(cero + 56 * MINUTO)

        // 6. Otra frontera: segundo descanso. Se vuelve a la app dentro de él.
        irA(56 * MINUTO + SEGUNDO)
        lanzarReceptor()
        hasta { textoAviso() == "Descanso" }
        assertNull(fondo.sonando.value)
        primerPlano()
        hasta { hay("ses-b-descanso") }

        // 7. El descanso propone el hábito pendiente y marcarlo es marcar de verdad: su registro y su bito.
        hasta { hay("d-fila") }
        assertTrue("la fila del hábito propuesto", hayTexto("Estirar"))
        val bitosAntes = bitos()
        pulsar("circulo-h1")
        hasta { entradasDe("h1").isNotEmpty() }
        hasta { bitos() > bitosAntes }
        assertTrue("el registro del hábito es de hoy", entradasDe("h1").all { it == hoy() })
        assertTrue(
            "el bito del hábito entra en el libro",
            estado().pointsLedger.any { it.reason == PointsReason.HABIT_DONE && it.refId?.startsWith("h1:") == true },
        )

        // 8. Los bloques 3 y 4 con la app delante: la UI anuncia sola cada frontera. Tras el 4, el descanso largo de 15.
        irA(61 * MINUTO + SEGUNDO)
        marca = marcaSonidos()
        tic()
        hasta { hay("ses-b-foco") }
        assertSuena(marca, Snd.BROTE, Haptica.RISE, "vuelta al foco con la app delante")
        assertEquals(SessionBackground.RAIN, fondo.sonando.value)
        irA(86 * MINUTO + SEGUNDO)
        tic()
        hasta { hay("ses-b-descanso") }
        irA(91 * MINUTO + SEGUNDO)
        tic()
        hasta { hay("ses-b-foco") }
        irA(116 * MINUTO + SEGUNDO)
        tic()
        hasta { hay("ses-b-descanso") }
        compose.onNodeWithTag("d-de").assertTextContains("largo", substring = true)
        assertAlarma(cero + 131 * MINUTO)

        // 9. Terminada con «1 h 40 min» de foco; Habi radiante; No molestar ya devuelto; sin fondo.
        irA(131 * MINUTO + SEGUNDO)
        marca = marcaSonidos()
        tic()
        hasta { hay("ses-b-fin") }
        assertSuena(marca, Snd.CAMPANILLA, null, "fin de la sesión")
        compose.onNodeWithTag("t-min").assertTextContains("1 h 40 min", substring = true)
        fotogramas(1_000)
        assertEquals("Habi radiante en Terminada", Mood.RADIANT, sonda.moodHabi())
        assertEquals("No molestar se devuelve al terminar", NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        assertNull(fondo.sonando.value)
        assertNotNull("Terminada espera tu respuesta: el registro sigue", leerRegistro())

        // 10. «Sí, terminado»: los bitos del libro (5, en plazo), Hoy con «Informe, hecho.», sin servicio ni aviso ni alarma.
        pulsar("t-si")
        hasta { hayTexto("Informe, hecho.") }
        val libro = estado().pointsLedger.single { it.reason == PointsReason.TASK_DONE }
        assertEquals(5, libro.delta)
        assertEquals(TaskStatus.DONE, runBlocking { container.tasks.task("t1") }?.status)
        assertNull("la sesión se borra del disco", leerRegistro())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        assertFalse("sin servicio", ServicioSesion.vivo.get())
        assertNull("sin aviso fijo", shadowOf(nm).getNotification(Notifier.FOCUS_ID))
        assertTrue("sin alarma de la sesión", alarmasDeSesion().isEmpty())
        val fila = runBlocking { container.database.sesionDao().all() }.single()
        assertEquals(100, fila.minutosFoco)
        assertEquals(4, fila.bloquesHechos)
        assertEquals(ResultadoSesion.HECHA.name, fila.resultado)
        hasta { hay("pildora-hoy") }
    }

    // ───────────────────────── escenario 2: matar el proceso ─────────────────────────

    @Test
    fun `the process dies in the middle of a focus and the new one brings back the same instant and the next boundary alarm`() {
        empezarDirecto(bloques = 2)
        irA(10 * MINUTO)
        tic()
        val antes = container.motorSesion.instante.value
        assertEquals(Tramo.Foco(1, 25), antes?.actual)
        val idSesion = leerRegistro()!!.id

        // El proceso muere: Android se lleva las alarmas y el aviso, y el sonido se calla.
        val nuevo = procesoNuevo()
        irA(12 * MINUTO)

        // Se vuelve por el aviso: la petición «focus» abre Hoy en el estado en curso.
        montar(nuevo)
        hasta { hay("inicio") }
        NavRequests.open("focus")
        hasta { hay("ses-b-foco") }
        val instante = nuevo.motorSesion.instante.value!!
        assertEquals(Tramo.Foco(1, 25), instante.actual)
        assertEquals("es la misma sesión", idSesion, nuevo.motorSesion.registro.value?.id)
        assertEquals("13 min de los 25", (13 * MINUTO).toDouble(), instante.restaMillis.toDouble(), 2.0 * SEGUNDO)
        compose.onNodeWithTag("f-t").assertTextContains("13:0", substring = true)
        // Lo de fuera se rearma: la alarma es la de la siguiente frontera (fin del foco 1) y el aviso dice «En marcha».
        hasta { alarmasDeSesion().isNotEmpty() }
        assertAlarma(cero + 25 * MINUTO)
        assertAviso(titulo = "Informe", texto = "En marcha")
    }

    @Test
    fun `the process dies in a break and the app is opened after the break ended in the next block with the right time`() {
        empezarDirecto(bloques = 2)
        irA(26 * MINUTO)
        tic()
        assertEquals(Tramo.Descanso(1, largo = false, minutos = 5), container.motorSesion.instante.value?.actual)
        val nuevo = procesoNuevo()

        // El descanso (25 a 30 min) acabó mientras no había proceso.
        irA(35 * MINUTO)
        montar(nuevo)
        hasta { hay("inicio") }
        NavRequests.open("focus")
        hasta { hay("ses-b-foco") }
        val instante = nuevo.motorSesion.instante.value!!
        assertEquals(Tramo.Foco(2, 25), instante.actual)
        assertEquals(
            "el bloque 2 empezó a los 30 min: llevan 5 de 25",
            (20 * MINUTO).toDouble(),
            instante.restaMillis.toDouble(),
            2.0 * SEGUNDO,
        )
        // Solo la alarma de la siguiente frontera (fin del bloque 2), no la vieja del descanso.
        hasta { alarmasDeSesion().isNotEmpty() }
        assertAlarma(cero + 55 * MINUTO)
        assertEquals(1, alarmasDeSesion().size)
        assertAviso(titulo = "Informe", texto = "En marcha")
    }

    // ───────────────────────── escenario 3: un recordatorio durante la sesión ─────────────────────────

    @Test
    fun `a reminder that falls during the session comes out when the session ends`() {
        runBlocking { container.settings.update { it.copy(breathingReminderEnabled = true) } }
        empezarDirecto(bloques = 1)
        segundoPlano()
        irA(10 * MINUTO)

        // El aviso cae a mitad del foco: espera, no suena.
        runBlocking { ReminderReceiver.procesar(app, container, "BREATHING", "", retener = true) }
        assertNull("el recordatorio espera a que acabe la sesión", shadowOf(nm).getNotification(Notifier.BREATHING_ID))

        // Acaba el único bloque con la app fuera: «Se acabó el tiempo» y, a la vez, sale lo que esperaba.
        irA(25 * MINUTO + SEGUNDO)
        lanzarReceptor()
        hasta { shadowOf(nm).getNotification(Notifier.BREATHING_ID) != null }
        val fin = shadowOf(nm).getNotification(Notifier.FOCUS_ID)
        assertEquals(NotificationChannels.HABI, fin.channelId)
        assertEquals("Se acabó el tiempo", fin.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("No molestar vuelve como estaba", NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        assertTrue("sin alarma de la sesión", alarmasDeSesion().isEmpty())
    }

    // ───────────────────────── escenario 4: Respirar con una sesión viva (H11) ─────────────────────────

    @Test
    fun `breathing from the card with a live session asks first and leaves the session as it was`() {
        empezarDirecto(bloques = 2)
        montar()
        hasta { hay("inicio") }

        EntradaSesion.pedir(Destino.Respirar)
        hasta { hayTexto("Ya tienes algo en marcha") }
        assertNotNull("preguntar no toca la sesión", leerRegistro())
        assertFalse("la respiración aún no ha empezado", hay("r-empezar"))

        // «Seguir con Informe» vuelve a la sesión tal como va.
        pulsarTexto("Seguir con Informe")
        hasta { hay("ses-b-foco") }
        assertNotNull(leerRegistro())
        assertEquals(Tramo.Foco(1, 25), container.motorSesion.instante.value?.actual)
    }

    @Test
    fun `breathing from the card with a live session lets you leave it as an attempt and opens the breathing`() {
        empezarDirecto(bloques = 2)
        montar()
        hasta { hay("inicio") }
        irA(7 * MINUTO)

        EntradaSesion.pedir(Destino.Respirar)
        hasta { hayTexto("Ya tienes algo en marcha") }
        pulsarTexto("Dejarla y empezar esta")

        hasta { hay("r-empezar") }
        hasta { leerRegistro() == null }
        hasta { hayTexto("Informe: queda como intento.") }
        val fila = runBlocking { container.database.sesionDao().all() }.single()
        assertEquals(ResultadoSesion.CAMBIADA.name, fila.resultado)
        assertEquals(7, fila.minutosFoco)
        val intentos = estado().taskEvents.filter { it.taskId == "t1" && it.kind == TaskEventKind.ATTEMPT }
        assertEquals("dejarla apunta un intento", 1, intentos.size)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        assertEquals(TaskStatus.OPEN, runBlocking { container.tasks.task("t1") }?.status)
    }

    // ───────────────────────── más casos que la spec exige claramente ─────────────────────────

    @Test
    fun `finishing from the focus pays the task once and leaves nothing behind`() {
        empezarDirecto(bloques = 2)
        hasta { fondo.sonando.value == SessionBackground.RAIN }
        arrancarServicio()
        montar()
        hasta { hay("inicio") }
        NavRequests.open("focus")
        hasta { hay("ses-b-foco") }
        irA(12 * MINUTO)

        pulsar("f-hecho")
        hasta { hayTexto("Informe, hecho.") }
        val libro = estado().pointsLedger.filter { it.reason == PointsReason.TASK_DONE }
        assertEquals("se marca y se paga una sola vez", 1, libro.size)
        assertEquals(5, libro.single().delta)
        assertEquals(TaskStatus.DONE, runBlocking { container.tasks.task("t1") }?.status)
        val fila = runBlocking { container.database.sesionDao().all() }.single()
        assertEquals(12, fila.minutosFoco)
        assertEquals(ResultadoSesion.HECHA.name, fila.resultado)
        assertNull(leerRegistro())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        assertFalse(ServicioSesion.vivo.get())
        assertNull(shadowOf(nm).getNotification(Notifier.FOCUS_ID))
        assertTrue(alarmasDeSesion().isEmpty())
        assertNull(fondo.sonando.value)
    }

    @Test
    fun `the session ends with the app out and a time up notice stays until the app is opened`() {
        empezarDirecto(bloques = 1)
        hasta { fondo.sonando.value == SessionBackground.RAIN }
        arrancarServicio()
        segundoPlano()
        irA(25 * MINUTO + SEGUNDO)
        lanzarReceptor()

        hasta { textoAviso() == "Se acabó el tiempo" }
        val fin = shadowOf(nm).getNotification(Notifier.FOCUS_ID)
        assertEquals("«Se acabó el tiempo» suena por el canal de Habi", NotificationChannels.HABI, fin.channelId)
        assertEquals("No molestar vuelve como estaba", NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        assertTrue("sin alarma de la sesión", alarmasDeSesion().isEmpty())
        assertFalse("el servicio se suelta al acabar el tiempo", ServicioSesion.vivo.get())
        assertNull("el fondo calla", fondo.sonando.value)

        // Al abrir, Terminada espera la respuesta sin caducar (§7.6).
        primerPlano()
        montar()
        hasta { hay("inicio") }
        NavRequests.open("focus")
        hasta { hay("ses-b-fin") }
        assertNotNull(leerRegistro())
    }

    // ───────────────────────── el foco, el descanso y Terminada por dentro (§7.4 a §7.6) ─────────────────────────

    @Test
    fun `pausing the focus silences the background, says paused in the notice, leaves no alarm and resuming puts it all back`() {
        abrirSesionViva(bloques = 2)
        hasta { fondo.sonando.value == SessionBackground.RAIN }
        irA(5 * MINUTO)
        tic()

        pulsar("f-pausa")
        hasta { textoAviso()?.startsWith("En pausa") == true }
        assertNull("el fondo se funde a 0 en la pausa", fondo.sonando.value)
        assertTrue("en pausa no hay frontera: sin alarma", alarmasDeSesion().isEmpty())

        // La pausa no tiene límite y no cuenta: 10 min parados.
        irA(15 * MINUTO)
        tic()
        pulsar("f-pausa")
        hasta { textoAviso() == "En marcha" }
        assertEquals(SessionBackground.RAIN, fondo.sonando.value)
        assertAlarma(cero + 35 * MINUTO)
        assertEquals(
            "quedan 20 min de los 25",
            (20 * MINUTO).toDouble(),
            container.motorSesion.instante.value!!.restaMillis.toDouble(),
            2.0 * SEGUNDO,
        )
    }

    @Test
    fun `adding minutes to the focus moves the next boundary alarm`() {
        abrirSesionViva(bloques = 2)
        irA(5 * MINUTO)
        tic()

        pulsar("f-mas-5")
        hasta { alarmasDeSesion().singleOrNull()?.triggerAtTime?.let { it > cero + 26 * MINUTO } == true }
        assertAlarma(cero + 30 * MINUTO)
        assertEquals(
            "quedan 25 min",
            (25 * MINUTO).toDouble(),
            container.motorSesion.instante.value!!.restaMillis.toDouble(),
            2.0 * SEGUNDO,
        )
    }

    @Test
    fun `skipping the break goes to the next block with its own boundary alarm`() {
        abrirSesionViva(bloques = 2)
        irA(26 * MINUTO)
        tic()
        hasta { hay("ses-b-descanso") }

        val marca = marcaSonidos()
        pulsar("d-saltar")
        hasta { hay("ses-b-foco") }
        assertEquals(Tramo.Foco(2, 25), container.motorSesion.instante.value?.actual)
        assertSuena(marca, Snd.BROTE, Haptica.TICK, "saltar el descanso")
        assertEquals(SessionBackground.RAIN, fondo.sonando.value)
        assertAlarma(SystemClock.elapsedRealtime() + 25 * MINUTO)
    }

    @Test
    fun `leaving the focus because of a setback keeps the task open, counts the minutes and leaves nothing behind`() {
        abrirSesionViva(bloques = 2)
        irA(9 * MINUTO)
        tic()
        val libroAntes = estado().pointsLedger.size

        pulsar("f-dejar")
        hasta { hayTexto("Lo dejamos aquí. Tus 9 min cuentan.") }
        assertNull(leerRegistro())
        assertEquals("sin marcar: la tarea sigue abierta", TaskStatus.OPEN, runBlocking { container.tasks.task("t1") }?.status)
        assertEquals("sin bitos por dejarlo", libroAntes, estado().pointsLedger.size)
        assertEquals("una tarea apunta un intento", 1, estado().taskEvents.count { it.taskId == "t1" && it.kind == TaskEventKind.ATTEMPT })
        val fila = runBlocking { container.database.sesionDao().all() }.single()
        assertEquals(ResultadoSesion.LO_DEJO.name, fila.resultado)
        assertEquals(9, fila.minutosFoco)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        assertNull(shadowOf(nm).getNotification(Notifier.FOCUS_ID))
        assertTrue(alarmasDeSesion().isEmpty())
        assertNull(fondo.sonando.value)
    }

    @Test
    fun `another five minutes starts a new direct block on the same task and background`() {
        abrirSesionViva(bloques = 1)
        irA(25 * MINUTO + SEGUNDO)
        tic()
        hasta { hay("ses-b-fin") }

        pulsar("t-otra")
        hasta { leerRegistro()?.plan?.minutosFoco == 5 }
        val nueva = leerRegistro()!!
        assertEquals(Objetivo.Tarea("t1"), nueva.plan.objetivo)
        assertEquals(5, nueva.plan.minutosFoco)
        assertEquals(1, nueva.plan.bloques)
        assertEquals(Entrada.DIRECTO, nueva.plan.entrada)
        assertEquals(SessionBackground.RAIN, nueva.plan.fondo)
        hasta { fondo.sonando.value == SessionBackground.RAIN }
        cero = nueva.anclaElapsed
        hasta { alarmasDeSesion().isNotEmpty() }
        assertAlarma(cero + 5 * MINUTO)
        assertEquals(TaskStatus.OPEN, runBlocking { container.tasks.task("t1") }?.status)
        // «Directo a Foco»: la pantalla se queda en la sesión nueva, no vuelve a Hoy por el instante en que la vieja se cerró.
        fotogramas(1_000)
        assertTrue("la pantalla sigue en Foco; en pantalla: ${etiquetas()}", hay("ses-b-foco"))
    }

    // ───────────────────────── ayudantes ─────────────────────────

    /** Empieza una sesión directa por el motor y abre Hoy en ella, como al volver por el aviso. */
    private fun abrirSesionViva(bloques: Int) {
        empezarDirecto(bloques)
        montar()
        hasta { hay("inicio") }
        NavRequests.open("focus")
        hasta { hay("ses-b-foco") }
    }

    /**
     * El fondo de verdad no puede sonar en Robolectric (no abre `SoundPool` ni `MediaPlayer`): se cambia por el grabador,
     * que sí dice `sonando` al instante. Hay que hacerlo antes de que nadie pida el motor, que lo toma al crearse.
     */
    private fun conFondoGrabado(c: AppContainer): FondoGrabado {
        val grabado = FondoGrabado()
        ponerPerezoso(c, "fondo", grabado)
        ponerPerezoso(c, "sonido", sonido)
        ponerPerezoso(c, "tacto", tacto)
        return grabado
    }

    private fun ponerPerezoso(
        c: AppContainer,
        nombre: String,
        valor: Any,
    ) {
        val campo = AppContainer::class.java.getDeclaredField("$nombre\$delegate").apply { isAccessible = true }
        campo.set(c, lazyOf(valor))
    }

    private fun montar(contenedor: AppContainer = container) {
        compose.setContent {
            BitoTheme {
                CompositionLocalProvider(LocalSondaJardin provides sonda) {
                    BitoNavHost(contenedor)
                }
            }
        }
        // La sesión pide un fotograma tras otro (respirar, Habi): con el reloj solo, la prueba no llegaría nunca a estar quieta.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)
    }

    /** Empieza una sesión directa de 25 min con la tarea, por el motor, antes de montar la pantalla. */
    private fun empezarDirecto(bloques: Int) {
        runBlocking {
            container.settings.update { it.copy(sessionEntry = Entrada.DIRECTO) }
            val motor = container.motorSesion
            motor.preparar(Destino.Tarea("t1"))
            motor.cambiarBorrador(motor.borrador.value!!.copy(bloques = bloques))
            cero = SystemClock.elapsedRealtime()
            assertNull(motor.empezar())
        }
    }

    /**
     * Mata el proceso: la sesión queda en el disco y se levanta un contenedor nuevo sobre una copia de ese disco (un
     * contenedor no puede abrir dos veces el mismo DataStore). Android se lleva las alarmas, el aviso y el sonido.
     */
    private fun procesoNuevo(): AppContainer {
        val viejo = container
        viejo.cerrarSesion()
        fondo.callar(seco = true)
        viejo.aire.parar()
        // El disco queda al día (el proceso viejo no se cierra: la app de prueba sigue usando su contenedor para los títulos).
        viejo.database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        val destino = tmp.newFolder("proceso2")
        File(app.filesDir, "").listFiles().orEmpty().filter { it.isFile }.forEach { it.copyTo(File(destino, it.name), overwrite = true) }
        val bases = File(destino, "databases").also { it.mkdirs() }
        app.getDatabasePath("x").parentFile?.listFiles().orEmpty().filter { it.isFile }.forEach {
            it.copyTo(File(bases, it.name), overwrite = true)
        }
        AlarmaSesion.cancelar(app)
        nm.cancelAll()
        ServicioSesion.vivo.set(false)
        val aparte =
            object : ContextWrapper(app) {
                override fun getFilesDir(): File = destino

                override fun getDatabasePath(name: String): File = File(bases, name)
            }
        val nuevo = AppContainer(aparte).also { contenedoresAparte += it }
        fondo = conFondoGrabado(nuevo)
        return nuevo
    }

    /** Pone el reloj a [ms] desde Empezar (solo hacia delante). */
    private fun irA(ms: Long) {
        SystemClock.setCurrentTimeMillis(cero + ms)
    }

    private fun tic() {
        runBlocking { (container.motorSesion as MotorSesionAndroid).tic() }
        fotogramas()
    }

    private fun fotogramas(ms: Long = 50) = compose.mainClock.advanceTimeBy(ms)

    private fun segundoPlano() {
        AppVisibility.visible = false
    }

    /** Volver a la app: lo que hace el Inicio al llegar a primer plano, que es recuperar la sesión del disco. */
    private fun primerPlano() {
        AppVisibility.visible = true
        runBlocking { container.motorSesion.recuperar() }
        fotogramas()
    }

    /** Lanza el receptor con la alarma que hay programada, como lo haría Android al sonar. */
    private fun lanzarReceptor() {
        val alarma = alarmasDeSesion().firstOrNull() ?: error("no hay alarma de la sesión programada")
        val intent = shadowOf(alarma.operation).savedIntent
        // Lo que hace `onReceive` sin `goAsync` (Robolectric no lo da): leer el almacén, avanzar, avisar y reprogramar.
        val tramo = intent.getIntExtra(AlarmaSesion.EXTRA_TRAMO, -1)
        runBlocking { SesionReceiver.procesar(PuertaFuera.entornoDe(app), PuertaFuera.audioDe(app), tramo) }
        fotogramas()
        hasta { !alarmaViva(alarma) }
    }

    private fun alarmaViva(alarma: ShadowAlarmManager.ScheduledAlarm) = alarmasDeSesion().any { it.triggerAtTime == alarma.triggerAtTime }

    @Suppress("DEPRECATION")
    private fun alarmasDeSesion(): List<ShadowAlarmManager.ScheduledAlarm> =
        alarmas.scheduledAlarms.filter { a ->
            a.operation?.let { shadowOf(it).savedIntent?.component?.className == SesionReceiver::class.java.name } == true
        }

    private fun assertAlarma(disparo: Long) {
        val lista = alarmasDeSesion()
        assertEquals("una sola alarma de la sesión", 1, lista.size)
        assertEquals("tipo ELAPSED_REALTIME_WAKEUP", AlarmManager.ELAPSED_REALTIME_WAKEUP, lista.single().type)
        assertEquals("la alarma es la de la siguiente frontera", disparo.toDouble(), lista.single().triggerAtTime.toDouble(), 2.0 * SEGUNDO)
    }

    /** El servicio, creado con el aviso que la app le pasó al arrancarlo (Robolectric no lo crea solo). */
    private fun arrancarServicio() {
        hasta { shadowOf(app).peekNextStartedService() != null || ServicioSesion.vivo.get() }
        while (true) {
            val intent = shadowOf(app).nextStartedService ?: break
            if (intent.component?.className != ServicioSesion::class.java.name) continue
            servicios += Robolectric.buildService(ServicioSesion::class.java, intent).create().also { it.startCommand(0, 1) }
        }
    }

    private fun textoAviso(): String? = shadowOf(nm).getNotification(Notifier.FOCUS_ID)?.extras?.getString(Notification.EXTRA_TEXT)

    private fun assertAviso(
        titulo: String,
        texto: String,
    ) {
        hasta { textoAviso() == texto }
        val aviso = shadowOf(nm).getNotification(Notifier.FOCUS_ID)
        assertEquals(NotificationChannels.SESION, aviso.channelId)
        assertEquals(titulo, aviso.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(texto, aviso.extras.getString(Notification.EXTRA_TEXT))
    }

    private fun marcaSonidos() = sonido.sonidos.size to tacto.hapticas.size

    private fun assertSuena(
        desde: Pair<Int, Int>,
        snd: Snd,
        haptica: Haptica?,
        cuando: String,
    ) {
        val sonaron = sonido.sonidos.drop(desde.first)
        assertTrue("$cuando: suena $snd (sonó $sonaron)", snd in sonaron)
        val vibraron = tacto.hapticas.drop(desde.second)
        if (haptica != null) assertTrue("$cuando: vibra $haptica (vibró $vibraron)", haptica in vibraron)
    }

    private fun estado() = runBlocking { container.domainState.snapshot() }

    private fun leerRegistro(): RegistroSesion? = runBlocking { container.sesionStore.leer() }

    private fun bitos(): Int = estado().pointsLedger.sumOf { it.delta }

    private fun entradasDe(habito: String): List<Int> = estado().entries.filter { it.habitId == habito }.map { it.logicalDay }

    private fun hoy(): Int = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneId.systemDefault())

    /** Espera a que se cumpla [cond] dejando correr fotogramas: Room y DataStore van por hilos reales. */
    private fun hasta(cond: () -> Boolean) {
        repeat(ESPERA_MAXIMA) {
            if (cond()) return
            fotogramas(16)
            Thread.sleep(1)
        }
        assertTrue("no llegó a tiempo; en pantalla: ${etiquetas()}", cond())
    }

    private fun etiquetas(): List<String> =
        compose.onAllNodes(SemanticsMatcher("con etiqueta") { SemanticsProperties.TestTag in it.config }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }

    private fun hay(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hayTexto(texto: String) = compose.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun pulsar(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        fotogramas()
    }

    private fun pulsarTexto(texto: String) {
        compose.onNodeWithText(texto).fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        fotogramas()
    }
}
