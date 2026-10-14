package com.alvarotc.bito.ui.settings.estratos

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.ui.settings.SettingsViewModel
import com.alvarotc.bito.ui.settings.hojas.HojaFondo
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.CanalMesa
import com.alvarotc.bito.ui.sistema.componentes.FilaInterruptor
import com.alvarotc.bito.ui.sistema.componentes.FilaValor
import com.alvarotc.bito.ui.sistema.componentes.MesaCanales

/** La línea del estrato cerrado: «Sonido encendido · vibración 7» o «Sonido apagado». */
internal fun resumenSonido(
    settings: Settings,
    resources: Resources,
): String =
    if (settings.soundEnabled) {
        resources.getString(R.string.ajustes_resumen_sonido_on, settings.vibStrength)
    } else {
        resources.getString(R.string.ajustes_resumen_sonido_off)
    }

private fun nombreDeFondo(fondo: SessionBackground): Int =
    when (fondo) {
        SessionBackground.RAIN -> R.string.ajustes_fondo_lluvia
        SessionBackground.BROWN -> R.string.ajustes_fondo_marron
        SessionBackground.CAFE -> R.string.ajustes_fondo_cafe
        SessionBackground.NONE -> R.string.ajustes_fondo_ninguno
    }

/** El cuerpo del estrato «Sonido y tacto» (spec §5.1.3). */
@Composable
fun ContenidoSonido(
    settings: Settings,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    var fondoAbierto by remember { mutableStateOf(false) }
    val niveles =
        mapOf(
            CanalMesa.HABI to settings.volHabi,
            CanalMesa.EFECTOS to settings.volEffects,
            CanalMesa.AMBIENTE to settings.volAmbient,
            CanalMesa.VIBRACION to settings.vibStrength,
        )
    val apagados =
        buildSet {
            if (!settings.habiSoundsEnabled) add(CanalMesa.HABI)
            if (!settings.logHapticEnabled) add(CanalMesa.VIBRACION)
        }
    Column(modifier) {
        FilaInterruptor(
            etiqueta = stringResource(R.string.ajustes_sonido),
            subtitulo = stringResource(R.string.ajustes_sonido_sub),
            activo = settings.soundEnabled,
            alCambiar = settingsViewModel::setSoundEnabled,
        )
        Subtitulo(stringResource(R.string.ajustes_volumen_canal))
        MesaCanales(
            niveles = niveles,
            alCambiar = { canal, nivel ->
                when (canal) {
                    CanalMesa.HABI -> settingsViewModel.setVolHabi(nivel)
                    CanalMesa.EFECTOS -> settingsViewModel.setVolEffects(nivel)
                    CanalMesa.AMBIENTE -> settingsViewModel.setVolAmbient(nivel)
                    CanalMesa.VIBRACION -> settingsViewModel.setVibStrength(nivel)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = Medidas.e12),
            apagados = apagados,
            todoApagado = !settings.soundEnabled,
        )
        FilaValor(
            etiqueta = stringResource(R.string.ajustes_fondo),
            subtitulo = stringResource(R.string.ajustes_fondo_sub),
            valor = stringResource(nombreDeFondo(settings.sessionBackground)),
            alPulsar = { fondoAbierto = true },
        )
        FilaInterruptor(
            etiqueta = stringResource(R.string.settings_habi_sounds),
            subtitulo = stringResource(R.string.settings_habi_sounds_hint),
            activo = settings.habiSoundsEnabled,
            alCambiar = settingsViewModel::setHabiSounds,
        )
        FilaInterruptor(
            etiqueta = stringResource(R.string.ajustes_sonido_registro),
            subtitulo = stringResource(R.string.ajustes_sonido_registro_sub),
            activo = settings.logSoundEnabled,
            alCambiar = settingsViewModel::setLogSound,
        )
        FilaInterruptor(
            etiqueta = stringResource(R.string.settings_log_haptic),
            subtitulo = stringResource(R.string.ajustes_vibracion_sub),
            activo = settings.logHapticEnabled,
            alCambiar = settingsViewModel::setLogHaptic,
        )
        FilaInterruptor(
            etiqueta = stringResource(R.string.ajustes_reducir),
            subtitulo = stringResource(R.string.ajustes_reducir_sub),
            activo = settings.reduceMotion,
            alCambiar = settingsViewModel::setReduceMotion,
        )
    }
    HojaFondo(
        abierta = fondoAbierto,
        elegido = settings.sessionBackground,
        alElegir = settingsViewModel::setSessionBackground,
        alCerrar = { fondoAbierto = false },
    )
}
