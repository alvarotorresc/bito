package com.alvarotc.bito.ui.tasks

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import com.alvarotc.bito.domain.model.DueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T13 only ever exercised [taskDueLabel]'s NONE branch (a loose task, via TodayScreenTest) — this
 * fills in WEEK and DATE, plus [taskDueOverdue]'s own boolean, the one every row tints Peligro on.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TaskDueLabelTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = 20_000

    private fun label(
        dueKind: DueKind,
        dueDay: Int?,
    ): String? {
        var result: String? = null
        compose.setContent { result = taskDueLabel(dueKind, dueDay, today) }
        compose.waitForIdle()
        return result
    }

    @Test
    fun `a WEEK task not yet past its Sunday says this week`() {
        assertEquals("this week", label(DueKind.WEEK, today + 2))
    }

    @Test
    fun `a DATE task a few days out counts them down`() {
        assertEquals("in 3 days", label(DueKind.DATE, today + 3))
    }

    @Test
    fun `an overdue DATE task says how many days late, and taskDueOverdue agrees`() {
        assertEquals("3 days overdue", label(DueKind.DATE, today - 3))
        assertTrue(taskDueOverdue(DueKind.DATE, today - 3, today))
    }

    @Test
    fun `a WEEK task past its Sunday is not the Peligro branch`() {
        assertFalse(taskDueOverdue(DueKind.WEEK, today - 3, today))
    }
}
