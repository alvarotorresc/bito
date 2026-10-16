package com.alvarotc.bito.ui.inicio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.alvarotc.bito.ui.sesion.EstadoCamaraSesion
import com.alvarotc.bito.ui.sesion.SesionInicio
import com.alvarotc.bito.ui.sesion.respirar.GestosHabi
import com.alvarotc.bito.ui.sesion.respirar.RespirarSuelta

/**
 * La respiración suelta dentro del Inicio (spec §4.2): pinta su propia tierra y su cabecera, mueve la [camara] y
 * entrega a [respiracion] el llenado de Habi y a [gestos] sus saltos y su alegría. [alVolverAHoy] se llama al lanzar la
 * vuelta de la cámara: el Inicio deja la pantalla montada hasta que la cámara llega a Hoy. Sin sesiones de respirar ni
 * memoria en [sesion] (las pruebas de Hoy) no se pinta nada.
 */
@Composable
internal fun SueltaEnInicio(
    sesion: SesionInicio,
    camara: EstadoCamaraSesion,
    respiracion: MutableState<(() -> Float)?>,
    gestos: GestosHabi,
    alVolverAHoy: () -> Unit,
) {
    val breathing = sesion.breathing ?: return
    val memoria = sesion.memoria ?: return
    RespirarSuelta(
        camara = camara,
        aire = sesion.aire,
        fuera = sesion.fuera,
        breathing = breathing,
        memoria = memoria,
        settings = sesion.ajustes,
        alVolverAHoy = alVolverAHoy,
        respiracion = respiracion,
        gestos = gestos,
        estado = sesion.estadoDominio,
        catalogo = sesion.catalogo,
    )
}
