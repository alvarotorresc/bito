package com.alvarotc.bito.ui.camino

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.Progreso
import com.alvarotc.bito.domain.testZone
import com.alvarotc.bito.ui.stats.buildNumbersUiState
import com.alvarotc.bito.ui.stats.buildRecordsUiState
import com.alvarotc.bito.ui.stats.buildStatsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CaminoUiStateTest {
    private fun camino(
        state: DomainState,
        badges: List<BadgeEntity> = emptyList(),
    ) = buildCaminoUiState(state, Personality.NEUTRA, TODAY, emptyList(), badges, 0, testZone)

    @Test
    fun `fourteen badges with progress, unlock day and special`() {
        val state = domainState(habits = listOf(RealHabits.makeBed))
        val ui = camino(state, listOf(BadgeEntity("first-habit", millisOn(TODAY - 3))))
        assertEquals(14, ui.logros.size)
        assertEquals(1, ui.conseguidos)
        assertEquals(TODAY - 3, ui.logros.single { it.def.id == "first-habit" }.conseguido)
        assertEquals(Progreso(0, 7), ui.logros.single { it.def.id == "streak-7" }.progreso)
        assertEquals(GardenSpecial.LANTERN, ui.logros.single { it.def.id == "streak-7" }.especial)
        assertEquals(null, ui.logros.single { it.def.id == "perfect-month" }.progreso)
    }

    @Test
    fun `without habits Camino says so and badges still come, all pending`() {
        val ui = camino(domainState())
        assertTrue(ui.sinHabitos)
        assertEquals(14, ui.logros.size)
        assertEquals(0, ui.conseguidos)
    }

    @Test
    fun `it carries what the four old screens showed`() {
        val state = domainState(habits = listOf(RealHabits.makeBed))
        val ui = camino(state)
        assertEquals(buildRecordsUiState(state, TODAY), ui.records)
        assertEquals(buildNumbersUiState(state, TODAY), ui.numbers)
        assertEquals(buildStatsUiState(state, Personality.NEUTRA, TODAY), ui.stats)
        assertEquals(false, ui.loading)
    }

    @Test
    fun `today travels as a date`() {
        assertEquals(LocalDate.ofEpochDay(TODAY.toLong()), camino(domainState()).hoy)
    }
}
