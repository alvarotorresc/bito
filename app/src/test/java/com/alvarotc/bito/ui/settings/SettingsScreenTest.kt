package com.alvarotc.bito.ui.settings

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        val backupVm = BackupViewModel(BackupRepository(db, settings, "test"), ioDispatcher = dispatcher)
        val settingsVm = SettingsViewModel(settings)
        compose.setContent {
            BitoTheme {
                SettingsScreen(backupViewModel = backupVm, settingsViewModel = settingsVm, onBack = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Export backup", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
