package com.alvarotc.bito.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
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
}
