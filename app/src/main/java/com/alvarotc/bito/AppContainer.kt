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
import com.alvarotc.bito.data.habla.ticksDeMinuto
import com.alvarotc.bito.data.repo.BreathingRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.FocusStore
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.notifications.AvisosHabi
import com.alvarotc.bito.ui.notifications.AvisosHabiAndroid
import com.alvarotc.bito.ui.notifications.QuickActionUseCase
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.crearTacto
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.sistema.sonido.crearMotorSonido
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

/** Hand-built DI (tech doc §1.4: no framework — constructors). */
class AppContainer(context: Context) {
    val database: BitoDatabase = BitoDatabase.build(context)
    val habits = HabitsRepository(database)
    val journal = JournalRepository(database)
    val rewards = RewardsRepository(database)
    val domainState = DomainStateRepository(database)
    val tasks = TasksRepository(database)
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
    val focus =
        FocusStore(
            PreferenceDataStoreFactory.create {
                context.filesDir.resolve("focus.preferences_pb")
            },
        )

    // Perezosos: alarmas y widgets arrancan el proceso sin tocar el audio (35 OGG) ni el vibrador.
    val sonido: MotorSonido by lazy { crearMotorSonido(context.applicationContext, settings) }
    val tacto: Tacto by lazy { crearTacto(context.applicationContext, settings) }
    val habiSounds by lazy { HabiSounds(sonido) }
    val keyStore = BackupKeyStore(context.filesDir)
    val backup =
        BackupRepository(database, settings, keyStore, BuildConfig.VERSION_NAME, cancelarAvisos = { avisosHabi.cancelarAceptados(it) })
    val reconciler = PointsReconciler(domainState, rewards, settings = settings, despachador = Dispatchers.Default)

    // Los avisos de Habi. Perezoso: la restauración y la fuente lo piden, el arranque no.
    val avisosHabi: AvisosHabi by lazy {
        AvisosHabiAndroid(context.applicationContext, contenedor = { this }, catalogo = { catalogoHabla })
    }

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
        )
    }

    private val registrarVuelta by lazy { QuickActionUseCase(journal, reconciler, domainState, habits, settings) }

    internal fun permisosAvisos(): PermisosAvisos {
        val notificaciones = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        // Antes de Android 12 las alarmas exactas no piden permiso.
        val alarmas =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                appContext.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        return PermisosAvisos(notificaciones, alarmas)
    }
}
