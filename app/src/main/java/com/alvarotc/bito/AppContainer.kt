package com.alvarotc.bito

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habla.AvisosAceptadosRepository
import com.alvarotc.bito.data.habla.CatalogoRecursos
import com.alvarotc.bito.data.habla.FuentePropuestasHabi
import com.alvarotc.bito.data.habla.LoGordoRepository
import com.alvarotc.bito.data.habla.MemoriaRepository
import com.alvarotc.bito.data.habla.PermisosAvisos
import com.alvarotc.bito.data.habla.PropuestaVigenteStore
import com.alvarotc.bito.data.habla.VueltaAlRegistrar
import com.alvarotc.bito.data.habla.ticksDeMinuto
import com.alvarotc.bito.data.repo.BreathingRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.sesion.SesionStore
import com.alvarotc.bito.data.sesion.SesionesRepository
import com.alvarotc.bito.data.sesion.ahoraDelSistema
import com.alvarotc.bito.data.sesion.tareasEmpezadasHoy
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Sealing
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Contextos
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.sesion.Aire
import com.alvarotc.bito.sesion.EfectosSesion
import com.alvarotc.bito.sesion.Fondo
import com.alvarotc.bito.sesion.FueraDeLaApp
import com.alvarotc.bito.sesion.MotorSesion
import com.alvarotc.bito.sesion.MotorSesionAndroid
import com.alvarotc.bito.sesion.NoMolestar
import com.alvarotc.bito.sesion.fuera.crearFuera
import com.alvarotc.bito.sesion.sonido.crearAire
import com.alvarotc.bito.sesion.sonido.crearFondo
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.notifications.AvisosHabi
import com.alvarotc.bito.ui.notifications.AvisosHabiAndroid
import com.alvarotc.bito.ui.notifications.QuickActionUseCase
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.crearTacto
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.sistema.sonido.crearMotorSonido
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.ZoneId

/** Hand-built DI (tech doc §1.4: no framework — constructors). */
class AppContainer(context: Context) {
    val database: BitoDatabase = BitoDatabase.build(context)
    val sesiones = SesionesRepository(database)
    val habits = HabitsRepository(database, sesiones)
    val rewards = RewardsRepository(database)
    val domainState = DomainStateRepository(database)
    val tasks = TasksRepository(database, sesiones)
    val breathing = BreathingRepository(database)
    val memoria = MemoriaRepository(database)
    val propuestaVigente = PropuestaVigenteStore(database)
    val loGordo = LoGordoRepository(database)
    val avisosAceptados = AvisosAceptadosRepository(database)
    val settings =
        SettingsRepository(
            PreferenceDataStoreFactory.create {
                context.filesDir.resolve("settings.preferences_pb")
            },
        )

    // El diario anota la vuelta con el primer registro tras la ausencia, venga de donde venga.
    val journal =
        JournalRepository(
            database,
            VueltaAlRegistrar(
                estado = { domainState.snapshot() },
                memoria = memoria,
                corteDia = { settings.settings.first().dayCutoffMinutes },
            ),
        )

    // Un solo DataStore por archivo: la sesión (`focus.preferences_pb`) es el único dueño de ese archivo.
    private val focusDataStore =
        PreferenceDataStoreFactory.create {
            context.filesDir.resolve("focus.preferences_pb")
        }

    // Todo lo vivo de la sesión (el tic del motor, los colectores de ajustes del audio, lo de fuera) cuelga de este alcance:
    // [cerrarSesion] lo cancela entero, sin que nada quede corriendo para otro contenedor.
    private val alcanceSesion = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Para todo lo que corre en segundo plano por la sesión. Los tests lo llaman al acabar. */
    fun cerrarSesion() = alcanceSesion.cancel()

    // La sesión (pieza 5). Perezosos: el arranque no los toca.
    val sesionStore: SesionStore by lazy { SesionStore(focusDataStore) }
    val noMolestar: NoMolestar by lazy { NoMolestar(context.applicationContext) }
    val aire: Aire by lazy { crearAire(context.applicationContext, sonido, tacto, settings, alcanceSesion) }
    val fondo: Fondo by lazy { crearFondo(context.applicationContext, sonido, settings, alcanceSesion) }
    val fuera: FueraDeLaApp by lazy { crearFuera(context.applicationContext, sesionStore, noMolestar, settings, alcanceSesion) }

    // El motor de la sesión, uno por proceso. La restauración de una copia lo usa para cerrar antes la sesión viva.
    private val motorSesionAndroid: MotorSesionAndroid by lazy {
        MotorSesionAndroid(
            store = sesionStore,
            efectos = EfectosSesion(tasks, habits, domainState, sesiones, reconciler, registrarVuelta),
            estado = domainState,
            loGordo = loGordo,
            acciones = registrarVuelta,
            settings = settings,
            aire = aire,
            fondo = fondo,
            fuera = fuera,
            noMolestar = noMolestar,
            sonido = sonido,
            tacto = tacto,
            reloj = { ahoraDelSistema(appContext) },
            alcance = alcanceSesion,
        )
    }
    val motorSesion: MotorSesion get() = motorSesionAndroid

    // Perezosos: alarmas y widgets arrancan el proceso sin tocar el audio (35 OGG) ni el vibrador.
    val sonido: MotorSonido by lazy { crearMotorSonido(context.applicationContext, settings) }
    val tacto: Tacto by lazy { crearTacto(context.applicationContext, settings) }
    val habiSounds by lazy { HabiSounds(sonido) }
    val keyStore = BackupKeyStore(context.filesDir)
    val backup =
        BackupRepository(
            database,
            settings,
            keyStore,
            BuildConfig.VERSION_NAME,
            cancelarAvisos = { avisosHabi.cancelarAceptados(it) },
            cerrarSesionViva = { motorSesionAndroid.cerrarPorRestaurar() },
        )
    val reconciler = PointsReconciler(domainState, rewards, settings = settings, despachador = Dispatchers.Default)

    // Los avisos de Habi. Perezoso: la restauración y la fuente lo piden, el arranque no. Los receptores usan esta
    // misma instancia, con el catálogo real.
    val avisosHabiAndroid: AvisosHabiAndroid by lazy {
        AvisosHabiAndroid(context.applicationContext, contenedor = { this }, catalogo = { catalogoHabla })
    }
    val avisosHabi: AvisosHabi get() = avisosHabiAndroid

    // Las bolsas de Habi del idioma activo. AppStartup lo precarga tras el primer fotograma; si algo lo pide
    // antes, lee esa bolsa en el momento (nunca queda vacío).
    val catalogoHabla: CatalogoRecursos by lazy { CatalogoRecursos { appContext.resources } }

    private val appContext = context.applicationContext

    val propuestas: FuentePropuestas by lazy {
        FuentePropuestasHabi(
            estado = domainState.observe(),
            ajustes = settings.settings,
            recordatorios = habits.observeHabits().map { lista -> lista.mapNotNull { h -> h.reminderMinutes?.let { h.id to it } }.toMap() },
            memoria = memoria,
            vigentes = propuestaVigente,
            gordo = loGordo,
            aceptados = avisosAceptados,
            catalogo = { catalogoHabla },
            avisos = avisosHabi,
            permisos = { permisosAvisos() },
            // «Hecho» de la vuelta: la misma escritura que las acciones rápidas de los recordatorios.
            registrar = { habitId, cantidad -> registrarVuelta.log(habitId, cantidad) },
            // Con la app abierta, la tarjeta cambia sola con la hora (la noche, las caducidades).
            ticks = ticksDeMinuto(System::currentTimeMillis),
            // Las tareas con una sesión hoy, cerrada o viva: para Habi, algo en lo que ya se está no está «sin empezar».
            empezadas =
                tareasEmpezadasHoy(
                    ajustes = settings.settings,
                    ticks = ticksDeMinuto(System::currentTimeMillis),
                    ahora = System::currentTimeMillis,
                    zona = ZoneId::systemDefault,
                    cerradasDe = sesiones::deDia,
                    viva = sesionStore.registro,
                ),
        )
    }

    private val registrarVuelta by lazy { QuickActionUseCase(journal, reconciler, domainState, habits, settings) }

    /**
     * El contexto de ahora para las frases que se redactan en la pantalla (la sesión): el mismo que construye la fuente de
     * Habi con los mismos ajustes y permisos, para que las frases con etiquetas (franja, estación, voz) filtren igual.
     */
    suspend fun contextoDelDia(): Contexto {
        val prefs = settings.settings.first()
        val state = domainState.snapshot()
        val ahora = System.currentTimeMillis()
        val zona = ZoneId.systemDefault()
        val hoy = LogicalDays.logicalDayOf(ahora, prefs.dayCutoffMinutes, zona)
        val permisos = permisosAvisos()
        return Contextos.de(
            ahoraMillis = ahora,
            zona = zona,
            corteDia = prefs.dayCutoffMinutes,
            horaManana = prefs.habiMorningMinutes,
            horaNoche = prefs.habiNightMinutes,
            dndActivo = prefs.dndEnabled,
            dndDesde = prefs.dndFromMinutes,
            dndHasta = prefs.dndToMinutes,
            voz = prefs.personality,
            nombre = prefs.userName,
            permisoAvisos = permisos.notificaciones,
            alarmasExactas = permisos.alarmasExactas,
            diaSellado = Sealing.isSealed(state, hoy),
            quedanExigibles = Hechos.quedanPendientes(state, hoy),
        )
    }

    internal fun permisosAvisos(): PermisosAvisos {
        val notificaciones = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        // Antes de Android 12 las alarmas exactas no piden permiso.
        val alarmas =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                appContext.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        return PermisosAvisos(notificaciones, alarmas)
    }
}
