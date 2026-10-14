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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.settings.SettingsViewModel
import com.alvarotc.bito.ui.settings.hojas.HojaDiaTermina
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Campo
import com.alvarotc.bito.ui.sistema.componentes.FilaValor
import com.alvarotc.bito.ui.sistema.componentes.HojaPersonalidad

private fun nombreDePersonalidad(p: Personality): Int =
    when (p) {
        Personality.NEUTRA -> R.string.personality_neutra
        Personality.CHEERLEADER -> R.string.personality_cheerleader
        Personality.SARGENTO -> R.string.personality_sargento
    }

private fun nombreDeIdioma(tag: String?): Int =
    when (tag) {
        "es" -> R.string.language_spanish
        "en" -> R.string.language_english
        else -> R.string.language_system
    }

/** La línea del estrato cerrado: «Neutra · Español». */
internal fun resumenGeneral(
    settings: Settings,
    personalidad: Personality,
    resources: Resources,
): String = "${resources.getString(nombreDePersonalidad(personalidad))} · ${resources.getString(nombreDeIdioma(settings.languageTag))}"

private enum class HojaGeneral { PERSONALIDAD, DIA_TERMINA }

/** Del sistema → Español → English → Del sistema. */
private fun siguienteIdioma(tag: String?): String? =
    when (tag) {
        null -> "es"
        "es" -> "en"
        else -> null
    }

/** El cuerpo del estrato «General» (spec §5.1.3). */
@Composable
fun ContenidoGeneral(
    settings: Settings,
    personalidad: Personality,
    alElegirPersonalidad: (Personality) -> Unit,
    archivados: Int,
    settingsViewModel: SettingsViewModel,
    alAbrirIntro: () -> Unit,
    alAbrirArchivados: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nombre by remember(settings.userName) { mutableStateOf(settings.userName) }
    var hoja by remember { mutableStateOf<HojaGeneral?>(null) }

    fun guardarNombre() {
        if (nombre.trim() != settings.userName) settingsViewModel.setUserName(nombre)
    }
    Column(modifier) {
        Campo(
            valor = nombre,
            alCambiar = { nombre = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Medidas.e8)
                    .onFocusChanged { if (!it.hasFocus) guardarNombre() },
            etiqueta = stringResource(R.string.settings_name_row),
            pista = stringResource(R.string.settings_name_placeholder),
            alHecho = ::guardarNombre,
        )
        FilaValor(
            etiqueta = stringResource(R.string.language_row),
            valor = stringResource(nombreDeIdioma(settings.languageTag)),
            alPulsar = { settingsViewModel.setLanguage(siguienteIdioma(settings.languageTag)) },
        )
        FilaValor(
            etiqueta = stringResource(R.string.ajustes_personalidad),
            subtitulo = stringResource(R.string.ajustes_personalidad_sub),
            valor = stringResource(nombreDePersonalidad(personalidad)),
            alPulsar = { hoja = HojaGeneral.PERSONALIDAD },
        )
        FilaValor(
            etiqueta = stringResource(R.string.cutoff_label),
            subtitulo = stringResource(R.string.cutoff_row_hint),
            valor = horaCorta(settings.dayCutoffMinutes),
            alPulsar = { hoja = HojaGeneral.DIA_TERMINA },
        )
        FilaValor(
            etiqueta = stringResource(R.string.settings_intro_row),
            valor = stringResource(R.string.ajustes_ver_de_nuevo),
            alPulsar = alAbrirIntro,
        )
        if (archivados > 0) {
            FilaValor(
                etiqueta = stringResource(R.string.ajustes_archivados, archivados),
                valor = stringResource(R.string.ajustes_ver),
                alPulsar = alAbrirArchivados,
            )
        }
    }
    HojaPersonalidad(
        abierta = hoja == HojaGeneral.PERSONALIDAD,
        actual = personalidad,
        alElegir = alElegirPersonalidad,
        alCerrar = { hoja = null },
    )
    HojaDiaTermina(
        abierta = hoja == HojaGeneral.DIA_TERMINA,
        minutos = settings.dayCutoffMinutes,
        alGuardar = settingsViewModel::setCutoff,
        alCerrar = { hoja = null },
    )
}
