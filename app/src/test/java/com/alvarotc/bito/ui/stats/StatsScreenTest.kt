package com.alvarotc.bito.ui.stats

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.BadgeCatalog
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
        onOpenBadges: () -> Unit = {},
    ) {
        val domainState = DomainStateRepository(db)
        val settings = SettingsRepository(settingsStore())
        val rewards = RewardsRepository(db)
        val vm =
            StatsViewModel(
                domainState,
                settings,
                rewards,
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcher,
            )
        compose.setContent {
            BitoTheme {
                StatsScreen(
                    viewModel = vm,
                    onOpenRecords = onOpenRecords,
                    onOpenNumbers = onOpenNumbers,
                    onOpenBadges = onOpenBadges,
                )
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
    fun `teaser cards get dato-grande presence regardless of label length`() {
        // "Tus números" wraps to two lines at half-card width where "Récords" doesn't — both must
        // still land at the same real height (heightIn(min = 72.dp) on the teaser row) rather than
        // one card being visibly thinner than the other.
        setContent()

        compose.onNodeWithTag("teaser-records", useUnmergedTree = true).assertHeightIsAtLeast(72.dp)
        compose.onNodeWithTag("teaser-numbers", useUnmergedTree = true).assertHeightIsAtLeast(72.dp)
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

    @Test
    fun `the achievements card opens the badges list`() {
        var opened = false
        setContent(onOpenBadges = { opened = true })

        compose.onNodeWithTag("achievements", useUnmergedTree = true).performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun `unlocked badges render before locked ones`() {
        // Seeded late in catalog order (positions 13-14 of 14): only a real unlocked-first
        // reorder puts them ahead of "streak-7" (position 1) — a naive catalog-order render
        // would leave them several rows below it, which this comparison must catch.
        runBlocking {
            db.badgeDao().insert(BadgeEntity("resurrection", unlockedAtMillis = 1_000L))
            db.badgeDao().insert(BadgeEntity("first-freezer", unlockedAtMillis = 2_000L))
        }
        setContent()

        // A single scroll (to the later node) so both bounds below are read from the same
        // scroll offset — two separate performScrollTo() calls would leave each top relative
        // to a different frame, making the comparison meaningless.
        compose.onNodeWithTag("badge-streak-7", useUnmergedTree = true).performScrollTo()
        val unlockedTop = compose.onNodeWithTag("badge-resurrection", useUnmergedTree = true).getUnclippedBoundsInRoot().top
        val lockedTop = compose.onNodeWithTag("badge-streak-7", useUnmergedTree = true).getUnclippedBoundsInRoot().top

        assertTrue(unlockedTop <= lockedTop)
    }

    @Test
    fun `the streak wall card announces the streak, not just the bare number and name`() {
        // [E]: flame Icon(null) + length Text + name Text — the word "streak" lives ONLY in the
        // icon (contentDescription = null), so a plain mergeDescendants would silently drop it,
        // announcing e.g. "3 Meditar" instead of a real streak description. An explicit override
        // on the card names the unit properly, same mechanism as AchievementsSection's [F] fix.
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "meditate", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 10),
            )
            for (day in (today - 2)..today) {
                db.entryDao().insert(entryEntity(id = "e-med-$day", habitId = "meditate", logicalDay = day, value = 1))
            }
        }
        setContent()

        compose.onNodeWithContentDescription("Meditar, streak of 3 days", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the week strip announces which days were done, not just the tally`() {
        // [D]: exercises the real weekRowDaysDescription() production helper directly against a
        // hand-picked DayDot list (Monday-first) — cheaper and more precise than reconstructing
        // this exact 7-state pattern through the full StatsViewModel/Room pipeline. OFF (index 5,
        // Saturday) is skipped, matching DotHeatmap's own per-cell convention.
        val dots =
            listOf(DayDot.FULFILLED, DayDot.FAILED, DayDot.FROZEN, DayDot.PAUSED, DayDot.PENDING, DayDot.OFF, DayDot.ACTIVITY)
        compose.setContent {
            BitoTheme {
                Text(weekRowDaysDescription(dots))
            }
        }

        compose.onNodeWithText("Mon Done, Tue Not done, Wed Frozen, Thu Paused, Fri Pending, Sun Done").assertExists()
    }

    @Test
    fun `the week strip's dot cluster carries the aggregated description in the real screen`() {
        // weekRowDaysDescription() is proven as a string builder in isolation above; this proves
        // the WIRING — that StatsScreen actually attaches its result to the real dot-cluster Row,
        // not just that the helper function itself is correct.
        runBlocking {
            HabitsRepository(db).create(
                habitEntity(id = "meditate", name = "Meditar", metric = Metric.CHECK, target = 1, createdOnDay = today - 10),
            )
            db.entryDao().insert(entryEntity(id = "e-med-1", habitId = "meditate", logicalDay = today, value = 1))
        }
        setContent()

        val description =
            compose
                .onNodeWithTag("week-dots-meditate", useUnmergedTree = true)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.ContentDescription)
        assertTrue(!description.isNullOrEmpty() && description.first().isNotBlank())
    }

    @Test
    fun `the achievements card announces the count, not every badge name`() {
        // [F]: without the override, tapping into this auto-merging clickable card would
        // concatenate the header text with every BadgeChip's name in the grid.
        runBlocking {
            db.badgeDao().insert(BadgeEntity("streak-7", unlockedAtMillis = 1_000L))
        }
        setContent()

        compose.onNodeWithTag("achievements", useUnmergedTree = true).performScrollTo()
        compose.onNodeWithContentDescription("Achievements, 1 of ${BadgeCatalog.all.size}, open").assertExists()
    }
}
