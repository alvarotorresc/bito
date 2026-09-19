package com.alvarotc.bito.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay

/**
 * La leyenda de plazo de una fila de tarea. La decide dueKind: una FECHA habla de dias, una
 * SEMANA habla de la semana, y una suelta no dice nada — no tiene plazo y fingirlo seria mentir.
 */
@Composable
fun taskDueLabel(
    dueKind: DueKind,
    dueDay: LogicalDay?,
    today: LogicalDay,
): String? {
    if (dueDay == null) return null
    val delta = dueDay - today
    return when (dueKind) {
        DueKind.NONE -> null
        DueKind.WEEK ->
            when {
                delta < 0 -> stringResource(R.string.task_due_last_week)
                delta == 0 -> stringResource(R.string.task_due_week_today)
                else -> stringResource(R.string.task_due_this_week)
            }
        DueKind.DATE ->
            when {
                delta < 0 -> stringResource(R.string.task_overdue, -delta)
                delta == 0 -> stringResource(R.string.task_due_today)
                else -> stringResource(R.string.task_due_in, delta)
            }
    }
}

/**
 * Solo una FECHA vencida tiñe la leyenda de Peligro — y la LEYENDA, no la fila: no se regaña.
 * Una semana pasada se queda en TintaSuave, mas suave todavia: sigue saliendo todos los dias,
 * que ya es recordatorio de sobra, y la app no le puso fecha, se la puso el calendario.
 */
fun taskDueOverdue(
    dueKind: DueKind,
    dueDay: LogicalDay?,
    today: LogicalDay,
): Boolean = dueKind == DueKind.DATE && dueDay != null && dueDay < today
