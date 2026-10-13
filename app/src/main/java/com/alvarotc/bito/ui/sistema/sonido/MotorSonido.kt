package com.alvarotc.bito.ui.sistema.sonido

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** Un sonido continuo (fondo de la sesión): entra con rampa de 400 ms y sale con rampa de 600 ms (spec §4.4.1). */
interface Bucle {
    val sonando: Boolean

    fun empezar()

    fun parar()
}

/**
 * El motor de sonido, uno por proceso (spec §4.4.1). [play] no bloquea nunca: un sonido sin cargar se
 * descarta. Callan, en este orden: «Sonido» apagado, el móvil en silencio o vibración, «Sonidos de Habi»
 * (canal HABI), «Sonido al registrar» ([Snd.registro]) y el volumen del canal a 0.
 */
interface MotorSonido {
    /** [intensidad] de 0 a 1 multiplica el volumen del canal (la moneda al 60 % es `play(Snd.MONEDA, .6f)`). */
    fun play(
        snd: Snd,
        intensidad: Float = 1f,
    )

    fun bucle(snd: Snd): Bucle

    /** Vista previa de la mesa: el sonido de [canal] al [nivel] (0–10) que se arrastra, sin esperar a que se guarde. */
    fun probar(
        canal: Canal,
        nivel: Int,
    )

    /** Ganancia actual de [canal] (0–1), ya con sus silencios. La usa la música de respirar. */
    fun ganancia(canal: Canal): Float

    /** La app pasa a segundo plano: se paran los bucles. */
    fun segundoPlano()
}

object BucleMudo : Bucle {
    override val sonando: Boolean = false

    override fun empezar() = Unit

    override fun parar() = Unit
}

/** Lo que suena hasta que la Tarea 20 pone el motor real, y en previsualizaciones y tests. */
object MotorSonidoMudo : MotorSonido {
    override fun play(
        snd: Snd,
        intensidad: Float,
    ) = Unit

    override fun bucle(snd: Snd): Bucle = BucleMudo

    override fun probar(
        canal: Canal,
        nivel: Int,
    ) = Unit

    override fun ganancia(canal: Canal): Float = 1f

    override fun segundoPlano() = Unit
}

val LocalSonido: ProvidableCompositionLocal<MotorSonido> = staticCompositionLocalOf { MotorSonidoMudo }
