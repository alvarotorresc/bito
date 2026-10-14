package com.alvarotc.bito

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.BreathingRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.FocusStore
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.crearTacto
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.sistema.sonido.crearMotorSonido
import kotlinx.coroutines.Dispatchers

/** Hand-built DI (tech doc §1.4: no framework — constructors). */
class AppContainer(context: Context) {
    val database: BitoDatabase = BitoDatabase.build(context)
    val habits = HabitsRepository(database)
    val journal = JournalRepository(database)
    val rewards = RewardsRepository(database)
    val domainState = DomainStateRepository(database)
    val tasks = TasksRepository(database)
    val breathing = BreathingRepository(database)
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
    val backup = BackupRepository(database, settings, keyStore, BuildConfig.VERSION_NAME)
    val reconciler = PointsReconciler(domainState, rewards, settings = settings, despachador = Dispatchers.Default)
}
