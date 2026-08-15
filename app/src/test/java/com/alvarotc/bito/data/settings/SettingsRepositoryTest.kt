package com.alvarotc.bito.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.alvarotc.bito.domain.model.Personality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun kotlinx.coroutines.test.TestScope.store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    @Test
    fun `fresh store yields the documented defaults`() =
        runTest {
            val settings = SettingsRepository(store("defaults")).settings.first()
            assertEquals("", settings.userName)
            assertEquals(0, settings.dayCutoffMinutes)
            assertNull(settings.languageTag)
            assertEquals(emptyList<Int>(), settings.globalReminderMinutes)
            assertEquals(21 * 60 + 30, settings.reviewTimeMinutes)
            assertEquals(true, settings.perfectDayCelebration)
            assertEquals(Personality.NEUTRA, settings.personality)
            assertNull(settings.backupFolderUri)
            assertEquals(BackupFrequency.DAILY, settings.backupFrequency)
            assertEquals(5, settings.backupCopies)
            assertFalse(settings.backupEncryption)
            assertFalse(settings.onboardingDone)
        }

    @Test
    fun `update persists every field round-trip`() =
        runTest {
            val repository = SettingsRepository(store("roundtrip"))
            val written =
                Settings(
                    userName = "Álvaro",
                    dayCutoffMinutes = 180,
                    languageTag = "es",
                    globalReminderMinutes = listOf(8 * 60, 22 * 60),
                    reviewTimeMinutes = 22 * 60,
                    perfectDayCelebration = false,
                    personality = Personality.SARGENTO,
                    backupFolderUri = "content://tree/backups",
                    backupFrequency = BackupFrequency.WEEKLY,
                    backupCopies = 3,
                    backupEncryption = true,
                    onboardingDone = true,
                )
            repository.update { written }
            assertEquals(written, repository.settings.first())
        }

    @Test
    fun `update transforms the current value, not the defaults`() =
        runTest {
            val repository = SettingsRepository(store("transform"))
            repository.update { it.copy(userName = "Álvaro") }
            repository.update { it.copy(dayCutoffMinutes = 180) }
            val settings = repository.settings.first()
            assertEquals("Álvaro", settings.userName)
            assertEquals(180, settings.dayCutoffMinutes)
        }

    @Test
    fun `setting a nullable field back to null removes its stored value`() =
        runTest {
            val repository = SettingsRepository(store("null-removal"))
            repository.update { it.copy(languageTag = "es", backupFolderUri = "content://tree/backups") }
            repository.update { it.copy(languageTag = null, backupFolderUri = null) }
            val settings = repository.settings.first()
            assertNull(settings.languageTag)
            assertNull(settings.backupFolderUri)
        }
}
