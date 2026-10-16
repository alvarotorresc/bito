package com.alvarotc.bito.ui.inicio

import android.app.AlarmManager
import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.ContenedoresDeSesion
import com.alvarotc.bito.data.db.HablaDao
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.habla.FuentePropuestasHabi
import com.alvarotc.bito.data.habla.LoGordoRepository
import com.alvarotc.bito.data.habla.MemoriaRepository
import com.alvarotc.bito.data.habla.PermisosAvisos
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.camino.CaminoViewModel
import com.alvarotc.bito.ui.cierre.CierreViewModel
import com.alvarotc.bito.ui.garden.LocalSondaJardin
import com.alvarotc.bito.ui.garden.SondaJardin
import com.alvarotc.bito.ui.garden.habiBodyRect
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.notifications.AvisosHabiAndroid
import com.alvarotc.bito.ui.notifications.QuickActionUseCase
import com.alvarotc.bito.ui.notifications.ReminderScheduler
import com.alvarotc.bito.ui.notifications.SlotKind
import com.alvarotc.bito.ui.notifications.planDeHoyDe
import com.alvarotc.bito.ui.premios.CelebracionesViewModel
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.MotorSonidoMudo
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.TodayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Lo que solo se ve junto (Tarea 32): el motor de m1, la fuente y el redactor de m2, los avisos de a y las pantallas de
 * u1 y u2 con el catálogo real, sobre la base de datos y los ajustes de un `AppContainer` y un reloj falso.
 *
 * Las fábricas de la app leen el reloj del sistema, así que aquí los VM se montan a mano sobre las piezas reales del
 * contenedor; el `NavHost` (rutas, peticiones y la ficha) se prueba aparte en `HabiConduceRutasE2ETest`. Mover [ahora]
 * no hace emitir a la fuente: cada paso de reloj monta piezas nuevas (como abrir la app otra vez).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp", application = Application::class)
class HabiConduceE2ETest {
    @get:Rule
    val sesiones = ContenedoresDeSesion()

    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val utc: ZoneId = ZoneOffset.UTC
    private lateinit var c: AppContainer

    /** El día de hoy de verdad (UTC): la memoria guarda los usos de los últimos 60 días desde el reloj del sistema. */
    private val dia = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, ZoneOffset.UTC)
    private var ahora = en(dia, 10)

    private fun en(
        d: Int,
        h: Int,
        m: Int = 0,
    ): Long =
        LocalDate
            .ofEpochDay(d.toLong())
            .atTime(h, m)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

    private val sonda = SondaJardin()
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private var store = ViewModelStore()

    // Las piezas de cada «apertura» de la app.
    private lateinit var memoria: MemoriaRepository
    private lateinit var gordo: LoGordoRepository
    private lateinit var avisos: AvisosHabiAndroid
    private lateinit var fuente: FuentePropuestasHabi
    private lateinit var hoy: TodayViewModel
    private lateinit var cierre: CierreViewModel
    private lateinit var camino: CaminoViewModel
    private lateinit var celebraciones: CelebracionesViewModel

    // Lo que monta la composición: cada ronda, unas piezas nuevas; [conCierre] es la ruta `today?cierre=true`.
    private var ronda by mutableIntStateOf(0)
    private var conCierre by mutableStateOf(false)

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        c = sesiones.registrar(AppContainer(app))
        runBlocking { c.settings.update { it.copy(onboardingDone = true, userName = "Álvaro") } }
    }

    // La base de datos no se cierra (como en BitoNavHostTest): lo que aún escucha al acabar no debe encontrarla cerrada.
    @After
    fun tearDown() = store.clear()

    private inline fun <reified T : ViewModel> vm(noinline crear: () -> T): T =
        ViewModelProvider(store, viewModelFactory { initializer { crear() } })[T::class.java]

    /** Unas piezas nuevas sobre los mismos datos, con el reloj en [ahora]: el proceso o el VM recién creados. */
    private fun abrir(conPantallas: Boolean = true) {
        store.clear()
        store = ViewModelStore()
        memoria = MemoriaRepository(c.database) { ahora }
        gordo = LoGordoRepository(c.database) { ahora }
        avisos =
            AvisosHabiAndroid(
                app,
                contenedor = { c },
                plan = { planDeHoyDe(c, ahora, utc) },
                fuente = { fuente },
                aceptados = { c.avisosAceptados },
                catalogo = { c.catalogoHabla },
            )
        val registrar = QuickActionUseCase(c.journal, c.reconciler, c.domainState, c.habits, c.settings)
        fuente =
            FuentePropuestasHabi(
                estado = c.domainState.observe(),
                ajustes = c.settings.settings,
                recordatorios = flowOf(emptyMap()),
                memoria = memoria,
                vigentes = c.propuestaVigente,
                gordo = gordo,
                aceptados = c.avisosAceptados,
                catalogo = { c.catalogoHabla },
                avisos = avisos,
                permisos = { PermisosAvisos(notificaciones = true, alarmasExactas = true) },
                now = { ahora },
                zone = { utc },
                registrar = { id, n -> registrar.log(id, n) },
            )
        if (!conPantallas) return
        hoy =
            vm {
                TodayViewModel(
                    c.domainState,
                    c.habits,
                    c.journal,
                    c.settings,
                    c.reconciler,
                    c.rewards,
                    tasks = c.tasks,
                    now = { ahora },
                    zone = { utc },
                    propuestas = fuente,
                    gordo = gordo,
                    aceptados = c.avisosAceptados,
                    memoria = memoria,
                )
            }
        camino = vm { CaminoViewModel(c.domainState, c.settings, c.rewards, { ahora }, { utc }, propuestas = fuente) }
        celebraciones = vm { CelebracionesViewModel(c.domainState, c.rewards, c.settings) }
        cierre =
            vm {
                CierreViewModel(
                    c.domainState,
                    c.habits,
                    c.journal,
                    c.settings,
                    c.reconciler,
                    c.rewards,
                    HabiSounds(MotorSonidoMudo),
                    memoria,
                    gordo,
                    c.avisosAceptados,
                    avisos,
                    fuente,
                    { ahora },
                    { utc },
                )
            }
    }

    private val navegar =
        NavegacionInicio(
            abrirFicha = {},
            abrirAjustes = {},
            abrirTienda = {},
            // Como el NavHost: `today?cierre=true` vuelve a montar el Inicio con el cierre abierto.
            abrirRepaso = {
                conCierre = true
                ronda++
            },
            abrirTareas = {},
            crearHabito = {},
            crearTarea = {},
        )

    private fun montar() {
        abrir()
        rule.setContent {
            ConSistema(sonido, tacto) {
                CompositionLocalProvider(LocalSondaJardin provides sonda) {
                    key(ronda) {
                        InicioScreen(
                            hoy,
                            camino,
                            celebraciones,
                            navegar,
                            enPrimerPlano = true,
                            cierre = conCierre,
                            cierreViewModel = cierre,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /** Otra apertura de la app con el reloj en [nuevo]: piezas nuevas sobre los mismos datos. */
    private fun reabrir(nuevo: Long = ahora) {
        ahora = nuevo
        conCierre = false
        abrir()
        ronda++
        rule.waitForIdle()
    }

    private fun hay(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(
        tag: String,
        ms: Long = 10_000,
    ) = hasta(ms) { hay(tag) }

    /**
     * Espera a [cond] dejando correr el bucle principal: la fuente lee Room en hilos reales y vuelve al principal, que
     * un `waitUntil` a secas no hace avanzar (como `esperarRuta` en `BitoNavHostTest`).
     */
    private fun hasta(
        ms: Long = 10_000,
        cond: () -> Boolean,
    ) {
        val limite = System.currentTimeMillis() + ms
        while (!cond()) {
            check(System.currentTimeMillis() < limite) { "no llegó a tiempo" }
            rule.waitForIdle()
            Thread.sleep(20)
        }
    }

    private fun esperarPropuesta(prefijo: String): Propuesta {
        runCatching { hasta { hoy.propuesta.value?.id?.startsWith("$prefijo:") == true } }
            .onFailure { error("esperaba $prefijo y la tarjeta tiene ${hoy.propuesta.value?.id}") }
        return hoy.propuesta.value!!
    }

    private fun tocar(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()

    private fun habito(
        id: String,
        nombre: String,
        creado: Int = dia - 10,
    ) = runBlocking {
        c.habits.create(habitEntity(id = id, name = nombre, metric = Metric.CHECK, target = 1, unit = null, createdOnDay = creado))
    }

    private fun registro(
        habitId: String,
        d: Int,
    ) = runBlocking { c.database.entryDao().insert(entryEntity(id = "$habitId-$d", habitId = habitId, logicalDay = d, value = 1)) }

    /** Fallado ayer y cumplido los tres días anteriores (spec §5.1 n.º 8). */
    private fun escapadoAyer(
        id: String,
        nombre: String,
    ) {
        habito(id, nombre)
        (2..4).forEach { registro(id, dia - it) }
    }

    private fun alarmasAceptado(): List<String> {
        val am = app.getSystemService(AlarmManager::class.java)
        return shadowOf(am).scheduledAlarms.mapNotNull { a ->
            val i = shadowOf(a.operation).savedIntent
            i.getStringExtra(
                ReminderScheduler.EXTRA_KEY,
            ).takeIf { i.getStringExtra(ReminderScheduler.EXTRA_KIND) == SlotKind.ACEPTADO.name }
        }
    }

    private fun dao(): HablaDao = c.database.hablaDao()

    // 1 y 2: «Vale, a las…», la confirmación, la hora en la fila y su alarma; marcar desde la lista lo cierra todo.

    @Test
    fun `a habit that slipped yesterday gets Vale at an hour, its row and its alarm, and marking it closes both`() {
        escapadoAyer("leer", "Leer")
        montar()
        val p = esperarPropuesta("AYER_SE_ESCAPO")
        assertNotNull("propone una hora", p.hora)
        esperar("habi-vale")
        tocar("habi-vale")

        val confirma = esperarPropuesta("CONFIRMA_VALE")
        assertEquals(p.hora, confirma.hora)
        esperar("hora-leer")
        hasta { alarmasAceptado().size == 1 }
        val aceptado = runBlocking { c.avisosAceptados.observeDe(dia).first() }.single()
        assertEquals(EstadoAviso.PENDIENTE, aceptado.estado)
        assertEquals(listOf(aceptado.id), alarmasAceptado())

        // Marcar desde la lista: la tarjeta se va sin texto (la vigente se borra y se emite null) y la alarma se cancela.
        val vistas = mutableListOf<Propuesta?>()
        val escucha = CoroutineScope(Dispatchers.Default).async { hoy.propuesta.collect { vistas += it } }
        tocar("circulo-leer")
        hasta { alarmasAceptado().isEmpty() }
        hasta { hoy.propuesta.value?.id?.startsWith("CONFIRMA_VALE:") != true }
        escucha.cancel()
        assertTrue(
            "la confirmación deja paso a un null: $vistas",
            vistas.dropWhile {
                it?.id?.startsWith("CONFIRMA_VALE:") == true
            }.firstOrNull() == null,
        )
    }

    // 3: lo gordo desde la tarjeta.

    @Test
    fun `choosing the big thing from the card saves it, tags the rows and the card says it was chosen`() {
        habito("meditar", "Meditar")
        habito("agua", "Beber agua")
        runBlocking {
            c.tasks.create(
                taskEntity(id = "banco", title = "Llamar al banco", dueKind = DueKind.DATE, dueDay = dia, createdOnDay = dia - 1),
            )
        }
        montar()
        esperarPropuesta("ELEGIR_GORDO")
        esperar("habi-elegir_gordo")
        tocar("habi-elegir_gordo")
        esperar("hoja-gordo")
        esperar("gordo-banco")
        // La hoja ya trae marcada la sugerencia de Habi: la tarea que vence hoy.
        rule.onNodeWithTag("gordo-banco").assertIsOn()
        tocar("hoja-gordo-guardar")

        esperarPropuesta("GORDO_ELEGIDO")
        esperar("gordo-etiqueta-banco")
        assertEquals(listOf(RefGordo(TipoRefGordo.TAREA, "banco")), runBlocking { gordo.observe(listOf(dia)).first()[dia] })
    }

    // 4 y 5: el cierre de las 21:30 con «Duro», Hoy de noche y el día duro de mañana.

    @Test
    fun `at night the card opens the close, good night seals, until tomorrow leaves Today at night, and a hard day follows`() {
        habito("meditar", "Meditar")
        habito("agua", "Beber agua")
        runBlocking {
            c.tasks.create(
                taskEntity(id = "banco", title = "Llamar al banco", dueKind = DueKind.DATE, dueDay = dia, createdOnDay = dia - 1),
            )
        }
        ahora = en(dia, 21, 30)
        montar()
        esperarPropuesta("CERRAR_DIA")
        esperar("habi-cerrar_dia")
        tocar("habi-cerrar_dia")

        esperar("cierre-buenas-noches")
        tocar("cierre-animo-2")
        tocar("cierre-buenas-noches")
        rule.mainClock.advanceTimeBy(2_600)
        hasta { runBlocking { c.domainState.observe().first() }.daySeals.any { it.logicalDay == dia } }
        esperar("cierre-hasta-manana")
        tocar("cierre-hasta-manana")
        hasta { !hay("cierre") }

        // Hoy de noche: tarjeta NOCHE, tareas con «mañana», el jardín de noche y las filas selladas.
        esperarPropuesta("NOCHE")
        esperar("manana-banco")
        hasta { sonda.noche() == 1f }
        val antes = runBlocking { c.database.entryDao().all() }.size
        tocar("circulo-meditar")
        rule.waitForIdle()
        assertEquals("una fila sellada no marca", antes, runBlocking { c.database.entryDao().all() }.size)

        // Tocar a Habi dormido (G17): se aplasta y no salta.
        val saltos = sonda.saltosHabi()
        val aplastados = sonda.aplastadosHabi()
        rule.onNodeWithTag("garden-scene", useUnmergedTree = true).performTouchInput {
            val cuerpo = habiBodyRect(width.toFloat(), height.toFloat())
            click(Offset(cuerpo.left + cuerpo.width / 2f, cuerpo.top + cuerpo.height / 2f))
        }
        hasta { sonda.aplastadosHabi() > aplastados }
        assertEquals("dormido no salta", saltos, sonda.saltosHabi())

        // Al día siguiente, tras «Duro»: el día duro y «Esperan sin prisa».
        reabrir(en(dia + 1, 10))
        esperarPropuesta("DIA_DURO")
        esperar("esperan-sin-prisa")
    }

    // B16 junto: «Hecho» en la vuelta hace saltar a Habi una vez, con su voz, a los 1,1 s.

    @Test
    fun `done on the return makes Habi jump once with its voice`() {
        habito("agua", "Beber agua", creado = dia - 30)
        (8..12).forEach { registro("agua", dia - it) }
        montar()
        esperarPropuesta("VUELTA")
        esperar("habi-hecho")
        val antes = sonda.saltosHabi()
        val voces = sonido.snds().count { it == Snd.HABI }
        tocar("habi-hecho")
        hasta { sonda.saltosHabi() > antes }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals("un solo salto", antes + 1, sonda.saltosHabi())
        assertEquals("una sola voz", voces + 1, sonido.snds().count { it == Snd.HABI })
    }

    // 8: la misma situación el mismo día da la misma frase, con el VM recreado y aunque se pierda la vigente.

    @Test
    fun `the same situation on the same day says the same sentence after recreating the view model`() {
        escapadoAyer("leer", "Leer")
        montar()
        val primera = esperarPropuesta("AYER_SE_ESCAPO")
        val texto = primera.texto as TextoHabi.Frase

        // El VM muere y vuelve (la vigente sigue guardada): la misma frase.
        reabrir()
        val otra = esperarPropuesta("AYER_SE_ESCAPO")
        assertEquals(texto, otra.texto)

        // Sin la vigente y sin los usos que dejó: el redactor vuelve a sacar lo mismo con la semilla del día.
        runBlocking {
            dao().vaciarVigentes()
            dao().vaciarUsos()
        }
        reabrir()
        val redactada = esperarPropuesta("AYER_SE_ESCAPO")
        assertEquals(texto.trozos, (redactada.texto as TextoHabi.Frase).trozos)
    }

    // A11: con el cerrojo de la fuente cogido, replanificar, cancelar y registrar no vuelven a la fuente.

    @Test
    fun `answering with the lock held never comes back to the source`() {
        escapadoAyer("leer", "Leer")
        habito("agua", "Beber agua")
        ahora = en(dia, 13, 30)
        abrir(conPantallas = false)

        class Hecho<T>(
            val valor: T,
        )

        // Fuera del hilo principal y con tope: un interbloqueo con el cerrojo (que va en NonCancellable) haría fallar
        // el test en vez de colgar la suite.
        fun <T> sinBloqueo(
            que: String,
            bloque: suspend () -> T,
        ): T =
            runBlocking {
                val trabajo = CoroutineScope(Dispatchers.Default).async { Hecho(bloque()) }
                (withTimeoutOrNull(10_000) { trabajo.await() } ?: error("$que se quedó esperando al cerrojo de la fuente")).valor
            }

        val p = sinBloqueo("la primera propuesta") { fuente.propuesta(Ambito.HOY).first { it != null }!! }
        // «Vale»: crea el aceptado y replanifica (AvisosHabiAndroid de verdad) dentro del cerrojo.
        sinBloqueo("«Vale»") { fuente.responder(p.id, Respuesta.VALE) }
        val confirma = sinBloqueo("la confirmación") { fuente.propuesta(Ambito.HOY).first { it != null }!! }
        // «Otra hora»: cancela el viejo (cancelarAceptados) y replanifica.
        sinBloqueo("«Otra hora»") { fuente.responder(confirma.id, Respuesta.OTRA_HORA, p.hora!! + 30) }
        assertEquals("la alarma del aceptado, movida", 1, alarmasAceptado().size)
        // Hecho desde la lista: la vigente (la confirmación de Leer) pasa a HECHA y replanifica con el cerrojo cogido.
        registro("leer", dia)
        sinBloqueo("la propuesta tras marcar") { fuente.propuesta(Ambito.HOY).first() }
        assertTrue(alarmasAceptado().isEmpty())
        // Lo gordo: guarda y replanifica.
        sinBloqueo("guardar lo gordo") { fuente.guardarGordo(listOf(RefGordo(TipoRefGordo.HABITO, "agua"))) }
    }
}
