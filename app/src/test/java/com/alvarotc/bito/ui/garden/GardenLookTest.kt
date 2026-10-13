package com.alvarotc.bito.ui.garden

import com.alvarotc.bito.domain.model.GardenLight
import com.alvarotc.bito.domain.model.GardenSeason
import com.alvarotc.bito.domain.model.LightPhase
import com.alvarotc.bito.ui.habi.ColorTint
import org.junit.Assert.assertEquals
import org.junit.Test

class GardenLookTest {
    private val delta = 1e-4f

    @Test
    fun `a fresh summer garden is not tinted at all`() {
        assertEquals(emptyList<ColorTint>(), GardenLook.plantTints(0, GardenSeason.SUMMER))
        assertEquals(emptyList<ColorTint>(), GardenLook.grassTints(0, GardenSeason.SUMMER))
        assertEquals(1f, GardenLook.plantScaleY(0), delta)
        assertEquals(0f, GardenLook.plantLeanDegrees(0), delta)
    }

    @Test
    fun `full wilt reproduces the prototype filters`() {
        val plant = GardenLook.plantTints(3, GardenSeason.SUMMER).single()
        assertEquals(0.4f, plant.saturation, delta)
        assertEquals(0.45f, plant.sepia, delta)
        assertEquals(0.9f, plant.brightness, 1e-3f)
        val grass = GardenLook.grassTints(3, GardenSeason.SUMMER).single()
        assertEquals(0.6f, grass.saturation, 1e-3f)
        assertEquals(0.25f, grass.sepia, 1e-3f)
        assertEquals(0.88f, GardenLook.plantScaleY(3), delta)
        assertEquals(-5.1f, GardenLook.plantLeanDegrees(3), delta)
    }

    @Test
    fun `a wilted plant leans with its top to the right, like skewX of the prototype`() {
        assertEquals(0f, GardenLook.plantShear(0), delta)
        // La copa está por encima del pie (dy < 0 en pantalla): x' = x + shear * dy debe ir a la derecha.
        val topShift = GardenLook.plantShear(3) * -100f
        assertEquals(100f * kotlin.math.tan(Math.toRadians(5.1).toFloat()), topShift, 1e-3f)
    }

    @Test
    fun `seasons tint after the wilt`() {
        val tints = GardenLook.plantTints(1, GardenSeason.AUTUMN)
        assertEquals(2, tints.size)
        assertEquals(-14f, tints[1].hueDegrees, delta)
    }

    @Test
    fun `nightness follows the night phase and its fades`() {
        fun light(
            phase: LightPhase,
            from: LightPhase,
            to: LightPhase,
            t: Float,
        ) = GardenLight(phase, from, to, t)
        assertEquals(1f, GardenLook.nightness(light(LightPhase.NOCHE, LightPhase.NOCHE, LightPhase.NOCHE, 0f)), delta)
        assertEquals(0f, GardenLook.nightness(light(LightPhase.DIA, LightPhase.DIA, LightPhase.DIA, 0f)), delta)
        assertEquals(0.5f, GardenLook.nightness(light(LightPhase.NOCHE, LightPhase.TARDE, LightPhase.NOCHE, 0.5f)), delta)
        assertEquals(0.75f, GardenLook.nightness(light(LightPhase.NOCHE, LightPhase.NOCHE, LightPhase.MANANA, 0.25f)), delta)
    }

    @Test
    fun `the light gradient blends between phases and the day stays white`() {
        assertEquals(0xFFFFFFFFL to 0xFFFFFFFFL, GardenLook.lightGradient(GardenLight(LightPhase.DIA, LightPhase.DIA, LightPhase.DIA, 0f)))
        val half = GardenLook.lightGradient(GardenLight(LightPhase.TARDE, LightPhase.DIA, LightPhase.TARDE, 0.5f))
        // Entre blanco (FF) y el FFCF9A de la tarde arriba: el verde baja a la mitad del camino.
        assertEquals(0xE7, ((half.first shr 8) and 0xFF).toInt())
    }
}
