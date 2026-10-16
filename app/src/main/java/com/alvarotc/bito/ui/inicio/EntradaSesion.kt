package com.alvarotc.bito.ui.inicio

import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.sesion.MotorSesion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lo que el Inicio ofrece para abrir la sesión: el motor, cómo poner el Inicio en modo sesión ([entrar] con
 * `preparando` si lo que toca ver es Preparar y no el estado de la sesión en curso), cómo ponerlo en modo respiración
 * suelta ([entrarSuelta]) y cómo preguntar qué hacer con la sesión viva antes de respirar ([ocupado], la hoja
 * «Ya tienes algo en marcha»).
 */
class AbrirSesion(
    val motor: MotorSesion,
    val entrar: (preparando: Boolean) -> Unit,
    val entrarSuelta: () -> Unit,
    val ocupado: () -> Unit = {},
)

/** Una petición de abrir la sesión que llega de otra pantalla; [destino] null = la sesión en curso o lo gordo. */
data class PeticionSesion(val destino: Destino?, val numero: Long)

/** «Ahora», «Sesión ahora» y las demás entradas (spec §4.1): todas acaban aquí. */
object EntradaSesion {
    /**
     * Prepara el plan del [destino] y pone el Inicio en modo sesión. Sin destino y con una sesión viva, lleva a su
     * estado. Respirar abre la respiración suelta (elegir) y, con una sesión viva, pregunta antes (H11).
     */
    suspend fun abrir(
        destino: Destino?,
        inicio: AbrirSesion,
    ) {
        val motor = inicio.motor
        when {
            destino == Destino.Respirar -> {
                // Sin borrador: si se deja la viva, no debe arrancar ninguna Preparar que se quedó a medias.
                motor.preparar(Destino.Respirar)
                if (motor.registro.value != null) inicio.ocupado() else inicio.entrarSuelta()
            }
            destino == null && motor.registro.value != null -> inicio.entrar(false)
            else -> {
                motor.preparar(destino)
                if (motor.borrador.value != null) inicio.entrar(true)
            }
        }
    }

    private var contador = 0L
    private val _pendiente = MutableStateFlow<PeticionSesion?>(null)

    /** Lo que otra pantalla pidió y el Inicio aún no ha abierto. Como `NavRequests`: una sola vez. */
    val pendiente: StateFlow<PeticionSesion?> = _pendiente.asStateFlow()

    /** Pide al Inicio que abra la sesión (la tarea de Tareas, la notificación). Hay que navegar a Hoy aparte. */
    fun pedir(destino: Destino?) {
        _pendiente.value = PeticionSesion(destino, ++contador)
    }

    fun consumir() {
        _pendiente.value = null
    }
}
