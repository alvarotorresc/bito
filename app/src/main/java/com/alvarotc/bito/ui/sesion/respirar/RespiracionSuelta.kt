package com.alvarotc.bito.ui.sesion.respirar

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.model.BreathingMode
import java.time.ZoneId

/** Dónde está la respiración suelta. */
enum class EtapaSuelta { ELEGIR, EN_MARCHA, COMO_ESTAS, FIN }

/** Lo que de verdad se respiró: se guarda en `breathing_sessions`. */
data class CierreSuelta(
    val modo: BreathingMode,
    val inicioPared: Long,
    val segundos: Int,
    val completa: Boolean,
)

/** Qué pasa al acabar el ejercicio: nada que guardar (menos de 10 s) o una sesión que guardar. */
sealed interface Desenlace {
    data object Descartada : Desenlace

    data class Guardar(val cierre: CierreSuelta) : Desenlace
}

/**
 * Los relojes y las costuras de la respiración suelta, para que los tests los fijen. Por defecto, los del sistema.
 * [animada] a `false` (tests): Habi toma el llenado del último tick de 100 ms en vez de pedir un fotograma por vuelta,
 * que no deja asentarse a `waitForIdle`.
 */
data class EntornoSuelta(
    val elapsed: () -> Long = SystemClock::elapsedRealtime,
    val pared: () -> Long = System::currentTimeMillis,
    val semilla: () -> Long = System::nanoTime,
    val zona: () -> ZoneId = ZoneId::systemDefault,
    val animada: Boolean = true,
)

/**
 * Lo que la respiración suelta le pide a Habi, que vive en el jardín del Inicio: cada subida de [saltos] es un salto de
 * 0,24 (`saltoHabi` de `GardenScene`), [alegre] es la cara de alegría (`delighted`) y cada subida de [caras] es
 * «cara normal».
 */
@Stable
class GestosHabi {
    var saltos by mutableIntStateOf(0)
        internal set
    var alegre by mutableStateOf(false)
        internal set
    var caras by mutableIntStateOf(0)
        internal set
}

/**
 * Las reglas de la respiración suelta, sin pantalla: el modo, la música, el ancla de `elapsedRealtime` y qué se guarda
 * al acabar. El tiempo sale siempre del ancla (nunca de un contador), así sigue igual con la pantalla apagada.
 */
@Stable
class RespiracionSuelta(
    private val elapsed: () -> Long,
    private val pared: () -> Long,
) {
    var etapa by mutableStateOf(EtapaSuelta.ELEGIR)
        internal set
    var modo by mutableStateOf(BreathingMode.CALM)
        internal set
    var musica by mutableStateOf(false)
        internal set

    var ancla = 0L
        private set
    var inicioPared = 0L
        private set

    val ciclos: Int get() = BreathingRhythm.cycles(modo)

    /** Dónde va el reloj ahora. */
    fun punto(ahoraElapsed: Long = elapsed()): BreathingRhythm.Point = BreathingRhythm.at(modo, ahoraElapsed - ancla, ciclos)

    /** El `elapsedRealtime` en que acaba el ejercicio. */
    val finElapsed: Long get() = ancla + BreathingRhythm.totalMillis(modo, ciclos)

    fun empezar() {
        ancla = elapsed()
        inicioPared = pared()
        etapa = EtapaSuelta.EN_MARCHA
    }

    /** Cierra el ejercicio (último ciclo o «Parar»): menos de [MIN_GUARDADO_SEG] s no se guarda. */
    fun cerrar(): Desenlace {
        val p = punto()
        val millis = if (p.finished) BreathingRhythm.totalMillis(modo, ciclos) else elapsed() - ancla
        val segundos = (millis / 1_000L).toInt()
        if (!p.finished && segundos < MIN_GUARDADO_SEG) {
            etapa = EtapaSuelta.ELEGIR
            return Desenlace.Descartada
        }
        etapa = EtapaSuelta.COMO_ESTAS
        return Desenlace.Guardar(CierreSuelta(modo, inicioPared, segundos, p.finished))
    }

    companion object {
        /** Menos de esto no fue una sesión, fue un toque. */
        const val MIN_GUARDADO_SEG = 10
    }
}
