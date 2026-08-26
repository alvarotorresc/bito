package com.alvarotc.bito.ui.habitform

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.HabitsRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The preset-pill grid's one real layout risk is text: five labels across two fixed-weight rows
 * must each hold a single uncut line, in both languages, at a raised font scale, on a narrow
 * screen. Split from [HabitFormScreenTest] because honest answers need
 * [GraphicsMode.Mode.NATIVE]: the suite's default LEGACY mode stubs glyph advances to ~1px
 * (verified empirically — "Daily" measured 5px wide), which would green-light any overflow.
 * Same opt-in, same reason, as HabiDrawingTest.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class HabitFormPresetPillLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var db: BitoDatabase

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

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "pill-layout.preferences_pb") }

    /** [HabitFormScreenTest]'s harness with only the font scaled up — the pixel density stays on
     * the qualifiers, mirroring a user raising the system font size, not display zoom. */
    private fun launchScreenAtFontScale(fontScale: Float) {
        val vm = HabitFormViewModel(HabitsRepository(db), SettingsRepository(settingsStore()), null)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                BitoTheme {
                    HabitFormScreen(vm, onBack = {})
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * `lineCount == 1` alone is a mirage: with `maxLines = 1` a too-long label still reports one
     * line while wrapping into a clipped phantom second one (`didExceedMaxLines`) — exactly how
     * "Cantidad" failed at 63px of room before the pill's inset was retuned — or, as a single
     * unbreakable word, ellipsizing within line one. So the guards are the paragraph's own
     * exceeded flag plus the measured line width against the width the pill actually granted.
     */
    private fun assertLabelHoldsOneUncutLine(label: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals("\"$label\" wrapped past one line", 1, layout.lineCount)
        assertFalse("\"$label\" needed a second line", layout.multiParagraph.didExceedMaxLines)
        val lineWidth = layout.getLineRight(0) - layout.getLineLeft(0)
        val maxWidth = layout.layoutInput.constraints.maxWidth
        assertTrue(
            "\"$label\" is wider ($lineWidth px) than its pill grants ($maxWidth px)",
            lineWidth <= maxWidth.toFloat(),
        )
    }

    @Config(qualifiers = "es-w360dp-h800dp")
    @Test
    fun `spanish preset labels hold one uncut line at large font scale on a narrow screen`() {
        launchScreenAtFontScale(1.15f)

        listOf("Diario", "Cantidad", "Duración", "X por semana", "Dejar de hacer")
            .forEach(::assertLabelHoldsOneUncutLine)
    }

    @Config(qualifiers = "w360dp-h800dp")
    @Test
    fun `english preset labels hold one uncut line at large font scale on a narrow screen`() {
        launchScreenAtFontScale(1.15f)

        listOf("Daily", "Amount", "Duration", "X per week", "Quit")
            .forEach(::assertLabelHoldsOneUncutLine)
    }
}
