@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.data.backup.BackupPreview
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/** Minimal Ajustes: one screen, one card — manual backup export/import (guía 8a: the star). */
@Composable
fun SettingsScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri?.let { viewModel.export(context.contentResolver, it) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.loadImport(context.contentResolver, it) }
        }

    val exportDone = stringResource(R.string.msg_export_done)
    val importDone = stringResource(R.string.msg_import_done)
    val invalidFile = stringResource(R.string.msg_invalid_file)
    val ioError = stringResource(R.string.msg_io_error)

    LaunchedEffect(state.message) {
        val text =
            when (state.message) {
                BackupMessage.EXPORT_DONE -> exportDone
                BackupMessage.IMPORT_DONE -> importDone
                BackupMessage.INVALID_FILE -> invalidFile
                BackupMessage.IO_ERROR -> ioError
                null -> null
            }
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = Papel,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsHeader(onBack)
            BackupsCard(
                onExport = { exportLauncher.launch(viewModel.suggestedFileName()) },
                // SAF can't filter on a custom ".bito" extension, so accept anything and let
                // loadImport's preview/validation reject the wrong file.
                onImport = { importLauncher.launch(arrayOf("*/*")) },
            )
        }
    }

    state.preview?.let { preview ->
        ImportPreviewSheet(preview = preview, onConfirm = viewModel::confirmImport, onDismiss = viewModel::dismissImport)
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), tint = Tinta)
        }
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge, color = Tinta)
    }
}

/** THE star of Ajustes (guía 8a): a Hoja-bordered card, not a solid-accent one. */
@Composable
private fun BackupsCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    BitoCard(border = Hoja, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backups_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.backups_body), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        Spacer(Modifier.height(12.dp))
        BackupRow(BitoIcons.Download, stringResource(R.string.backup_export), onExport)
        BackupRow(BitoIcons.Upload, stringResource(R.string.backup_import), onImport)
    }
}

@Composable
private fun BackupRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Hoja, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Tinta)
    }
}

/** Restore confirmation (tech doc §5.4): the warning is Brasa — emotional heat, never interaction. */
@Composable
private fun ImportPreviewSheet(
    preview: BackupPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.import_preview_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.import_preview_meta, preview.exportedAt.take(10), preview.appVersion),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.import_preview_counts, preview.habits, preview.entries),
                style = MaterialTheme.typography.bodyLarge,
                color = Tinta,
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.import_preview_warning), style = MaterialTheme.typography.labelMedium, color = Brasa)
            Spacer(Modifier.height(16.dp))
            PillButton(stringResource(R.string.import_confirm), onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GhostPillButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
