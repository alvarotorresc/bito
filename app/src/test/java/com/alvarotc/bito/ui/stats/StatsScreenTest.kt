package com.alvarotc.bito.ui.stats

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import kotlin.test.assertTrue

/**
 * Drives the real [StatsScreen] over an in-memory Room database, following
 * [com.alvarotc.bito.ui.detail.DetailScreenTest]'s harness.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class StatsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)
    private var storeIndex = 0

    private lateinit var db: BitoDatabase

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "stats-screen-${storeIndex++}.preferences_pb") }

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

    private fun setContent(
        onOpenRecords: () -> Unit = {},
        onOpenNumbers: () -> Unit = {},
    ) {
        val domainState = DomainStateRepository(db)
        val settings = SettingsRepository(settingsStore())
        val vm = StatsViewModel(domainState, settings, now = { fixedNow }, zone = { utc })
        compose.setContent {
            BitoTheme {
                StatsScreen(viewModel = vm, onOpenRecords = onOpenRecords, onOpenNumbers = onOpenNumbers)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the perfect days card is the only solid accent`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 5),
            )
        }
        setContent()

        compose.onAllNodesWithTag("accent", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `teasers navigate to records and numbers`() {
        var openedRecords = false
        var openedNumbers = false
        setContent(onOpenRecords = { openedRecords = true }, onOpenNumbers = { openedNumbers = true })

        compose.onNodeWithTag("teaser-records", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("teaser-numbers", useUnmergedTree = true).performClick()

        assertTrue(openedRecords)
        assertTrue(openedNumbers)
    }

    @Test
    fun `the streak wall lists active streaks longest first`() {
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "meditate", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 10),
            )
            HabitsRepository(db).create(
                habitEntity(id = "water", name = "Agua", metric = Metric.CHECK, target = 1, createdOnDay = today - 10),
            )
            // Meditar: 3-day streak ending today. Agua: 1-day streak ending today. Both ACTIVE, so
            // the screen must render Meditar (longer) to the left of Agua (shorter).
            for (day in (today - 2)..today) {
                db.entryDao().insert(entryEntity(id = "e-med-$day", habitId = "meditate", logicalDay = day, value = 1))
            }
            db.entryDao().insert(entryEntity(id = "e-water", habitId = "water", logicalDay = today, value = 1))
        }
        setContent()

        val meditateLeft =
            compose.onNodeWithTag("streak-meditate", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        val waterLeft =
            compose.onNodeWithTag("streak-water", useUnmergedTree = true).getUnclippedBoundsInRoot().left

        assertTrue(meditateLeft < waterLeft)
    }
}
