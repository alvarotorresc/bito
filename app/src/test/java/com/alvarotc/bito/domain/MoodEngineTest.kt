package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.TaskEvent
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Habi's mood: a pure function of the closed 7-day window [TODAY-7, TODAY-1]
 * plus the prolonged-absence rule. Pending and paused periods take part in
 * neither side of the ratio.
 */
class MoodEngineTest {
    private val firstWindowDay = TODAY - 7
    private val lastWindowDay = TODAY - 1

    /** Days of the window on which the bed was made. */
    private fun windowWith(vararg madeBedOn: Int) =
        domainState(
            habits = listOf(RealHabits.makeBed),
            entries = entriesOn(RealHabits.makeBed, madeBedOn.toList()),
        )

    // -----------------------------------------------------------------------
    // Ausencia prolongada
    // -----------------------------------------------------------------------

    @Test
    fun `three logical days without any activity turn Habi dramatic`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3)

        assertEquals(Mood.DRAMATIC, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 3))
    }

    @Test
    fun `a long absence stays dramatic whatever the window says`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3, TODAY - 2, TODAY - 1)

        assertEquals(Mood.DRAMATIC, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 10))
    }

    @Test
    fun `two days without activity are not drama yet`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3, TODAY - 2)

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 2))
    }

    @Test
    fun `activity today is never drama`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3, TODAY - 2, TODAY - 1)

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY))
    }

    @Test
    fun `a fresh start with no history at all is normal, never drama`() {
        assertEquals(Mood.NORMAL, MoodEngine.moodOf(domainState(), TODAY, lastActivityDay = null))
    }

    @Test
    fun `a user who has never logged anything is never dramatic`() {
        val state = domainState(habits = listOf(RealHabits.makeBed))

        assertNotEquals(Mood.DRAMATIC, MoodEngine.moodOf(state, TODAY, lastActivityDay = null))
    }

    // -----------------------------------------------------------------------
    // Ratio de la ventana
    // -----------------------------------------------------------------------

    @Test
    fun `a spotless week makes Habi radiant`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3, TODAY - 2, TODAY - 1)

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a ratio just over eighty percent is still radiant`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3, TODAY - 2)

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a middling week keeps Habi normal`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4, TODAY - 3)

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `exactly eighty percent is normal, not radiant`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = entriesOn(RealHabits.makeBed, listOf(TODAY - 7, TODAY - 6, TODAY - 5, TODAY - 4)),
                pauses = listOf(pauseOn(RealHabits.makeBed, TODAY - 2, TODAY - 1)),
            )

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `exactly fifty percent is normal, not wilted`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = entriesOn(RealHabits.makeBed, listOf(TODAY - 7, TODAY - 6, TODAY - 5)),
                pauses = listOf(pauseOn(RealHabits.makeBed, TODAY - 1, TODAY - 1)),
            )

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a bad week wilts Habi`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5)

        assertEquals(Mood.WILTED, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a week with nothing done at all wilts Habi`() {
        val state = domainState(habits = listOf(RealHabits.makeBed))

        assertEquals(Mood.WILTED, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a window without a single decided period is normal`() {
        val state = domainState(habits = listOf(RealHabits.makeBed.createdOn(TODAY)))

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY))
    }

    @Test
    fun `a fully paused window is normal — pauses count for neither side`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                pauses = listOf(pauseOn(RealHabits.makeBed, firstWindowDay, lastWindowDay)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `unsealed abstinences stay pending and never drag the mood down`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.noSmoking),
                entries = entriesOn(RealHabits.makeBed, firstWindowDay..lastWindowDay),
            )

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    // -----------------------------------------------------------------------
    // Límites de la ventana
    // -----------------------------------------------------------------------

    @Test
    fun `the window ends yesterday — what happens today does not count yet`() {
        val state = windowWith(TODAY - 7, TODAY - 6, TODAY - 5, TODAY)

        assertEquals(Mood.WILTED, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY))
    }

    @Test
    fun `the window starts seven days back — older days do not count`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = entriesOn(RealHabits.makeBed, (TODAY - 7)..(TODAY - 2)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    // -----------------------------------------------------------------------
    // Hábitos semanales
    // -----------------------------------------------------------------------

    @Test
    fun `a weekly habit counts once, on the window that holds its closing day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries = entriesOn(RealHabits.strengthTraining, listOf(LAST_MONDAY, LAST_WEDNESDAY, LAST_FRIDAY)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `a missed week weighs on the mood once its sunday is inside the window`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(Mood.WILTED, MoodEngine.moodOf(state, TODAY, lastActivityDay = TODAY - 1))
    }

    // -----------------------------------------------------------------------
    // Tareas: matizan el animo de los habitos, con dos topes
    // -----------------------------------------------------------------------

    // MoodEngine no expone el ratio interno, solo el Mood, asi que estos dos primeros casos usan
    // ventanas calibradas al borde de cada umbral (ver nineDecidedWindow): se fija el SIGNO del
    // efecto de las tareas (empuja a RADIANT / hunde a WILTED), no un numero interno.

    @Test
    fun `tasks done and attempted inside the window push the ratio up`() {
        // 9 decididos, 7 cumplidos -> 7/9 = 0,778, por debajo de RADIANT_RATIO = 0,8 (NORMAL).
        // Una tarea hecha y un intento (dos tareas distintas, dos unidades, peso 1 porque
        // cap = 9/2 = 4,5 >= 2) la empujan a 9/11 = 0,818 -> RADIANT.
        val base = nineDecidedWindow(kept = 7)
        assertEquals(Mood.NORMAL, MoodEngine.moodOf(base, TODAY, lastActivityDay = TODAY - 1))

        val withTasks =
            base.copy(
                tasks = listOf(task(id = "t1", status = TaskStatus.DONE, doneOnDay = TODAY - 2)),
                taskEvents = listOf(taskEvent(taskId = "t2", kind = TaskEventKind.ATTEMPT, day = TODAY - 3)),
            )

        assertEquals(Mood.RADIANT, MoodEngine.moodOf(withTasks, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `postponing inside the window pushes the ratio down`() {
        // 9 decididos, 5 cumplidos -> 5/9 = 0,556, por encima de WILTED_RATIO = 0,5 (NORMAL).
        // Dos tareas distintas pospuestas (dos unidades, mismo peso 1) la hunden a 5/11 = 0,455
        // -> WILTED.
        val base = nineDecidedWindow(kept = 5)
        assertEquals(Mood.NORMAL, MoodEngine.moodOf(base, TODAY, lastActivityDay = TODAY - 1))

        val withPostponed =
            base.copy(
                taskEvents =
                    listOf(
                        taskEvent(taskId = "t1", kind = TaskEventKind.POSTPONED, day = TODAY - 2),
                        taskEvent(taskId = "t2", kind = TaskEventKind.POSTPONED, day = TODAY - 2),
                    ),
            )

        assertEquals(Mood.WILTED, MoodEngine.moodOf(withPostponed, TODAY, lastActivityDay = TODAY - 1))
    }

    @Test
    fun `at most one positive and one negative per task and day`() {
        // halfKeptWindow (cap=2.0) es vacua aqui: con o sin deduplicar, 20 intentos del mismo
        // dia dan NORMAL en los dos casos. nineDecidedWindow(7) (cap=4.5) si distingue: sin
        // deduplicar, el peso capado (4.5/20) aun suma 4.5 puntos -> 11,5/13,5 = 0,85 -> RADIANT;
        // deduplicado a una unidad, 8/10 = 0,80 -> NORMAL.
        val base = nineDecidedWindow(kept = 7)
        val once =
            base.copy(
                taskEvents = listOf(taskEvent(taskId = "t1", kind = TaskEventKind.ATTEMPT, day = TODAY - 2)),
            )
        val twenty =
            base.copy(
                taskEvents = (1..20).map { taskEvent(taskId = "t1", kind = TaskEventKind.ATTEMPT, day = TODAY - 2) },
            )

        assertEquals(MoodEngine.moodOf(once, TODAY, TODAY - 1), MoodEngine.moodOf(twenty, TODAY, TODAY - 1))
    }

    @Test
    fun `tasks never weigh more than a third of the window`() {
        // Los dos casos que la spec §5.2 deja medidos: 10 habitos cumplidos y 5 o 20 pospuestas.
        val fivePostponed = allKeptWindow(10).copy(taskEvents = postponedOn(count = 5))
        val twentyPostponed = allKeptWindow(10).copy(taskEvents = postponedOn(count = 20))

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(fivePostponed, TODAY, TODAY - 1))
        assertEquals(Mood.NORMAL, MoodEngine.moodOf(twentyPostponed, TODAY, TODAY - 1))
    }

    @Test
    fun `with no decided habits in the window tasks do not move the mood`() {
        val onlyTasks =
            domainState(
                taskEvents = postponedOn(count = 20),
                tasks = (1..20).map { task(id = "t$it") },
            )

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(onlyTasks, TODAY, TODAY - 1))
    }

    @Test
    fun `three days of only tasks do not turn her dramatic`() {
        val state =
            domainState(
                tasks = listOf(task(id = "t1", status = TaskStatus.DONE, doneOnDay = TODAY)),
            )

        assertEquals(Mood.NORMAL, MoodEngine.moodOf(state, TODAY, StatsEngine.lastActivityDay(state)))
    }

    /**
     * Ventana de 9 decididos: make-bed vivo toda la ventana de 7 dias ([kept] cumplidos de 7) mas
     * un segundo habito (meditate) de alta TODAY-2 sin entradas — vivo solo los 2 ultimos dias de
     * la ventana, siempre fallidos. Decididos = 7 + 2 = 9 siempre; cumplidos = [kept]. Calibra los
     * dos primeros tests al borde de cada umbral y da un cap (4,5) que distingue deduplicar de no
     * deduplicar en el tercero.
     */
    private fun nineDecidedWindow(kept: Int): DomainState =
        domainState(
            habits = listOf(RealHabits.makeBed, RealHabits.meditate.createdOn(TODAY - 2)),
            entries = entriesOn(RealHabits.makeBed, (TODAY - kept) until TODAY),
        )

    /** Ventana con [count] habitos-periodo decididos y TODOS cumplidos. */
    private fun allKeptWindow(count: Int): DomainState {
        val habits = (1..count).map { RealHabits.makeBed.copy(id = "kept-$it", createdOnDay = TODAY - 1) }
        return domainState(
            habits = habits,
            entries = habits.flatMap { entriesOn(it, listOf(TODAY - 1)) },
        )
    }

    private fun postponedOn(count: Int): List<TaskEvent> =
        (1..count).map { taskEvent(taskId = "t$it", kind = TaskEventKind.POSTPONED, day = TODAY - 2) }
}
