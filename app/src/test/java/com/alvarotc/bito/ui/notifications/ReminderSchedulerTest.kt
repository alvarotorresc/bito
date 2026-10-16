package com.alvarotc.bito.ui.notifications

import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.habla.AvisoPlan
import com.alvarotc.bito.domain.habla.PlanAvisos
import com.alvarotc.bito.domain.habla.TipoAviso
import com.alvarotc.bito.domain.model.HabitStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSchedulerTest {
    // taskNoticesEnabled defaults to true (D8: on by default), so a default Settings() also
    // schedules a TASKS slot. Los avisos de Habi salen del plan: sin plan, ni mañana ni noche.
    @Test
    fun `slots cover global hours, active habit reminders and tasks`() {
        val settings = Settings(globalReminderMinutes = listOf(480, 1200), habiNightMinutes = 1290)
        val habits = listOf(habitEntity(id = "h1", reminderMinutes = 600, status = HabitStatus.ACTIVE))

        val slots = ReminderScheduler.slotsOf(settings, habits)

        assertEquals(4, slots.size)
        assertEquals(2, slots.count { it.kind == SlotKind.GLOBAL })
        assertTrue(slots.any { it.kind == SlotKind.GLOBAL && it.key == "480" && it.minutesOfDay == 480 })
        assertTrue(slots.any { it.kind == SlotKind.GLOBAL && it.key == "1200" && it.minutesOfDay == 1200 })
        assertTrue(slots.any { it.kind == SlotKind.HABIT && it.key == "h1" && it.minutesOfDay == 600 })
        assertTrue(slots.any { it.kind == SlotKind.TASKS && it.key == "" && it.minutesOfDay == 12 * 60 })
    }

    @Test
    fun `paused, archived and reminderless habits schedule nothing beyond tasks`() {
        val settings = Settings(globalReminderMinutes = emptyList(), habiNightMinutes = 1290)
        val habits =
            listOf(
                habitEntity(id = "paused", reminderMinutes = 600, status = HabitStatus.PAUSED),
                habitEntity(id = "archived", reminderMinutes = 700, status = HabitStatus.ARCHIVED),
                habitEntity(id = "no-reminder", reminderMinutes = null, status = HabitStatus.ACTIVE),
            )

        val slots = ReminderScheduler.slotsOf(settings, habits)

        assertEquals(listOf(SlotKind.TASKS), slots.map { it.kind })
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
        val settings = Settings(globalReminderMinutes = listOf(480, 1200), habiNightMinutes = 1290)
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
    fun `the breathing slot does not collide with the night or a global hour at the same minute`() {
        val settings =
            Settings(
                globalReminderMinutes = listOf(22 * 60),
                habiNightMinutes = 22 * 60,
                breathingReminderEnabled = true,
                breathingReminderTimeMinutes = 22 * 60,
            )

        val noche = AvisoPlan(TipoAviso.NOCHE, PlanAvisos.CLAVE_NOCHE, 22 * 60, TODAY)
        val slots = ReminderScheduler.slotsOf(settings, emptyList(), listOf(noche))
        val codes = slots.map(ReminderScheduler::requestCodeOf)

        assertEquals(4, slots.size)
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `the night slot replaces the review slot`() {
        val noche = AvisoPlan(TipoAviso.NOCHE, PlanAvisos.CLAVE_NOCHE, 21 * 60 + 30, TODAY)
        val slots = ReminderScheduler.slotsOf(Settings(habiNightMinutes = 21 * 60 + 30), emptyList(), listOf(noche))

        assertTrue(slots.contains(Slot(SlotKind.HABI_NOCHE, "noche", 21 * 60 + 30)))
        assertTrue(slots.none { it.kind.name == "REVIEW" })
        assertTrue(SlotKind.entries.none { it.name == "REVIEW" })
    }

    @Test
    fun `habi slots come from the plan`() {
        val plan =
            listOf(
                AvisoPlan(TipoAviso.MANANA, PlanAvisos.CLAVE_MANANA, 8 * 60 + 30, TODAY),
                AvisoPlan(TipoAviso.NOCHE, PlanAvisos.CLAVE_NOCHE, 23 * 60, TODAY),
                AvisoPlan(TipoAviso.ACEPTADO, "AYER_SE_ESCAPO:h1:$TODAY:870", 14 * 60 + 30, TODAY),
            )

        val slots = ReminderScheduler.slotsOf(Settings(habiNightMinutes = 23 * 60 + 30, taskNoticesEnabled = false), emptyList(), plan)

        assertEquals(
            listOf(
                Slot(SlotKind.HABI_MANANA, "manana", 8 * 60 + 30),
                Slot(SlotKind.HABI_NOCHE, "noche", 23 * 60),
                Slot(SlotKind.ACEPTADO, "AYER_SE_ESCAPO:h1:$TODAY:870", 14 * 60 + 30),
            ),
            slots,
        )
    }

    @Test
    fun `a kept slot inside the silence waits for its end`() {
        val settings =
            Settings(
                globalReminderMinutes = listOf(7 * 60, 9 * 60),
                dndEnabled = true,
                dndFromMinutes = 23 * 60,
                dndToMinutes = 7 * 60 + 30,
                dayCutoffMinutes = 4 * 60,
                taskNoticesEnabled = false,
            )

        val slots = ReminderScheduler.slotsOf(settings, emptyList())

        assertEquals(listOf(Slot(SlotKind.GLOBAL, "420", 7 * 60 + 30), Slot(SlotKind.GLOBAL, "540", 9 * 60)), slots)
    }

    @Test
    fun `a kept slot is lost when the silence ends on the next day`() {
        val settings =
            Settings(
                globalReminderMinutes = listOf(23 * 60 + 30),
                dndEnabled = true,
                dndFromMinutes = 23 * 60,
                dndToMinutes = 7 * 60 + 30,
                dayCutoffMinutes = 4 * 60,
                taskNoticesEnabled = false,
            )

        assertTrue(ReminderScheduler.slotsOf(settings, emptyList()).isEmpty())
    }

    @Test
    fun `without the silence switch kept slots stay where they are`() {
        val settings =
            Settings(
                globalReminderMinutes = listOf(7 * 60),
                dndEnabled = false,
                dndFromMinutes = 23 * 60,
                dndToMinutes = 7 * 60 + 30,
                taskNoticesEnabled = false,
            )

        assertEquals(listOf(Slot(SlotKind.GLOBAL, "420", 7 * 60)), ReminderScheduler.slotsOf(settings, emptyList()))
    }
}
