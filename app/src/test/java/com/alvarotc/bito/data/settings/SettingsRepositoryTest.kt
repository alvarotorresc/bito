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
            assertEquals(true, settings.habiSoundsEnabled)
            assertEquals(-1, settings.perfectDayCelebratedDay)
            assertEquals(0L, settings.badgesSeenUntilMillis)
            assertNull(settings.lastAutoBackupAtMillis)
            assertNull(settings.lastAutoBackupError)
        }

    @Test
    fun `celebration markers round-trip through the store`() =
        runTest {
            val repo = SettingsRepository(store("markers"))
            repo.update { it.copy(perfectDayCelebratedDay = 20679, badgesSeenUntilMillis = 1234L) }
            val read = repo.settings.first()
            assertEquals(20679, read.perfectDayCelebratedDay)
            assertEquals(1234L, read.badgesSeenUntilMillis)
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
                    habiSoundsEnabled = false,
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

    @Test
    fun `last auto backup fields default to null`() =
        runTest {
            val settings = SettingsRepository(store("auto-backup-defaults")).settings.first()
            assertNull(settings.lastAutoBackupAtMillis)
            assertNull(settings.lastAutoBackupError)
        }

    @Test
    fun `last auto backup fields survive a write and read`() =
        runTest {
            val repository = SettingsRepository(store("auto-backup-roundtrip"))
            repository.update { it.copy(lastAutoBackupAtMillis = 1234567L, lastAutoBackupError = AutoBackupError.WRITE) }
            val settings = repository.settings.first()
            assertEquals(1234567L, settings.lastAutoBackupAtMillis)
            assertEquals(AutoBackupError.WRITE, settings.lastAutoBackupError)
        }

    @Test
    fun `error clears when set back to null`() =
        runTest {
            val repository = SettingsRepository(store("auto-backup-null-clear"))
            repository.update { it.copy(lastAutoBackupAtMillis = 1234567L, lastAutoBackupError = AutoBackupError.FOLDER) }
            repository.update { it.copy(lastAutoBackupError = null) }
            val settings = repository.settings.first()
            assertEquals(1234567L, settings.lastAutoBackupAtMillis)
            assertNull(settings.lastAutoBackupError)
        }
}
