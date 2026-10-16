package com.alvarotc.bito.ui.sesion

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.sesion.FormatoSesion
import com.alvarotc.bito.sesion.AvisoSesion
import com.alvarotc.bito.sesion.MotorSesion
import kotlinx.coroutines.launch

/** Lo que hace Atrás en cada estado (`atras()` del mock, spec §4.2). */
enum class AtrasSesion {
    /** Con una hoja abierta, la cierra. */
    CERRAR_HOJA,

    /** Preparar y Terminada: a Hoy. En Terminada la sesión sigue esperando su respuesta, sin aviso. */
    A_HOY,

    /** Respirar para entrar: se descarta el arranque y vuelve Preparar. */
    A_PREPARAR,

    /** Foco y Descanso: a Hoy sin cortar la sesión y con el aviso «Sigue en marcha». */
    SIGUE_EN_MARCHA,
}

/** Null cuando no hay nada que volver (Hoy). */
fun atrasDe(
    bloque: BloqueSesion?,
    hojaAbierta: Boolean,
    sesionCerrada: Boolean = false,
): AtrasSesion? =
    when {
        hojaAbierta -> AtrasSesion.CERRAR_HOJA
        bloque == null -> null
        // Lo que se queda en pantalla tras cerrarse la sesión (el «Hecha» de Foco): no sigue nada en marcha.
        sesionCerrada -> AtrasSesion.A_HOY
        bloque == BloqueSesion.PREPARAR || bloque == BloqueSesion.TERMINADA -> AtrasSesion.A_HOY
        bloque == BloqueSesion.RESPIRAR -> AtrasSesion.A_PREPARAR
        else -> AtrasSesion.SIGUE_EN_MARCHA
    }

/**
 * El Atrás de la sesión: el del sistema y el botón de la cabecera hacen lo mismo. [volverAHoy] lleva la cámara a Hoy
 * (con `ascenso` y `low`) y [avisar] enseña el aviso G9. Devuelve la acción del botón, o null en Hoy.
 */
@Composable
fun rememberAtrasSesion(
    estado: EstadoSesionUi,
    motor: MotorSesion,
    hojas: RegistroHoja,
    volverAHoy: () -> Unit,
    avisar: suspend (String) -> Unit,
): AccionAtras? {
    val scope = rememberCoroutineScope()
    val recursos = LocalContext.current.resources
    val hoy = rememberUpdatedState(volverAHoy)
    val avisa = rememberUpdatedState(avisar)
    val que = atrasDe(estado.bloque, estado.hojaAbierta || hojas.abierta, estado.cerrada)
    val pulsar: () -> Unit = {
        when (atrasDe(estado.bloque, estado.hojaAbierta || hojas.abierta, estado.cerrada)) {
            AtrasSesion.CERRAR_HOJA -> hojas.cerrar()
            AtrasSesion.A_HOY -> hoy.value()
            AtrasSesion.A_PREPARAR -> scope.launch { motor.volverAPreparar() }
            AtrasSesion.SIGUE_EN_MARCHA -> {
                val resta = FormatoSesion.cuenta(motor.instante.value?.restaMillis ?: 0L)
                hoy.value()
                scope.launch { avisa.value(AvisoSesion.SigueEnMarcha(resta).texto(recursos)) }
            }
            null -> Unit
        }
    }
    BackHandler(enabled = que != null, onBack = pulsar)
    if (estado.bloque == null) return null
    val etiqueta = stringResource(if (estado.bloque == BloqueSesion.RESPIRAR) R.string.ses_saltar_y_volver else R.string.ses_volver)
    return AccionAtras(etiqueta, pulsar)
}
