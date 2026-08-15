package com.alvarotc.bito.ui.habitform

import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TODAY = 20_000
private const val NOW_MILLIS = 1_000L
private const val SORT_ORDER = 5
private const val NEW_ID = "new-id"

class HabitFormModelTest {
    // --- Preset matrix: form -> entity -------------------------------------------------

    @Test
    fun `DAILY_CHECK maps to a CHECK habit with target 1`() {
        val form = HabitFormState(name = "Hacer la cama", preset = HabitPreset.DAILY_CHECK)
        val entity = form.toNewEntity(NEW_ID, TODAY, NOW_MILLIS, SORT_ORDER)

        assertEquals("Hacer la cama", entity.name)
        assertEquals(Metric.CHECK, entity.metric)
        assertEquals(Period.DAY, entity.period)
        assertEquals(Direction.AT_LEAST, entity.direction)
        assertEquals(1, entity.target)
        assertNull(entity.unit)
        assertEquals(LogMode.COUNTER, entity.logMode)
        assertCommonFields(entity)

        val recovered = entity.toFormState()
        assertEquals(HabitPreset.DAILY_CHECK, recovered.preset)
        assertEquals(Period.DAY, recovered.period)
        assertEquals(1, recovered.target)
        assertFalse(recovered.binaryMode)
    }

    @Test
    fun `QUANTITY maps to a COUNT habit with the given unit`() {
        val form = HabitFormState(name = "Agua", preset = HabitPreset.QUANTITY, target = 8, unit = "vasos", period = Period.DAY)
        val entity = form.toNewEntity(NEW_ID, TODAY, NOW_MILLIS, SORT_ORDER)

        assertEquals("Agua", entity.name)
        assertEquals(Metric.COUNT, entity.metric)
        assertEquals(Period.DAY, entity.period)
        assertEquals(Direction.AT_LEAST, entity.direction)
        assertEquals(8, entity.target)
        assertEquals("vasos", entity.unit)
        assertEquals(LogMode.COUNTER, entity.logMode)
        assertCommonFields(entity)

        val recovered = entity.toFormState()
        assertEquals(HabitPreset.QUANTITY, recovered.preset)
        assertEquals(Period.DAY, recovered.period)
        assertEquals(8, recovered.target)
        assertFalse(recovered.binaryMode)
    }

    @Test
    fun `QUANTITY with binaryMode maps to BINARY logMode and a blank unit becomes null`() {
        val form = HabitFormState(name = "Pasos", preset = HabitPreset.QUANTITY, target = 9000, binaryMode = true)
        val entity = form.toNewEntity(NEW_ID, TODAY, NOW_MILLIS, SORT_ORDER)

        assertEquals("Pasos", entity.name)
        assertEquals(Metric.COUNT, entity.metric)
        assertEquals(Period.DAY, entity.period)
        assertEquals(Direction.AT_LEAST, entity.direction)
        assertEquals(9000, entity.target)
        assertNull(entity.unit)
        assertEquals(LogMode.BINARY, entity.logMode)
        assertCommonFields(entity)

        val recovered = entity.toFormState()
        assertEquals(HabitPreset.QUANTITY, recovered.preset)
        assertTrue(recovered.binaryMode)
    }

    @Test
    fun `DURATION maps to a DURATION habit with no unit`() {
        val form = HabitFormState(name = "Guitarra", preset = HabitPreset.DURATION, target = 20, period = Period.DAY)
        val entity = form.toNewEntity(NEW_ID, TODAY, NOW_MILLIS, SORT_ORDER)

        assertEquals("Guitarra", entity.name)
        assertEquals(Metric.DURATION, entity.metric)
        assertEquals(Period.DAY, entity.period)
        assertEquals(Direction.AT_LEAST, entity.direction)
        assertEquals(20, entity.target)
        assertNull(entity.unit)
        assertEquals(LogMode.COUNTER, entity.logMode)
        assertCommonFields(entity)

        val recovered = entity.toFormState()
        assertEquals(HabitPreset.DURATION, recovered.preset)
        assertEquals(Period.DAY, recovered.period)
        assertEquals(20, recovered.target)
        assertFalse(recovered.binaryMode)
    }

    @Test
    fun `WEEKLY_TIMES maps to a CHECK habit over WEEK regardless of the form period`() {
        val form = HabitFormState(name = "Fuerza", preset = HabitPreset.WEEKLY_TIMES, target = 3, period = Period.DAY)
        val entity = form.toNewEntity(NEW_ID, TODAY, NOW_MILLIS, SORT_ORDER)

        assertEquals("Fuerza", entity.name)
        assertEquals(Metric.CHECK, entity.metric)
        assertEquals(Period.WEEK, entity.period)
        assertEquals(Direction.AT_LEAST, entity.direction)
        assertEquals(3, entity.target)
        assertNull(entity.unit)
        assertEquals(LogMode.COUNTER, entity.logMode)
        assertCommonFields(entity)

        val recovered = entity.toFormState()
        assertEquals(HabitPreset.WEEKLY_TIMES, recovered.preset)
        assertEquals(Period.WEEK, recovered.period)
        assertEquals(3, recovered.target)
        assertFalse(recovered.binaryMode)
    }

    @Test
    fun `QUIT TOTAL maps to a ZERO habit with target 0 regardless of the form target`() {
        val form = HabitFormState(name = "No fumar", preset = HabitPreset.QUIT, quitMode = QuitMode.TOTAL, target = 1)
        val entity = form.toNewEntity(NEW_ID, TODAY, NOW_MILLIS, SORT_ORDER)

        assertEquals("No fumar", entity.name)
        assertEquals(Metric.CHECK, entity.metric)
        assertEquals(Period.DAY, entity.period)
        assertEquals(Direction.ZERO, entity.direction)
        assertEquals(0, entity.target)
        assertNull(entity.unit)
        assertEquals(LogMode.COUNTER, entity.logMode)
        assertCommonFields(entity)

        val recovered = entity.toFormState()
        assertEquals(HabitPreset.QUIT, recovered.preset)
        assertEquals(QuitMode.TOTAL, recovered.quitMode)
        assertEquals(0, recovered.target)
    }

    @Test
    fun `QUIT LIMIT DURATION maps to an AT_MOST DURATION habit`() {
        val form =
            HabitFormState(
                name = "Redes",
                preset = HabitPreset.QUIT,
                quitMode = QuitMode.LIMIT,
                limitMetric = Metric.DURATION,
                target = 30,
                period = Period.DAY,
            )
        val entity = form.toNewEntity(NEW_ID, TODAY, NOW_MILLIS, SORT_ORDER)

        assertEquals("Redes", entity.name)
        assertEquals(Metric.DURATION, entity.metric)
        assertEquals(Period.DAY, entity.period)
        assertEquals(Direction.AT_MOST, entity.direction)
        assertEquals(30, entity.target)
        assertNull(entity.unit)
        assertEquals(LogMode.COUNTER, entity.logMode)
        assertCommonFields(entity)

        val recovered = entity.toFormState()
        assertEquals(HabitPreset.QUIT, recovered.preset)
        assertEquals(QuitMode.LIMIT, recovered.quitMode)
        assertEquals(Metric.DURATION, recovered.limitMetric)
        assertEquals(Period.DAY, recovered.period)
        assertEquals(30, recovered.target)
    }

    @Test
    fun `QUIT LIMIT COUNT maps to an AT_MOST COUNT habit over WEEK`() {
        val form =
            HabitFormState(
                name = "Domicilio",
                preset = HabitPreset.QUIT,
                quitMode = QuitMode.LIMIT,
                limitMetric = Metric.COUNT,
                target = 1,
                period = Period.WEEK,
            )
        val entity = form.toNewEntity(NEW_ID, TODAY, NOW_MILLIS, SORT_ORDER)

        assertEquals("Domicilio", entity.name)
        assertEquals(Metric.COUNT, entity.metric)
        assertEquals(Period.WEEK, entity.period)
        assertEquals(Direction.AT_MOST, entity.direction)
        assertEquals(1, entity.target)
        assertNull(entity.unit)
        assertEquals(LogMode.COUNTER, entity.logMode)
        assertCommonFields(entity)

        val recovered = entity.toFormState()
        assertEquals(HabitPreset.QUIT, recovered.preset)
        assertEquals(QuitMode.LIMIT, recovered.quitMode)
        assertEquals(Metric.COUNT, recovered.limitMetric)
        assertEquals(Period.WEEK, recovered.period)
        assertEquals(1, recovered.target)
    }

    @Test
    fun `step is honored on the new entity`() {
        val form = HabitFormState(name = "Agua", preset = HabitPreset.QUANTITY, target = 8, unit = "vasos", step = 5)
        val entity = form.toNewEntity(NEW_ID, TODAY, NOW_MILLIS, SORT_ORDER)

        assertEquals(5, entity.step)
    }

    // --- defaultTargetFor ----------------------------------------------------------------

    @Test
    fun `defaultTargetFor returns the preset default`() {
        assertEquals(1, defaultTargetFor(HabitPreset.DAILY_CHECK, QuitMode.TOTAL, Metric.DURATION))
        assertEquals(8, defaultTargetFor(HabitPreset.QUANTITY, QuitMode.TOTAL, Metric.DURATION))
        assertEquals(20, defaultTargetFor(HabitPreset.DURATION, QuitMode.TOTAL, Metric.DURATION))
        assertEquals(3, defaultTargetFor(HabitPreset.WEEKLY_TIMES, QuitMode.TOTAL, Metric.DURATION))
        assertEquals(0, defaultTargetFor(HabitPreset.QUIT, QuitMode.TOTAL, Metric.DURATION))
        assertEquals(30, defaultTargetFor(HabitPreset.QUIT, QuitMode.LIMIT, Metric.DURATION))
        assertEquals(1, defaultTargetFor(HabitPreset.QUIT, QuitMode.LIMIT, Metric.COUNT))
    }

    // --- HabitFormState derived properties ------------------------------------------------

    @Test
    fun `canSave requires a non-blank name`() {
        val form = HabitFormState(name = "  ", preset = HabitPreset.QUANTITY, target = 8)
        assertFalse(form.canSave)
    }

    @Test
    fun `canSave allows QUIT TOTAL with a zero target`() {
        val form = HabitFormState(name = "No fumar", preset = HabitPreset.QUIT, quitMode = QuitMode.TOTAL, target = 0)
        assertTrue(form.canSave)
    }

    @Test
    fun `canSave rejects a non-QUIT-TOTAL preset with a zero target`() {
        val form = HabitFormState(name = "Agua", preset = HabitPreset.QUANTITY, target = 0)
        assertFalse(form.canSave)
    }

    @Test
    fun `isEditing reflects whether editingId is set`() {
        assertFalse(HabitFormState().isEditing)
        assertTrue(HabitFormState(editingId = "h1").isEditing)
    }

    private fun assertCommonFields(
        entity: HabitEntity,
        step: Int = 1,
    ) {
        assertEquals(NEW_ID, entity.id)
        assertEquals(HabitStatus.ACTIVE, entity.status)
        assertEquals(TODAY, entity.createdOnDay)
        assertEquals(NOW_MILLIS, entity.createdAtMillis)
        assertNull(entity.archivedOnDay)
        assertNull(entity.archivedAtMillis)
        assertNull(entity.timeBucket)
        assertNull(entity.timeOfDayMinutes)
        assertNull(entity.reminderMinutes)
        assertEquals(SORT_ORDER, entity.sortOrder)
        assertEquals(step, entity.step)
    }
}
