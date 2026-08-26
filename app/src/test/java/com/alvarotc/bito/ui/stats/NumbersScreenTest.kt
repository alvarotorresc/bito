package com.alvarotc.bito.ui.stats

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.DomainStateRepository
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
import java.io.File
import java.time.ZoneId

/**
 * Drives the real [NumbersScreen] over an in-memory Room database, following
 * [StatsScreenTest]'s harness. New (T7b): NumbersScreen had zero test coverage before this a11y
 * pass touched [NumberTileCard]'s merge semantics.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class NumbersScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")

    private lateinit var db: BitoDatabase

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "numbers-screen.preferences_pb") }

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
    fun `a tile reads its value and label as one unit`() {
        // [E]: Icon(null) + Text(value) + Text(label) had no merging ancestor before the fix — a
        // single MERGED-tree node carrying both texts only exists once NumberTileCard's BitoCard
        // gets `mergeDescendants = true`. Zero setup needed: even the empty-DB totals still pair
        // a real value ("0") with its label.
        val domainState = DomainStateRepository(db)
        val settings = SettingsRepository(settingsStore())
        val vm = NumbersViewModel(domainState, settings, now = { fixedNow }, zone = { utc }, defaultDispatcher = dispatcher)
        compose.setContent {
            BitoTheme {
                NumbersScreen(viewModel = vm, onBack = {})
            }
        }
        compose.waitForIdle()

        compose.onNode(hasText("0") and hasText("Entries logged")).assertExists()
    }
}
