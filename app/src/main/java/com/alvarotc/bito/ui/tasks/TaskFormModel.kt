package com.alvarotc.bito.ui.tasks

import com.alvarotc.bito.data.db.TaskEntity
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay

/** Form state for creating or editing a task. The three degrees of due date share one shape. */
data class TaskFormState(
    val editingId: String? = null,
    val title: String = "",
    val firstStep: String = "",
    val dueKind: DueKind = DueKind.NONE,
    val dueDay: LogicalDay? = null,
) {
    val isEditing: Boolean get() = editingId != null
    val canSave: Boolean get() = title.isNotBlank()
}

/**
 * The [LogicalDay] this state actually commits to, given [today]. NONE has none; WEEK re-anchors
 * ALWAYS to the current week's Sunday — saying "this week" again is re-committing, not keeping a
 * stale one; DATE hands back whatever was chosen.
 */
fun TaskFormState.resolvedDueDay(today: LogicalDay): LogicalDay? =
    when (dueKind) {
        DueKind.NONE -> null
        DueKind.WEEK -> Tasks.weekDueOf(today)
        DueKind.DATE -> dueDay
    }

/** Recovers the form shape a stored task was built from, blanks included. */
fun TaskEntity.toFormState(): TaskFormState =
    TaskFormState(
        editingId = id,
        title = title,
        firstStep = firstStep.orEmpty(),
        dueKind = dueKind,
        dueDay = dueDay,
    )
