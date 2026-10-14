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
class RegistroAnclas(
    /** Reloj en milisegundos para la memoria de las anclas retiradas (se cambia en los tests). */
    private val ahora: () -> Long = System::currentTimeMillis,
) {
    private val mapa = mutableStateMapOf<String, Rect>()
    val posiciones: Map<String, Rect> get() = mapa

    /** La última posición de las anclas que se fueron hace poco: [clave] -> (rectángulo, instante en que se retiró). */
    private val retiradas = mutableMapOf<String, Pair<Rect, Long>>()
    private val _gotas = MutableSharedFlow<EventoGota>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val gotas: SharedFlow<EventoGota> = _gotas.asSharedFlow()

    fun poner(
        clave: String,
        rect: Rect,
    ) {
        mapa[clave] = rect
        retiradas.remove(clave)
    }

    fun quitar(clave: String) {
        val t = ahora()
        retiradas.entries.removeAll { t - it.value.second > MEMORIA_MS } // no crece sin límite
        mapa.remove(clave)?.let { retiradas[clave] = it to t }
    }

    /** Cuántas anclas retiradas se recuerdan ahora (para los tests). */
    internal val retiradasRecordadas: Int get() = retiradas.size

    private val emisorLlegadas = MutableSharedFlow<Unit>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Cada moneda que llega al saldo: el saldo hace su tic en ese momento (G4), no cuando se escribe el libro. */
    val llegadasDeMoneda: SharedFlow<Unit> = emisorLlegadas.asSharedFlow()

    fun monedaLlego() {
        emisorLlegadas.tryEmit(Unit)
    }

    /**
     * Dónde está [clave] ahora o, si se retiró hace menos de [MEMORIA_MS], dónde estuvo por última vez. La fila de una
     * tarea terminada sale de la composición antes de que llegue su entrada del libro, y de ahí salen sus monedas.
     */
    fun posicionO(clave: String): Rect? {
        mapa[clave]?.let { return it }
        val (rect, desde) = retiradas[clave] ?: return null
        if (ahora() - desde <= MEMORIA_MS) return rect
        retiradas.remove(clave)
        return null
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

private const val MEMORIA_MS = 2_000L

val LocalAnclas = staticCompositionLocalOf<RegistroAnclas?> { null }

/** Registra el rectángulo de este nodo con [clave]. Sin [LocalAnclas] no hace nada (tests de fila sueltos). */
fun Modifier.anclaParticula(clave: String): Modifier =
    composed {
        val registro = LocalAnclas.current ?: return@composed this
        DisposableEffect(registro, clave) { onDispose { registro.quitar(clave) } }
        onGloballyPositioned { registro.poner(clave, it.boundsInRoot()) }
    }
