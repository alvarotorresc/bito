package com.alvarotc.bito.ui.sesion

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.PlanSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.sesion.MotorSesion

/** Los bloques de la tierra de la sesión (`.ses-b`) y el encuadre de cámara de cada uno (`ENC` del mock). */
enum class BloqueSesion(val id: String, val encuadre: Encuadre) {
    PREPARAR("prep", Encuadre.PREPARAR),
    RESPIRAR("entrar", Encuadre.RESPIRAR),
    FOCO("foco", Encuadre.FOCO),
    DESCANSO("descanso", Encuadre.DESCANSO),
    TERMINADA("fin", Encuadre.TERMINADA),
}

/** Qué bloque se pinta y con qué encuadre; sin bloque, la tierra está vacía y la cámara en Hoy. */
data class EstadoSesionUi(
    val bloque: BloqueSesion?,
    val hojaAbierta: Boolean = false,
    /** La cámara que pide un bloque retenido distinta de la suya (el «Hecha» de Foco lleva la de Terminada). */
    val encuadreRetenido: Encuadre? = null,
    /** El motor ya cerró la sesión y [bloque] es solo lo que se queda en pantalla un rato. */
    val cerrada: Boolean = false,
) {
    val encuadre: Encuadre get() = encuadreRetenido ?: bloque?.encuadre ?: Encuadre.HOY

    /**
     * Sin bloque por haberse cerrado la sesión, sigue el [retenido] (si hay uno) con su [encuadre] o el suyo. La
     * sesión ya está cerrada: Atrás no dice que siga en marcha.
     */
    fun conRetenido(
        retenido: BloqueSesion?,
        encuadre: Encuadre? = null,
    ): EstadoSesionUi =
        if (bloque == null && retenido != null) copy(bloque = retenido, encuadreRetenido = encuadre, cerrada = true) else this
}

/**
 * Un bloque que sigue montado después de que la sesión se cierre en el motor (el «Hecha» de Foco, 2200 ms; el de
 * Terminada, 1500 ms). El bloque llama a [retener] antes de cerrar la sesión y a [soltar] al acabar; la escena
 * también suelta al irse a Hoy.
 */
@Stable
class BloqueRetenido {
    var bloque: BloqueSesion? by mutableStateOf(null)
        private set

    /** La cámara con la que se queda el bloque retenido, si no es la suya. */
    var encuadre: Encuadre? by mutableStateOf(null)
        private set

    fun retener(
        b: BloqueSesion,
        encuadre: Encuadre? = null,
    ) {
        bloque = b
        this.encuadre = encuadre
    }

    fun soltar() {
        bloque = null
        encuadre = null
    }

    /** Retiene [b] [ms] milisegundos y lo suelta. */
    suspend fun conservar(
        b: BloqueSesion,
        ms: Long,
        encuadre: Encuadre? = null,
    ) {
        retener(b, encuadre)
        try {
            kotlinx.coroutines.delay(ms)
        } finally {
            soltar()
        }
    }
}

val LocalBloqueRetenido = compositionLocalOf { BloqueRetenido() }

/**
 * La cara de Habi en la escena de la sesión. [radiante] la pone feliz (Terminada y el «Hecha» de Foco); el Inicio la lee
 * al pintar el jardín y la apaga al volver a Hoy.
 */
@Stable
class CaraHabi {
    var radiante: Boolean by mutableStateOf(false)
}

/** La cara de Habi que el Inicio provee a los bloques; fuera del Inicio no se ve en ninguna escena. */
val LocalCaraHabi = compositionLocalOf { CaraHabi() }

/** Hace saltar a Habi en la escena de la sesión con esa [amplitud] (0,11 el saltito de Foco); fuera del Inicio no hace nada. */
val LocalSaltoHabi = compositionLocalOf<(Float) -> Unit> { {} }

/** Lo que el Inicio sabe de la sesión (motor, fondo, No molestar, objetivo, voz); null fuera del Inicio. */
val LocalSesionInicio = compositionLocalOf<SesionInicio?> { null }

/**
 * Del instante, solo el tramo en que va ([actual], null = Terminada): lo que decide qué bloque se pinta. Cada tic del
 * motor emite un instante nuevo; con esto el Inicio solo se entera cuando cambia de tramo y no con cada tic. El resto del
 * instante (la cuenta, la pausa) lo lee quien lo pinta.
 */
@Immutable
data class TramoVisto(val actual: Tramo?) {
    val bloque: BloqueSesion
        get() =
            when (actual) {
                is Tramo.Respirar -> BloqueSesion.RESPIRAR
                is Tramo.Foco -> BloqueSesion.FOCO
                is Tramo.Descanso -> BloqueSesion.DESCANSO
                null -> BloqueSesion.TERMINADA
            }

    companion object {
        fun de(instante: Instante?): TramoVisto? = instante?.let { TramoVisto(it.actual) }
    }
}

/**
 * De lo que dice el motor a lo que se ve. [preparando] es la vista: Preparar manda sobre una sesión viva mientras el
 * borrador siga ahí (se abre otra tarea con una en marcha, spec §4.1). Sin ella, manda el tramo de la sesión; y
 * sin sesión, el borrador.
 */
fun estadoSesionUi(
    instante: Instante?,
    borrador: PlanSesion?,
    preparando: Boolean,
    hojaAbierta: Boolean = false,
): EstadoSesionUi = estadoSesionDeTramo(TramoVisto.de(instante), borrador, preparando, hojaAbierta)

/** Lo mismo, desde el tramo visto: no depende de cada tic del motor. */
fun estadoSesionDeTramo(
    tramo: TramoVisto?,
    borrador: PlanSesion?,
    preparando: Boolean,
    hojaAbierta: Boolean = false,
): EstadoSesionUi {
    val bloque =
        when {
            preparando && borrador != null -> BloqueSesion.PREPARAR
            tramo != null -> tramo.bloque
            borrador != null -> BloqueSesion.PREPARAR
            else -> null
        }
    return EstadoSesionUi(bloque, hojaAbierta)
}

/**
 * Quien tiene una hoja abierta en la sesión lo apunta aquí con el modo de cerrarla; Atrás la cierra antes que nada.
 * Cada hoja de un bloque se registra con [abrir] al aparecer y [soltar] al irse.
 */
@Stable
class RegistroHoja {
    private var cierre: (() -> Unit)? by mutableStateOf(null)

    val abierta: Boolean get() = cierre != null

    fun abrir(cerrar: () -> Unit) {
        cierre = cerrar
    }

    fun soltar() {
        cierre = null
    }

    /** Cierra la hoja abierta; devuelve si había alguna. */
    fun cerrar(): Boolean {
        val c = cierre ?: return false
        c()
        return true
    }
}

/** El registro de hojas de la sesión que pone el Inicio; los bloques lo leen. */
val LocalRegistroHoja = compositionLocalOf { RegistroHoja() }

/** El motor de la sesión para los bloques de la tierra; null fuera del Inicio. */
val LocalMotorSesion = compositionLocalOf<MotorSesion?> { null }

/** La salida de la sesión hacia Hoy (cámara incluida) para los bloques. */
@Stable
class SalidaSesion(
    val volverAHoy: () -> Unit,
    /** Un aviso breve del Inicio (G9), con su texto ya resuelto. */
    val avisar: (String) -> Unit = {},
)

val LocalSalidaSesion = compositionLocalOf { SalidaSesion({}) }

/** El llenado de la respiración de Habi: el bloque que respira pone aquí su lectura y el Inicio se la pasa a la escena. */
val LocalRespiracionJardin = compositionLocalOf<MutableState<(() -> Float)?>?> { null }
