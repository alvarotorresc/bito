package com.alvarotc.bito.ui.breathing

/**
 * La musica de fondo del ejercicio (D7), con la misma separacion que FocusPresence: el ViewModel
 * habla con esta interfaz y la plataforma (MediaPlayer, foco de audio) queda en la
 * implementacion. [start] arranca con fundido; [stop] funde a cero y libera. Las dos son
 * idempotentes: parar sin haber empezado no hace nada.
 */
interface BreathingMusic {
    fun start()

    fun stop()
}

/**
 * Provisional: la ruta necesita un reproductor para construir el ViewModel antes de que la pista
 * entre en el APK. No suena nada. Se borra en cuanto existe AndroidBreathingMusic.
 */
object SilentBreathingMusic : BreathingMusic {
    override fun start() = Unit

    override fun stop() = Unit
}
