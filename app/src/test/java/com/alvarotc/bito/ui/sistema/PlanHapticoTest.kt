package com.alvarotc.bito.ui.sistema

import android.os.VibrationEffect.Composition
import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanHapticoTest {
    private val todas: (Int) -> Boolean = { true }

    private fun plan(
        h: Haptica,
        sdk: Int = 34,
        fuerza: Int = 10,
        vibracion: Boolean = true,
        vista: Boolean = true,
        prims: (Int) -> Boolean = todas,
    ) = PlanHaptico.de(h, sdk, prims, fuerza, vibracion, vista)

    @Test
    fun `vibration off or strength zero is silent`() {
        assertEquals(PlanTacto.Nada, plan(Haptica.CLICK, vibracion = false))
        assertEquals(PlanTacto.Nada, plan(Haptica.CLICK, fuerza = 0))
    }

    @Test
    fun `tick is a 0,3 primitive scaled by strength`() {
        assertEquals(PlanTacto.Primitivas(listOf(Paso(Composition.PRIMITIVE_TICK, 0.15f))), plan(Haptica.TICK, fuerza = 5))
    }

    @Test
    fun `ticks are three ticks 110 ms apart`() {
        val p = plan(Haptica.TICKS) as PlanTacto.Primitivas
        assertEquals(listOf(0, 110, 110), p.pasos.map { it.retrasoMs })
    }

    @Test
    fun `low and thud fall back on API 30`() {
        assertEquals(PlanTacto.Predefinido(android.os.VibrationEffect.EFFECT_TICK), plan(Haptica.LOW, sdk = 30))
        assertEquals(PlanTacto.Primitivas(listOf(Paso(Composition.PRIMITIVE_LOW_TICK, 0.4f))), plan(Haptica.LOW, sdk = 31))
    }

    @Test
    fun `seg uses the view constant on API 34 only when the system touch feedback is on`() {
        assertEquals(PlanTacto.Constante(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK), plan(Haptica.SEG, sdk = 34))
        assertEquals(PlanTacto.Primitivas(listOf(Paso(Composition.PRIMITIVE_TICK, 0.2f))), plan(Haptica.SEG, sdk = 34, vista = false))
        assertEquals(PlanTacto.Primitivas(listOf(Paso(Composition.PRIMITIVE_TICK, 0.2f))), plan(Haptica.SEG, sdk = 33))
    }

    @Test
    fun `before Android 10 everything is a short pulse scaled by strength`() {
        assertEquals(PlanTacto.Pulso(12, 127), plan(Haptica.TICK, sdk = 26, fuerza = 5))
        assertEquals(PlanTacto.Pulso(35, 255), plan(Haptica.REJECT, sdk = 28))
    }

    @Test
    fun `without primitive support it uses predefined effects`() {
        assertEquals(PlanTacto.Predefinido(android.os.VibrationEffect.EFFECT_CLICK), plan(Haptica.CLICK, sdk = 33, prims = { false }))
    }
}
