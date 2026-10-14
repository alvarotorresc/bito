package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GrowthStage
import org.junit.Assert.assertEquals
import org.junit.Test

class PlantaDeHabitoTest {
    private val cama = RealHabits.makeBed

    private fun conDias(n: Int) = domainState(habits = listOf(cama), entries = (1..n).map { entryOn(cama, TODAY - it) })

    @Test
    fun `the species is stable for the same id and does not depend on other habits`() {
        val sola = plantaDeHabito(cama, domainState(habits = listOf(cama)), TODAY).especie
        val conOtros = plantaDeHabito(cama, domainState(habits = listOf(RealHabits.water, cama, RealHabits.floss)), TODAY).especie
        assertEquals(sola, conOtros)
        assertEquals(GardenSpecies.entries[Math.floorMod(cama.id.hashCode(), 6)], sola)
        assertEquals(especieDe(cama.id), especieDe(cama.id))
    }

    @Test
    fun `stage thresholds follow the garden`() {
        val esperado =
            mapOf(
                0 to GrowthStage.SEED, 2 to GrowthStage.SEED, 3 to GrowthStage.SPROUT, 7 to GrowthStage.SPROUT,
                8 to GrowthStage.PLANT, 14 to GrowthStage.PLANT, 15 to GrowthStage.FLOWERS, 24 to GrowthStage.FLOWERS,
                25 to GrowthStage.TREE,
            )
        esperado.forEach { (n, etapa) ->
            val planta = plantaDeHabito(cama, conDias(n), TODAY)
            assertEquals("$n cumplidos", n, planta.cumplidos)
            assertEquals("$n cumplidos", etapa, planta.etapa)
        }
    }

    @Test
    fun `a weekly habit counts weeks, not days`() {
        val fuerza = RealHabits.strengthTraining // 3 por semana
        val state = domainState(habits = listOf(fuerza), entries = (0..2).map { entryOn(fuerza, LAST_MONDAY + it) })
        assertEquals(1, periodosCumplidos(fuerza, state, TODAY))
    }

    @Test
    fun `correcting a past day to not done can lower the stage`() {
        val tres = conDias(3)
        assertEquals(GrowthStage.SPROUT, plantaDeHabito(cama, tres, TODAY).etapa)
        val corregido = tres.copy(entries = tres.entries.drop(1))
        assertEquals(GrowthStage.SEED, plantaDeHabito(cama, corregido, TODAY).etapa)
    }
}
