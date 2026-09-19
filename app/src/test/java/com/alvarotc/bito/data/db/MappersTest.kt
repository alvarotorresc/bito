package com.alvarotc.bito.data.db

import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.data.taskEventEntity
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.Task
import com.alvarotc.bito.domain.model.TaskEvent
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    @Test
    fun `habit entity maps to the pure domain habit`() {
        val domain =
            habitEntity(
                id = "smoke",
                name = "No fumar",
                metric = Metric.CHECK,
                direction = Direction.ZERO,
                target = 0,
                unit = null,
                status = HabitStatus.ARCHIVED,
                createdOnDay = DAY_ZERO,
                archivedAtMillis = 5_000L,
                archivedOnDay = DAY_ZERO + 30,
            ).toDomain()
        assertEquals("smoke", domain.id)
        assertEquals(Metric.CHECK, domain.metric)
        assertEquals(Direction.ZERO, domain.direction)
        assertEquals(HabitStatus.ARCHIVED, domain.status)
        assertEquals(DAY_ZERO, domain.createdOnDay)
        assertEquals(DAY_ZERO + 30, domain.archivedOnDay)
    }

    @Test
    fun `entry and ledger entities map field by field`() {
        val entry = entryEntity(id = "e9", habitId = "h1", logicalDay = DAY_ZERO + 2, value = 30).toDomain()
        assertEquals("e9", entry.id)
        assertEquals(DAY_ZERO + 2, entry.logicalDay)
        assertEquals(30, entry.value)

        val ledger = pointsLedgerEntity(id = "p9", delta = -6, reason = PointsReason.BUY_FREEZER).toDomain()
        assertEquals(-6, ledger.delta)
        assertEquals(PointsReason.BUY_FREEZER, ledger.reason)
    }

    @Test
    fun `task entity maps field by field`() {
        val entity =
            taskEntity(
                id = "t1",
                title = "Llamar al banco",
                firstStep = "Buscar el numero",
                dueKind = DueKind.WEEK,
                dueDay = DAY_ZERO + 3,
                status = TaskStatus.DONE,
                createdAtMillis = 1_234L,
                createdOnDay = DAY_ZERO,
                doneAtMillis = 9_999L,
                doneOnDay = DAY_ZERO + 1,
            )

        assertEquals(
            Task(
                id = "t1",
                title = "Llamar al banco",
                firstStep = "Buscar el numero",
                dueKind = DueKind.WEEK,
                dueDay = DAY_ZERO + 3,
                status = TaskStatus.DONE,
                createdOnDay = DAY_ZERO,
                createdAtMillis = 1_234L,
                doneOnDay = DAY_ZERO + 1,
            ),
            entity.toDomain(),
        )
    }

    @Test
    fun `task event entity maps field by field`() {
        val entity = taskEventEntity(id = "e1", taskId = "t1", kind = TaskEventKind.POSTPONED, logicalDay = DAY_ZERO)

        assertEquals(TaskEvent("e1", "t1", TaskEventKind.POSTPONED, DAY_ZERO), entity.toDomain())
    }
}
