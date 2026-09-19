package com.alvarotc.bito.ui.widget

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.HabiPose
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.TargetChange
import com.alvarotc.bito.domain.model.equippedSetOf
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.TodayUiState
import com.alvarotc.bito.ui.today.buildTodayUiState
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TODAY = 20679

class WidgetModelTest {
    private fun card(
        id: String,
        kind: CardKind = CardKind.CHECK,
        done: Boolean = false,
    ) = HabitCardUi(
        id = id, name = id, kind = kind, progress = 0, target = 1, unit = null,
        step = 3, direction = com.alvarotc.bito.domain.model.Direction.AT_LEAST,
        period = com.alvarotc.bito.domain.model.Period.DAY,
        doneToday = done, failed = false, streak = 0,
    )

    private fun state(
        vararg cards: HabitCardUi,
        spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, equippedSetOf(emptyList())),
    ) = TodayUiState(today = TODAY, ringDone = 0, ringTotal = cards.size, cards = cards.toList(), spec = spec, loading = false)

    private fun dailyCheck(
        id: String,
        createdOnDay: Int,
    ) = Habit(
        id = id,
        name = id,
        metric = Metric.CHECK,
        period = com.alvarotc.bito.domain.model.Period.DAY,
        direction = com.alvarotc.bito.domain.model.Direction.AT_LEAST,
        target = 1,
        logMode = LogMode.BINARY,
        createdOnDay = createdOnDay,
    )

    private fun stateOf(habits: List<Habit>) =
        DomainState(
            habits = habits,
            targetChanges = habits.map { TargetChange(it.id, it.createdOnDay, it.target) },
        )

    @Test
    fun `completed habits disappear from the widget`() {
        val model = buildWidgetModel(state(card("agua"), card("cama", done = true)), selectedIds = null)
        assertEquals(listOf("agua"), model.items.map { it.habitId })
        assertEquals(1, model.done)
        assertEquals(2, model.total)
    }

    @Test
    fun `an instance only sees its selection and measures progress over it`() {
        val model =
            buildWidgetModel(
                state(card("agua"), card("cama", done = true), card("pasos")),
                selectedIds = setOf("cama", "pasos"),
            )
        assertEquals(listOf("pasos"), model.items.map { it.habitId })
        assertEquals(1, model.done)
        assertEquals(2, model.total)
    }

    @Test
    fun `an empty selection means every habit`() {
        val model = buildWidgetModel(state(card("agua")), selectedIds = emptySet())
        assertEquals(1, model.total)
    }

    @Test
    fun `only check and counter cards log from the widget`() {
        val model =
            buildWidgetModel(
                state(card("agua", CardKind.COUNTER), card("guitarra", CardKind.DURATION), card("fumar", CardKind.ABSTINENCE)),
                selectedIds = null,
            )
        assertTrue(model.items.first { it.habitId == "agua" }.tapLogs)
        assertEquals(3, model.items.first { it.habitId == "agua" }.step)
        assertFalse(model.items.first { it.habitId == "guitarra" }.tapLogs)
        assertFalse(model.items.first { it.habitId == "fumar" }.tapLogs)
    }

    @Test
    fun `the widget's Habi mirrors the live mood, personality and equipped look`() {
        val spec = HabiSpec(Mood.RADIANT, Personality.CHEERLEADER, equippedSetOf(listOf("body-dorado")))
        val model = buildWidgetModel(state(card("agua"), spec = spec), selectedIds = null)
        assertEquals(Mood.RADIANT, model.spec.mood)
        assertEquals(Personality.CHEERLEADER, model.spec.personality)
        assertEquals("body-dorado", model.spec.equipped.bodyColor)
    }

    @Test
    fun `the widget model carries the day pose and the eye level`() {
        val state =
            buildTodayUiState(
                stateOf(listOf(dailyCheck(id = "cama", createdOnDay = TODAY))),
                emptyMap(),
                TODAY,
                minutesOfDay = 22 * 60,
                reviewTimeMinutes = 21 * 60 + 30,
                eyesPainted = 1,
            )

        val model = buildWidgetModel(state, selectedIds = null)

        assertEquals(HabiPose.WAITING, model.spec.pose)
        assertEquals(1, model.spec.eyesPainted)
    }

    @Test
    fun `the widget clock takes the logical day and the minute of day from the same instant`() {
        val zone = ZoneId.of("UTC")
        val cutoffMinutes = 4 * 60
        val justAfterCutoff = LocalDateTime.of(2026, 1, 2, 4, 5).atZone(zone).toInstant().toEpochMilli()

        val clock = widgetClock(justAfterCutoff, cutoffMinutes, zone)

        // Ya pasado el corte de las 4: el dia logico es el nuevo, 2 de enero, no el 1.
        assertEquals(LocalDate.of(2026, 1, 2).toEpochDay().toInt(), clock.today)
        // El minuto del dia es el reloj de pared tal cual, sin el ajuste del corte.
        assertEquals(4 * 60 + 5, clock.minutesOfDay)
    }
}
