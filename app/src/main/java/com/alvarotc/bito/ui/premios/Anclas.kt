package com.alvarotc.bito.ui.premios

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Claves de posición en el Inicio (pieza 3, §6.2): de dónde salen y adónde llegan gotas, monedas y granos. */
object ClavesAncla {
    const val SALDO = "saldo"
    const val PARCELA = "parcela"

    /** El cuerpo de Habi en la escena: lo registra `GardenScene` (Tarea 14) para el globito de «volver de crear». */
    const val HABI = "habi"

    /** El círculo de un hábito: de ahí salen la gota y su moneda. */
    fun fila(habitId: String): String = "fila:$habitId"

    /** El círculo de una tarea: de ahí caen los granos (G6) y salen sus monedas. */
    fun tarea(taskId: String): String = "tarea:$taskId"
}

/** Lo que sale de una fila hacia la capa de partículas (pieza 3, §6.2). */
sealed interface EventoGota {
    /** G1: una gota del círculo [origen] a la parcela que crece. */
    data class Sale(val origen: String) : EventoGota

    /** G3: la gota en vuelo de [origen] se apaga en 120 ms. */
    data class Apaga(val origen: String) : EventoGota

    /** G6: 14 granos caen del círculo de una tarea ([origen] = `ClavesAncla.tarea(id)`). Solo dibujo: el sonido es de la fila. */
    data class Granos(val origen: String) : EventoGota
}

/** Dónde está cada cosa del Inicio, en coordenadas de la raíz, y las gotas y granos que salen (pieza 3, §6.2). */
@Stable
class RegistroAnclas {
    private val mapa = mutableStateMapOf<String, Rect>()
    val posiciones: Map<String, Rect> get() = mapa
    private val _gotas = MutableSharedFlow<EventoGota>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val gotas: SharedFlow<EventoGota> = _gotas.asSharedFlow()

    fun poner(
        clave: String,
        rect: Rect,
    ) {
        mapa[clave] = rect
    }

    fun quitar(clave: String) {
        mapa.remove(clave)
    }

    fun gota(origen: String) {
        _gotas.tryEmit(EventoGota.Sale(origen))
    }

    fun apagarGota(origen: String) {
        _gotas.tryEmit(EventoGota.Apaga(origen))
    }

    fun granos(origen: String) {
        _gotas.tryEmit(EventoGota.Granos(origen))
    }
}

val LocalAnclas = staticCompositionLocalOf<RegistroAnclas?> { null }

/** Registra el rectángulo de este nodo con [clave]. Sin [LocalAnclas] no hace nada (tests de fila sueltos). */
fun Modifier.anclaParticula(clave: String): Modifier =
    composed {
        val registro = LocalAnclas.current ?: return@composed this
        DisposableEffect(registro, clave) { onDispose { registro.quitar(clave) } }
        onGloballyPositioned { registro.poner(clave, it.boundsInRoot()) }
    }
