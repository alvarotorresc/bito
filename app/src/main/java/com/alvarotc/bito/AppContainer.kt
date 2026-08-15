package com.alvarotc.bito

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository

/** Hand-built DI (tech doc §1.4: no framework — constructors). */
class AppContainer(context: Context) {
    val database: BitoDatabase = BitoDatabase.build(context)
    val habits = HabitsRepository(database)
    val journal = JournalRepository(database)
    val rewards = RewardsRepository(database)
    val domainState = DomainStateRepository(database)
    val settings =
        SettingsRepository(
            PreferenceDataStoreFactory.create {
                context.filesDir.resolve("settings.preferences_pb")
            },
        )
}
