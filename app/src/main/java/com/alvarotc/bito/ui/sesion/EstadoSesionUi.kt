package com.alvarotc.bito.ui.sesion

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
) {
    val encuadre: Encuadre get() = bloque?.encuadre ?: Encuadre.HOY
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
): EstadoSesionUi {
    val bloque =
        when {
            preparando && borrador != null -> BloqueSesion.PREPARAR
            instante != null ->
                when (instante.actual) {
                    is Tramo.Respirar -> BloqueSesion.RESPIRAR
                    is Tramo.Foco -> BloqueSesion.FOCO
                    is Tramo.Descanso -> BloqueSesion.DESCANSO
                    null -> BloqueSesion.TERMINADA
                }
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
class SalidaSesion(val volverAHoy: () -> Unit)

val LocalSalidaSesion = compositionLocalOf { SalidaSesion {} }

/** El llenado de la respiración de Habi: el bloque que respira pone aquí su lectura y el Inicio se la pasa a la escena. */
val LocalRespiracionJardin = compositionLocalOf<MutableState<(() -> Float)?>?> { null }
