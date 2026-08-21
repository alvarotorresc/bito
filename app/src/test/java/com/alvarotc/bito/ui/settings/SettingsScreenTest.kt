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
import androidx.compose.ui.test.assertTextContains
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

        compose.onNodeWithText("Export a copy…", useUnmergedTree = true)
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

        // The General card itself now always shows (it hosts the language row below), so the
        // absence check moves to the archived row specifically — "Archived habit"/"Archived
        // habits" both start with this substring.
        compose.onNodeWithText("Archived habit", substring = true, useUnmergedTree = true).assertDoesNotExist()
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
    fun `the language row shows the current choice`() {
        val settings = SettingsRepository(settingsStore("settings-screen-language"))
        runBlocking { settings.update { it.copy(languageTag = "en") } }
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

        compose.onNodeWithText("English", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    /**
     * "English" alone doesn't discriminate the es-branch of the row's `when` — es and en share no
     * string with system's default, but [language_english] happens to read "English" in both
     * locales this suite's default (EN) qualifiers ever render, so this pins the es-branch on its
     * own string instead. [language_spanish] is the endonym "Español" in every locale (platform
     * convention for language pickers: each language names itself), not a per-locale translation.
     */
    @Test
    fun `the language row shows Español when the tag is es`() {
        val settings = SettingsRepository(settingsStore("settings-screen-language-es"))
        runBlocking { settings.update { it.copy(languageTag = "es") } }
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

        compose.onNodeWithText("Español", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tapping the language row cycles system to es to en to system, persisting each step`() {
        val settings = SettingsRepository(settingsStore("settings-screen-language-cycle"))
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

        compose.onNodeWithText("System", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("System", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Español", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals("es", runBlocking { settings.settings.first() }.languageTag)

        compose.onNodeWithText("Español", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("English", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals("en", runBlocking { settings.settings.first() }.languageTag)

        compose.onNodeWithText("English", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("System", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertNull(runBlocking { settings.settings.first() }.languageTag)
    }

    @Test
    fun `settings lets the user rename themselves`() {
        val settings = SettingsRepository(settingsStore("settings-screen-name"))
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

        compose.onNodeWithText("Your name", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("settings-name-field", useUnmergedTree = true).performTextInput("Alvaro")
        compose.waitForIdle()
        compose.onNodeWithTag("settings-name-field", useUnmergedTree = true).assertTextContains("Alvaro")
        compose.onNodeWithTag("settings-name-confirm", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals("Alvaro", runBlocking { settings.settings.first() }.userName)
        compose.onNodeWithText("Alvaro", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
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

        // The folder name now lives inside the status chip's compound second line ("in
        // Documents/Bito", no count known here since this VM's default countBackups is a no-op),
        // not as a Text node of its own — substring, not exact, match.
        compose.onNodeWithText("Documents/Bito", substring = true, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `automatic backup and keep rows appear only with a folder`() {
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

        compose.onNodeWithText("Automatic backup", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Keep", useUnmergedTree = true).assertDoesNotExist()

        runBlocking {
            settings.update {
                it.copy(backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ABito")
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Automatic backup", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Daily", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keep", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        // Default copies = 5, plural "other" form.
        compose.onNodeWithText("last 5 copies", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the back up now button appears only with a folder, restore always does`() {
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
        compose.onNodeWithText("Restore", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        runBlocking {
            settings.update {
                it.copy(backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ABito")
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Back up now", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Restore", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
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

        compose.onNodeWithTag("backup-encryption-switch", useUnmergedTree = true).performScrollTo().performClick()
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

        compose.onNodeWithTag("backup-encryption-switch", useUnmergedTree = true).performScrollTo().performClick()
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

    /**
     * Regression for the reviewer-found race: Cancel has no busy gate, so a decrypt attempt can
     * still be in flight when the sheet is dismissed. If that attempt later resolves
     * WRONG_PASSPHRASE after askImportPassphrase is already false, the message effect must not
     * latch the inline-error flag — otherwise the NEXT encrypted import's sheet would mount
     * already showing a stale "Wrong passphrase". Simulated honestly (not just asserted as a
     * contract): cryptoDispatcher is a separate, manually-advanced StandardTestDispatcher so the
     * test can pause submitImportPassphrase() right after busy=true — mid-Argon2 — dismiss the
     * sheet there, and only then let the decrypt resolve.
     */
    @Test
    fun `dismissing during a wrong-passphrase check does not poison the next sheet`() {
        val settings = SettingsRepository(settingsStore("settings-screen-stale-wrong-passphrase"))
        val keyStore = BackupKeyStore(tmp.root)
        val backupRepo = BackupRepository(db, settings, keyStore, "test")
        val json = runBlocking { backupRepo.exportJson(0L) }
        val encrypted =
            BackupCrypto.encrypt(
                json,
                BackupCrypto.deriveKey("right-pass".toCharArray(), BackupCrypto.newSalt(), TEST_ARGON2_PARAMS),
            )
        val cryptoDispatcher = StandardTestDispatcher()
        val backupVm =
            BackupViewModel(
                backupRepo,
                settings,
                keyStore,
                backupNow = {},
                ioDispatcher = dispatcher,
                cryptoDispatcher = cryptoDispatcher,
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val firstUri = Uri.parse("content://bito/stale-wrong-passphrase-1.bito")
        shadowOf(resolver).registerInputStream(firstUri, ByteArrayInputStream(encrypted))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        backupVm.loadImport(resolver, firstUri)
        compose.waitForIdle()

        compose.onNodeWithTag("import-passphrase-field", useUnmergedTree = true).performTextInput("nope-pass")
        // Fires submitImportPassphrase: busy=true is set synchronously, then the coroutine
        // suspends at withContext(cryptoDispatcher) because that scheduler hasn't been advanced.
        compose.onNodeWithTag("import-passphrase-confirm", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action?.invoke()
        assertTrue("the decrypt attempt must still be in flight", backupVm.state.value.busy)

        // Tap Cancel while that decrypt is still in flight.
        backupVm.dismissImportPassphrase()
        compose.waitForIdle()
        assertFalse(backupVm.state.value.askImportPassphrase)

        // Only now let the in-flight decrypt resolve WRONG_PASSPHRASE — after the sheet is gone.
        cryptoDispatcher.scheduler.advanceUntilIdle()
        compose.waitForIdle()

        // A fresh encrypted import must not have its sheet mount with a stale inline error.
        val secondUri = Uri.parse("content://bito/stale-wrong-passphrase-2.bito")
        shadowOf(resolver).registerInputStream(secondUri, ByteArrayInputStream(encrypted))
        backupVm.loadImport(resolver, secondUri)
        compose.waitForIdle()

        compose.onNodeWithTag("import-passphrase-field", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Wrong passphrase", useUnmergedTree = true).assertDoesNotExist()
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

    @Test
    fun `the status line shows the folder and count when known`() {
        val settings = SettingsRepository(settingsStore("settings-screen-backup-count-known"))
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
                countBackups = { 4 },
            )
        val settingsVm = SettingsViewModel(settings, HabitsRepository(db))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("4 copies in Documents/Bito", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * "Restore" replaces the removed "Import backup" row; the system document picker itself can't
     * be driven under Robolectric (no test in this file ever has), so this drives the same
     * fake-file pattern every other import test uses — the picker's callback is exactly what calls
     * [BackupViewModel.loadImport] — and checks the button that now starts that flow is present.
     */
    @Test
    fun `restore button opens the import flow`() {
        val settings = SettingsRepository(settingsStore("settings-screen-restore-button"))
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
        val uri = Uri.parse("content://bito/restore-flow.bito")
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(exported.toByteArray()))
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {}, onOpenArchived = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Restore", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        backupVm.loadImport(resolver, uri)
        compose.waitForIdle()

        compose.onNodeWithText("Restore this backup?", useUnmergedTree = true).assertIsDisplayed()
    }
}
