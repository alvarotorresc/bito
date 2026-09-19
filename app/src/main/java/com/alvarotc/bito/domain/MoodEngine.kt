package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.TaskEventKind

/** Habi's mood — a pure function of recent compliance. */
object MoodEngine {
    /** Days without a single entry or seal that turn Habi dramatic. */
    private const val DRAMATIC_SILENCE_DAYS = 3

    /** Length of the closed window the ratio looks at. */
    private const val WINDOW_DAYS = 7

    private const val RADIANT_RATIO = 0.8
    private const val WILTED_RATIO = 0.5

    /**
     * Mood on [today]:
     *
     * 1. DRAMATIC when [lastActivityDay] (see [StatsEngine.lastActivityDay],
     *    null if none ever) is 3+ days behind [today] [DECIDED 2026-08-14].
     *    Null never triggers drama — the ratio below decides (a genuinely
     *    fresh state has no decided periods -> NORMAL).
     * 2. Otherwise, compliance ratio over the closed 7-day window
     *    [today-7, today-1]: FULFILLED / (FULFILLED + FAILED) habit-periods.
     *    DAY-period habits contribute each requirable day; WEEK/MONTH habits
     *    contribute once, on windows containing their period's last day.
     *    PENDING and PAUSED periods stay out of both sides of the ratio.
     * 2 bis. Tasks done/attempted in the window nudge the ratio up, postponed
     *    tasks nudge it down — capped to at most one of each per (task, day)
     *    and, as a block, to never more than a third of the window (D15).
     * 3. ratio > 0.8 -> RADIANT; ratio < 0.5 -> WILTED; otherwise NORMAL.
     *    No decided periods in the window -> NORMAL.
     */
    fun moodOf(
        state: DomainState,
        today: LogicalDay,
        lastActivityDay: LogicalDay?,
    ): Mood {
        if (lastActivityDay != null && today - lastActivityDay >= DRAMATIC_SILENCE_DAYS) return Mood.DRAMATIC

        val window = (today - WINDOW_DAYS)..(today - 1)
        var fulfilled = 0
        var decided = 0
        for (habit in state.habits) {
            val firstKey = LogicalDays.periodKeyOf(window.first, habit.period)
            val lastKey = LogicalDays.periodKeyOf(window.last, habit.period)
            for (periodKey in firstKey..lastKey) {
                // A period weighs on the window that contains its close, so a week counts once.
                if (LogicalDays.daysOf(periodKey, habit.period).last !in window) continue
                when (Compliance.complianceOf(state, habit, periodKey, today)) {
                    ComplianceStatus.FULFILLED -> {
                        fulfilled++
                        decided++
                    }

                    ComplianceStatus.FAILED -> decided++
                    else -> Unit
                }
            }
        }
        if (decided == 0) return Mood.NORMAL

        // Las tareas matizan el animo de los habitos; no lo gobiernan. Dos topes:
        // 1. Como mucho un positivo y un negativo por (tarea, dia) — si no, «Empezar -> lo dejo»
        //    veinte veces en una tarde farmea animo. El tope va AQUI, en la derivacion pura, y
        //    nunca suprimiendo escrituras de eventos: los eventos son historia, y una
        //    restauracion que reprodujera eventos crudos daria un animo distinto del que vio el
        //    usuario.
        // 2. Las tareas no pasan nunca de un tercio de la ventana (D15): t/(h+t) <= 1/3 equivale
        //    a t <= h/2. El peso las escala EN BLOQUE, asi que la proporcion entre hechas y
        //    pospuestas se conserva exacta: capar no le cambia el signo a una semana, solo le
        //    baja el volumen.
        val positives =
            (
                state.tasks.asSequence().mapNotNull { t -> t.doneOnDay?.takeIf { it in window }?.let { t.id to it } } +
                    state.taskEvents.asSequence()
                        .filter { it.kind == TaskEventKind.ATTEMPT && it.logicalDay in window }
                        .map { it.taskId to it.logicalDay }
            ).toSet().size
        val negatives =
            state.taskEvents.asSequence()
                .filter { it.kind == TaskEventKind.POSTPONED && it.logicalDay in window }
                .map { it.taskId to it.logicalDay }
                .toSet()
                .size

        val units = positives + negatives
        val cap = decided / 2.0
        val weight = if (units == 0) 0.0 else minOf(1.0, cap / units)
        val ratio = (fulfilled + positives * weight) / (decided + units * weight)
        return when {
            ratio > RADIANT_RATIO -> Mood.RADIANT
            ratio < WILTED_RATIO -> Mood.WILTED
            else -> Mood.NORMAL
        }
    }
}
