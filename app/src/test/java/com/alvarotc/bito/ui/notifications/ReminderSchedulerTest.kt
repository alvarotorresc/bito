package com.alvarotc.bito.ui.notifications

import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.model.HabitStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSchedulerTest {
    // taskNoticesEnabled defaults to true (D8: on by default), so a default Settings() now also
    // schedules a TASKS slot — these two counts recalibrate from 4 and 1 to 5 and 2.
    @Test
    fun `slots cover global hours, active habit reminders and the review`() {
        val settings = Settings(globalReminderMinutes = listOf(480, 1200), reviewTimeMinutes = 1290)
        val habits = listOf(habitEntity(id = "h1", reminderMinutes = 600, status = HabitStatus.ACTIVE))

        val slots = ReminderScheduler.slotsOf(settings, habits)

        assertEquals(5, slots.size)
        assertEquals(2, slots.count { it.kind == SlotKind.GLOBAL })
        assertTrue(slots.any { it.kind == SlotKind.GLOBAL && it.key == "480" && it.minutesOfDay == 480 })
        assertTrue(slots.any { it.kind == SlotKind.GLOBAL && it.key == "1200" && it.minutesOfDay == 1200 })
        assertTrue(slots.any { it.kind == SlotKind.HABIT && it.key == "h1" && it.minutesOfDay == 600 })
        assertTrue(slots.any { it.kind == SlotKind.REVIEW && it.key == "" && it.minutesOfDay == 1290 })
        assertTrue(slots.any { it.kind == SlotKind.TASKS && it.key == "" && it.minutesOfDay == 12 * 60 })
    }

    @Test
    fun `paused, archived and reminderless habits schedule nothing beyond review and tasks`() {
        val settings = Settings(globalReminderMinutes = emptyList(), reviewTimeMinutes = 1290)
        val habits =
            listOf(
                habitEntity(id = "paused", reminderMinutes = 600, status = HabitStatus.PAUSED),
                habitEntity(id = "archived", reminderMinutes = 700, status = HabitStatus.ARCHIVED),
                habitEntity(id = "no-reminder", reminderMinutes = null, status = HabitStatus.ACTIVE),
            )

        val slots = ReminderScheduler.slotsOf(settings, habits)

        assertEquals(2, slots.size)
        assertEquals(setOf(SlotKind.REVIEW, SlotKind.TASKS), slots.map { it.kind }.toSet())
    }

    @Test
    fun `the tasks slot exists at noon when the switch is on`() {
        val slots = ReminderScheduler.slotsOf(Settings(taskNoticesEnabled = true), emptyList())

        val tasks = slots.single { it.kind == SlotKind.TASKS }
        assertEquals("", tasks.key)
        assertEquals(12 * 60, tasks.minutesOfDay)
    }

    @Test
    fun `the tasks slot disappears when the switch is off`() {
        val slots = ReminderScheduler.slotsOf(Settings(taskNoticesEnabled = false), emptyList())

        assertTrue(slots.none { it.kind == SlotKind.TASKS })
    }

    @Test
    fun `the tasks slot does not collide with a global reminder at the same hour`() {
        val settings = Settings(globalReminderMinutes = listOf(12 * 60), taskNoticesEnabled = true)

        val codes = ReminderScheduler.slotsOf(settings, emptyList()).map(ReminderScheduler::requestCodeOf)

        assertEquals(codes.size, codes.toSet().size)
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

    @Test
    fun `the breathing slot exists only while its switch is on, at its own hour`() {
        val on = ReminderScheduler.slotsOf(Settings(breathingReminderEnabled = true, breathingReminderTimeMinutes = 7 * 60), emptyList())
        val off = ReminderScheduler.slotsOf(Settings(breathingReminderEnabled = false), emptyList())

        val breathing = on.single { it.kind == SlotKind.BREATHING }
        assertEquals("", breathing.key)
        assertEquals(7 * 60, breathing.minutesOfDay)
        assertTrue(off.none { it.kind == SlotKind.BREATHING })
    }

    @Test
    fun `the breathing slot does not collide with the review or a global hour at the same minute`() {
        val settings =
            Settings(
                globalReminderMinutes = listOf(22 * 60),
                reviewTimeMinutes = 22 * 60,
                breathingReminderEnabled = true,
                breathingReminderTimeMinutes = 22 * 60,
            )

        val codes = ReminderScheduler.slotsOf(settings, emptyList()).map(ReminderScheduler::requestCodeOf)

        assertEquals(codes.size, codes.toSet().size)
    }
}
