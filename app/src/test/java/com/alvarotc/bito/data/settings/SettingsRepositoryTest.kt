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
import org.junit.Assert.assertTrue
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

    @Test
    fun `seeding a store that never had hours plants 12,00 and 19,00 as ordinary hours`() =
        runTest {
            val repository = SettingsRepository(store("seed-fresh"))

            repository.seedDefaultReminders()

            assertEquals(listOf(12 * 60, 19 * 60), repository.settings.first().globalReminderMinutes)
        }

    @Test
    fun `seeding is one-shot — hours the user deleted never resurrect`() =
        runTest {
            val repository = SettingsRepository(store("seed-no-resurrect"))
            repository.seedDefaultReminders()
            repository.update { it.copy(globalReminderMinutes = emptyList()) }

            repository.seedDefaultReminders()

            assertEquals(emptyList<Int>(), repository.settings.first().globalReminderMinutes)
        }

    @Test
    fun `a store with hours already configured only gets the marker, never the seed`() =
        runTest {
            val repository = SettingsRepository(store("seed-configured"))
            repository.update { it.copy(globalReminderMinutes = listOf(480)) }

            repository.seedDefaultReminders()
            assertEquals(listOf(480), repository.settings.first().globalReminderMinutes)

            // The marker landed on that first call: clearing the hours later never re-seeds.
            repository.update { it.copy(globalReminderMinutes = emptyList()) }
            repository.seedDefaultReminders()
            assertEquals(emptyList<Int>(), repository.settings.first().globalReminderMinutes)
        }

    @Test
    fun `a restore that writes a whole Settings is never re-seeded over`() =
        runTest {
            val repository = SettingsRepository(store("seed-restore"))
            repository.seedDefaultReminders()

            // BackupRepository.import applies a restore exactly like this: a full Settings
            // built from the backup file, replacing whatever the store had — including a
            // backup with no reminder hours at all.
            repository.update { Settings(userName = "Álvaro", globalReminderMinutes = emptyList()) }
            repository.seedDefaultReminders()

            val restored = repository.settings.first()
            assertEquals(emptyList<Int>(), restored.globalReminderMinutes)
            assertEquals("Álvaro", restored.userName)
        }

    @Test
    fun `the notification prompt is claimable exactly once per install`() =
        runTest {
            val repository = SettingsRepository(store("notif-claim"))

            // The one unprompted ask this install gets — after it, the notice in Ajustes is the
            // recovery path, not another dialog. This is what keeps a refusal from being nagged at
            // on every route change for the rest of the install's life.
            assertTrue(repository.claimNotificationPrompt())
            assertFalse(repository.claimNotificationPrompt())
            assertFalse(repository.claimNotificationPrompt())
        }

    @Test
    fun `a restore never carries another install's claim onto this device`() =
        runTest {
            val repository = SettingsRepository(store("notif-claim-restore"))

            // BackupRepository.import replaces Settings wholesale — the marker lives outside
            // Settings, so a restore can neither resurrect the claim nor spend it: this device has
            // never shown the dialog, and it still gets its one ask.
            repository.update { Settings(userName = "Álvaro") }
            assertTrue(repository.claimNotificationPrompt())

            repository.update { Settings(userName = "Álvaro") }
            assertFalse(repository.claimNotificationPrompt())
        }
}
