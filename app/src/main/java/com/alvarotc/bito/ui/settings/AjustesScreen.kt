@file:OptIn(ExperimentalFoundationApi::class)

package com.alvarotc.bito.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.habi.HabiViewModel
import com.alvarotc.bito.ui.settings.estratos.ContenidoAcerca
import com.alvarotc.bito.ui.settings.estratos.ContenidoAvisos
import com.alvarotc.bito.ui.settings.estratos.ContenidoCopias
import com.alvarotc.bito.ui.settings.estratos.ContenidoGeneral
import com.alvarotc.bito.ui.settings.estratos.ContenidoSonido
import com.alvarotc.bito.ui.settings.estratos.resumenAcerca
import com.alvarotc.bito.ui.settings.estratos.resumenAvisos
import com.alvarotc.bito.ui.settings.estratos.resumenCopias
import com.alvarotc.bito.ui.settings.estratos.resumenGeneral
import com.alvarotc.bito.ui.settings.estratos.resumenSonido
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.componentes.EstratoPlegable
import kotlinx.coroutines.delay

private const val GENERAL = 0
private const val AVISOS = 1
private const val SONIDO = 2
private const val COPIAS = 3
private const val ACERCA = 4

/** Lo que tarda el estrato en abrirse antes de subirlo a la vista. */
private const val ESPERA_ABRIR_MS = 260L

/**
 * Ajustes de la Bito 2 (spec §5.1): la maqueta del jardín arriba y cinco estratos de tierra debajo, uno abierto
 * cada vez. [habiViewModel] es el de la actividad: da el jardín de la maqueta y la personalidad de Habi.
 * [onBack] se conserva porque Ajustes es una pestaña y la llama el `NavHost`; aquí no hay flecha.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AjustesScreen(
    backupViewModel: BackupViewModel,
    settingsViewModel: SettingsViewModel,
    habiViewModel: HabiViewModel,
    onBack: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenIntro: () -> Unit = {},
) {
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()
    val archivados by settingsViewModel.archivedHabits.collectAsStateWithLifecycle()
    val habi by habiViewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val colores = BitoTema.colores
    val scroll = rememberScrollState()
    var abierto by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colores.roca)
            .verticalScroll(scroll),
    ) {
        Box(Modifier.background(colores.estratos.general.fondo)) {
            MaquetaJardin(garden = habi.garden, spec = habi.spec, scroll = scroll, modifier = Modifier.statusBarsPadding())
        }
        settings?.let { actual ->
            EstratoAjustes(GENERAL, R.string.ajustes_estrato_general, resumenGeneral(actual, habi.spec.personality, resources), abierto, {
                abierto = it
            }) {
                ContenidoGeneral(
                    settings = actual,
                    personalidad = habi.spec.personality,
                    alElegirPersonalidad = habiViewModel::setPersonality,
                    archivados = archivados.size,
                    settingsViewModel = settingsViewModel,
                    alAbrirIntro = onOpenIntro,
                    alAbrirArchivados = onOpenArchived,
                )
            }
            EstratoAjustes(AVISOS, R.string.ajustes_estrato_avisos, resumenAvisos(actual, resources), abierto, { abierto = it }) {
                ContenidoAvisos(settings = actual, settingsViewModel = settingsViewModel)
            }
            EstratoAjustes(SONIDO, R.string.ajustes_estrato_sonido, resumenSonido(actual, resources), abierto, { abierto = it }) {
                ContenidoSonido(settings = actual, settingsViewModel = settingsViewModel)
            }
        }
        EstratoAjustes(COPIAS, R.string.ajustes_estrato_copias, resumenCopias(backupViewModel), abierto, { abierto = it }) {
            ContenidoCopias(backupViewModel)
        }
        EstratoAjustes(ACERCA, R.string.ajustes_estrato_acerca, resumenAcerca(), abierto, { abierto = it }) { ContenidoAcerca() }
        Box(Modifier.height(48.dp))
    }
}

/** Un estrato de Ajustes: se abre y se cierra con [alCambiar] y, al abrirse, sube a la vista. */
@Composable
private fun EstratoAjustes(
    indice: Int,
    titulo: Int,
    resumen: String,
    abierto: Int?,
    alCambiar: (Int?) -> Unit,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val peticion = remember { BringIntoViewRequester() }
    val esteAbierto = abierto == indice
    LaunchedEffect(esteAbierto) {
        if (esteAbierto) {
            delay(ESPERA_ABRIR_MS)
            peticion.bringIntoView()
        }
    }
    EstratoPlegable(
        estrato = BitoTema.colores.estratos.todos[indice],
        titulo = stringResource(titulo),
        resumen = resumen,
        abierto = esteAbierto,
        alPulsar = { alCambiar(if (esteAbierto) null else indice) },
        modifier = Modifier.bringIntoViewRequester(peticion),
        contenido = contenido,
    )
}
