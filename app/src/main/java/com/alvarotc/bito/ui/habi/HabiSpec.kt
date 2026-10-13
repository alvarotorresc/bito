package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality

/**
 * Lo que es Habi ahora: ánimo × personalidad eligen la cara (§5.2) y [equipped] dice qué lleva puesto.
 *
 * [bodyToneOverride] y [closedEyes] son solo para escenas (el sofá del onboarding 7b); en la app
 * van siempre a null y false. [bodyToneOverride] sustituye el tinte del color de cuerpo.
 * [sleepCap] pone el gorro de dormir encima de lo que lleve en la cabeza mientras está a true
 * (spec pieza 2, §7.2); con disfraz no se pinta.
 */
data class HabiSpec(
    val mood: Mood,
    val personality: Personality,
    val equipped: EquippedSet,
    val bodyToneOverride: ColorTint? = null,
    val closedEyes: Boolean = false,
    val sleepCap: Boolean = false,
)
