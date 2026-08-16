package com.alvarotc.bito.ui.notifications

import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.model.HabitStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSchedulerTest {
    @Test
    fun `slots cover global hours, active habit reminders and the review`() {
        val settings = Settings(globalReminderMinutes = listOf(480, 1200), reviewTimeMinutes = 1290)
        val habits = listOf(habitEntity(id = "h1", reminderMinutes = 600, status = HabitStatus.ACTIVE))

        val slots = ReminderScheduler.slotsOf(settings, habits)

        assertEquals(4, slots.size)
        assertEquals(2, slots.count { it.kind == SlotKind.GLOBAL })
        assertTrue(slots.any { it.kind == SlotKind.GLOBAL && it.key == "480" && it.minutesOfDay == 480 })
        assertTrue(slots.any { it.kind == SlotKind.GLOBAL && it.key == "1200" && it.minutesOfDay == 1200 })
        assertTrue(slots.any { it.kind == SlotKind.HABIT && it.key == "h1" && it.minutesOfDay == 600 })
        assertTrue(slots.any { it.kind == SlotKind.REVIEW && it.key == "" && it.minutesOfDay == 1290 })
    }

    @Test
    fun `paused, archived and reminderless habits schedule nothing`() {
        val settings = Settings(globalReminderMinutes = emptyList(), reviewTimeMinutes = 1290)
        val habits =
            listOf(
                habitEntity(id = "paused", reminderMinutes = 600, status = HabitStatus.PAUSED),
                habitEntity(id = "archived", reminderMinutes = 700, status = HabitStatus.ARCHIVED),
                habitEntity(id = "no-reminder", reminderMinutes = null, status = HabitStatus.ACTIVE),
            )

        val slots = ReminderScheduler.slotsOf(settings, habits)

        assertEquals(1, slots.size)
        assertEquals(SlotKind.REVIEW, slots.single().kind)
    }

    @Test
    fun `slot keys are stable and distinct`() {
        val settings = Settings(globalReminderMinutes = listOf(480, 1200), reviewTimeMinutes = 1290)
        val habits = listOf(habitEntity(id = "h1", reminderMinutes = 600, status = HabitStatus.ACTIVE))

        val slots = ReminderScheduler.slotsOf(settings, habits)
        val requestCodes = slots.map(ReminderScheduler::requestCodeOf)

        assertEquals(slots.size, requestCodes.toSet().size)
        assertEquals(
            requestCodes,
            ReminderScheduler.slotsOf(settings, habits).map(ReminderScheduler::requestCodeOf),
        )
    }
}
