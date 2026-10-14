package com.alvarotc.bito.ui.detail

import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GrowthStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnclasPlantaTest {
    @Test
    fun `the thirty species and stage pairs have three points inside the piece`() {
        GardenSpecies.entries.forEach { especie ->
            GrowthStage.entries.forEach { etapa ->
                val p = AnclasPlanta.tabla["${especie.key}-${etapa.ordinal + 1}"]
                assertNotNull("${especie.key}-${etapa.ordinal + 1}", p)
                assertEquals(6, p!!.size)
                p.forEach { assertTrue(it in -0.05f..1.05f) }
            }
        }
    }
}
