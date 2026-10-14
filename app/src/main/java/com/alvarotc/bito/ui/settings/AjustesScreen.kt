package com.alvarotc.bito.ui.settings

import androidx.compose.runtime.Composable
import com.alvarotc.bito.ui.habi.HabiViewModel

/**
 * Ajustes de la Bito 2 (spec §5.1). Hasta la Tarea 23 enseña la pantalla de siempre: si la noche se
 * para antes, Ajustes sigue funcionando. [habiViewModel] es el de la actividad: da el jardín de la maqueta.
 */
@Composable
fun AjustesScreen(
    backupViewModel: BackupViewModel,
    settingsViewModel: SettingsViewModel,
    habiViewModel: HabiViewModel,
    onBack: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenIntro: () -> Unit = {},
) = SettingsScreen(backupViewModel, settingsViewModel, onBack, onOpenArchived, onOpenIntro)
