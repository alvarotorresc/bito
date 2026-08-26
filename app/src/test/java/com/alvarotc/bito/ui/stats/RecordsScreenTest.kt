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
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
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
 * Drives the real [RecordsScreen] over an in-memory Room database, following
 * [StatsScreenTest]'s harness. New (T7b): RecordsScreen had zero test coverage before this a11y
 * pass touched [BestRecordHero]/[RecordLine]'s merge semantics.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class RecordsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "records-screen.preferences_pb") }

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

    private fun setContent() {
        val domainState = DomainStateRepository(db)
        val settings = SettingsRepository(settingsStore())
        val vm = RecordsViewModel(domainState, settings, now = { fixedNow }, zone = { utc }, defaultDispatcher = dispatcher)
        compose.setContent {
            BitoTheme {
                RecordsScreen(viewModel = vm, onBack = {})
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the hero card reads its best streak and habit name as one unit`() {
        // [E]: trophy Icon(null) + up to 4 plain Text stops had no merging ancestor before the
        // fix — a single MERGED-tree node carrying both the best count and the habit name only
        // exists once BestRecordHero's inner Column gets `mergeDescendants = true`.
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "meditate", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 10),
            )
            for (day in (today - 2)..today) {
                db.entryDao().insert(entryEntity(id = "e-med-$day", habitId = "meditate", logicalDay = day, value = 1))
            }
        }
        setContent()

        compose.onNode(hasText("3") and hasText("Meditar")).assertExists()
    }

    @Test
    fun `a record line reads its name and current streak as one unit`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "meditate", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 10),
            )
            db.entryDao().insert(entryEntity(id = "e-med-1", habitId = "meditate", logicalDay = today, value = 1))
        }
        setContent()

        compose.onNode(hasText("Meditar") and hasText("now:", substring = true)).assertExists()
    }
}
