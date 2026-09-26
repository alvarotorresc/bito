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
