package com.alvarotc.bito.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.backup.Argon2Params
import com.alvarotc.bito.data.backup.BackupCrypto
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.AutoBackupError
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

// Argon2 at production cost (~100-300ms) would dominate this suite's runtime; cheap but still in
// BackupCrypto's validated range (memoryKib >= 8 * parallelism) — same tier BackupViewModelTest uses.
private val TEST_ARGON2_PARAMS = Argon2Params(memoryKib = 64, iterations = 1, parallelism = 1)

/**
 * Drives the real settings screen over isolated dependencies (in-memory Room, tmp DataStore) —
 * deliberately NOT AppContainer, whose ReminderSync would leak background coroutines across
 * Robolectric tests. The 600dp-short config makes the content taller than the viewport.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h600dp")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: BitoDatabase

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { java.io.File(tmp.root, "$name.preferences_pb") }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `the backups card stays reachable on short screens`() {
        val settings = SettingsRepository(settingsStore("settings-screen"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Export backup", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the archived habits row is hidden when nothing is archived`() {
        val settings = SettingsRepository(settingsStore("settings-screen-no-archived"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("General", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `tapping the archived habits row navigates to the archived list`() {
        runBlocking {
            HabitsRepository(db).create(habitEntity(id = "gone", name = "Fumar", status = HabitStatus.ARCHIVED))
        }
        val settings = SettingsRepository(settingsStore("settings-screen-archived"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        var opened = false
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = { opened = true })
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Archived habit (1)", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        assertTrue(opened)
    }

    @Test
    fun `the reminders card hints when no hours are configured`() {
        val settings = SettingsRepository(settingsStore("settings-screen-no-reminders"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("No reminders set", substring = true, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the reminders hint disappears once an hour is configured`() {
        val settings = SettingsRepository(settingsStore("settings-screen-with-reminder"))
        runBlocking { settings.update { it.copy(globalReminderMinutes = listOf(9 * 60)) } }
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("No reminders set", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the Habi section shows the sounds toggle, on by default`() {
        val settings = SettingsRepository(settingsStore("settings-screen-habi-default"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Habi sounds", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("habi-sounds-switch", useUnmergedTree = true).assertIsOn()
    }

    @Test
    fun `tapping the Habi sounds toggle flips and persists the setting`() {
        val settings = SettingsRepository(settingsStore("settings-screen-habi-toggle"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("habi-sounds-switch", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("habi-sounds-switch", useUnmergedTree = true).assertIsOff()
        assertFalse(runBlocking { settings.settings.first() }.habiSoundsEnabled)
    }

    @Test
    fun `the celebration switch writes the setting`() {
        val settings = SettingsRepository(settingsStore("settings-screen-celebration-toggle"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("celebration-switch", useUnmergedTree = true).assertIsOn()
        compose.onNodeWithTag("celebration-switch", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("celebration-switch", useUnmergedTree = true).assertIsOff()
        assertFalse(runBlocking { settings.settings.first() }.perfectDayCelebration)
    }

    /**
     * Covers the import preview's "%1\$s · %2\$s" join of two independent pluralStringResource
     * calls (habits, log entries) — the nit 6 follow-up parameter wiring a format regression could
     * slip through silently, since the string itself no longer carries any `%1$d`.
     */
    @Test
    fun `the import preview pluralizes both the habit and entry counts`() {
        runBlocking {
            val habits = HabitsRepository(db)
            repeat(3) { i -> habits.create(habitEntity(id = "ip-h$i", name = "Habit $i")) }
            repeat(12) { i -> db.entryDao().insert(entryEntity(id = "ip-e$i", habitId = "ip-h0", logicalDay = i)) }
        }
        val settings = SettingsRepository(settingsStore("settings-screen-import-preview-many"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupRepo = BackupRepository(db, settings, keyStore, "test")
        val exported = runBlocking { backupRepo.exportJson(0L) }
        val backupVm =
            BackupViewModel(
                backupRepo,
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val uri = Uri.parse("content://bito/import-preview-many.bito")
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(exported.toByteArray()))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        backupVm.loadImport(resolver, uri)
        compose.waitForIdle()

        // ImportPreviewSheet is a ModalBottomSheet, its own layout root outside the settings
        // screen's scrollable column — nothing to scroll to, it renders fully visible on open.
        compose.onNodeWithText("3 habits · 12 log entries", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Same path as above at the singular edge, where a bare %1\$d format would have read "1 habits · 1 log entries". */
    @Test
    fun `the import preview keeps singular grammar at a count of one`() {
        runBlocking {
            val habits = HabitsRepository(db)
            habits.create(habitEntity(id = "ip-h0", name = "Habit"))
            db.entryDao().insert(entryEntity(id = "ip-e0", habitId = "ip-h0", logicalDay = 0))
        }
        val settings = SettingsRepository(settingsStore("settings-screen-import-preview-one"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupRepo = BackupRepository(db, settings, keyStore, "test")
        val exported = runBlocking { backupRepo.exportJson(0L) }
        val backupVm =
            BackupViewModel(
                backupRepo,
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val uri = Uri.parse("content://bito/import-preview-one.bito")
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(exported.toByteArray()))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        backupVm.loadImport(resolver, uri)
        compose.waitForIdle()

        compose.onNodeWithText("1 habit · 1 log entry", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the backup card shows the folder name when set`() {
        val settings = SettingsRepository(settingsStore("settings-screen-backup-folder-name"))
        runBlocking {
            settings.update {
                it.copy(backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FBito")
            }
        }
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Documents/Bito", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `frequency pills and copies stepper appear only with a folder`() {
        val settings = SettingsRepository(settingsStore("settings-screen-backup-frequency-stepper"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Daily", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Number of copies", useUnmergedTree = true).assertDoesNotExist()

        runBlocking {
            settings.update {
                it.copy(backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ABito")
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Daily", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Number of copies", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `backup now row appears only with a folder`() {
        val settings = SettingsRepository(settingsStore("settings-screen-backup-now-row"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Back up now", useUnmergedTree = true).assertDoesNotExist()

        runBlocking {
            settings.update {
                it.copy(backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ABito")
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Back up now", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a folder error renders its explanation`() {
        val settings = SettingsRepository(settingsStore("settings-screen-backup-folder-error"))
        runBlocking {
            settings.update {
                it.copy(
                    backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ABito",
                    lastAutoBackupError = AutoBackupError.FOLDER,
                )
            }
        }
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("The folder is no longer available — choose it again", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the create sheet blocks until both passphrases match`() {
        val settings = SettingsRepository(settingsStore("settings-screen-passphrase-match"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Encryption", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("passphrase-field", useUnmergedTree = true).performTextInput("longpass1")
        compose.onNodeWithTag("passphrase-repeat-field", useUnmergedTree = true).performTextInput("longpass2")
        compose.waitForIdle()

        compose.onNodeWithTag("passphrase-confirm", useUnmergedTree = true).assertIsNotEnabled()

        compose.onNodeWithTag("passphrase-repeat-field", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("passphrase-repeat-field", useUnmergedTree = true).performTextInput("longpass1")
        compose.waitForIdle()

        compose.onNodeWithTag("passphrase-confirm", useUnmergedTree = true).assertIsEnabled()
    }

    @Test
    fun `short passphrases keep confirm disabled`() {
        val settings = SettingsRepository(settingsStore("settings-screen-passphrase-short"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Encryption", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("passphrase-field", useUnmergedTree = true).performTextInput("short1")
        compose.onNodeWithTag("passphrase-repeat-field", useUnmergedTree = true).performTextInput("short1")
        compose.waitForIdle()

        compose.onNodeWithTag("passphrase-confirm", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `an encrypted import opens the passphrase sheet`() {
        val settings = SettingsRepository(settingsStore("settings-screen-encrypted-import"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupRepo = BackupRepository(db, settings, keyStore, "test")
        val json = runBlocking { backupRepo.exportJson(0L) }
        val encrypted =
            BackupCrypto.encrypt(
                json,
                BackupCrypto.deriveKey("right-pass".toCharArray(), BackupCrypto.newSalt(), TEST_ARGON2_PARAMS),
            )
        val backupVm =
            BackupViewModel(
                backupRepo,
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val uri = Uri.parse("content://bito/encrypted-import.bito")
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(encrypted))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        backupVm.loadImport(resolver, uri)
        compose.waitForIdle()

        compose.onNodeWithTag("import-passphrase-field", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a wrong passphrase shows the inline error and keeps the sheet`() {
        val settings = SettingsRepository(settingsStore("settings-screen-wrong-passphrase"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupRepo = BackupRepository(db, settings, keyStore, "test")
        val json = runBlocking { backupRepo.exportJson(0L) }
        val encrypted =
            BackupCrypto.encrypt(
                json,
                BackupCrypto.deriveKey("right-pass".toCharArray(), BackupCrypto.newSalt(), TEST_ARGON2_PARAMS),
            )
        val backupVm =
            BackupViewModel(
                backupRepo,
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val uri = Uri.parse("content://bito/wrong-passphrase.bito")
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(encrypted))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        backupVm.loadImport(resolver, uri)
        compose.waitForIdle()

        compose.onNodeWithTag("import-passphrase-field", useUnmergedTree = true).performTextInput("nope-pass")
        // Not performClick(): synthesized touch gestures don't reach a button inside a
        // ModalBottomSheet under this Robolectric harness (documented in DetailScreenTest,
        // ReviewScreenTest, CelebrationSheetsTest) — invoking the node's own OnClick action
        // directly is what actually proves tapping "Save" calls submitImportPassphrase.
        compose.onNodeWithTag("import-passphrase-confirm", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        compose.waitForIdle()

        compose.onNodeWithText("Wrong passphrase", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("import-passphrase-field", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `needs-key state relabels the encryption row`() {
        val settings = SettingsRepository(settingsStore("settings-screen-needs-key"))
        runBlocking { settings.update { it.copy(backupEncryption = true) } }
        val keyStore = BackupKeyStore(tmp.root)
        val backupVm =
            BackupViewModel(
                BackupRepository(db, settings, keyStore, "test"),
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Needs a passphrase", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
}
