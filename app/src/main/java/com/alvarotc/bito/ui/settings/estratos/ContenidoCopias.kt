package com.alvarotc.bito.ui.settings.estratos

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alvarotc.bito.ui.settings.BackupViewModel

/** El cuerpo del estrato «Copias de seguridad» (Tarea 25). */
@Composable
fun ContenidoCopias(
    backupViewModel: BackupViewModel,
    modifier: Modifier = Modifier,
) = Box(modifier)

/** La línea del estrato cerrado: carpeta y última copia, o el error. */
@Composable
fun resumenCopias(backupViewModel: BackupViewModel): String = ""
