@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.data.backup.BackupPreview
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.BitoSnackbar
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.TimePickerSheet
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import android.provider.Settings as AndroidSettings

private const val DEFAULT_REMINDER_MINUTES = 8 * 60

/**
 * Ajustes: day cutoff and reminders (this task) plus the manual backup export/import card
 * (guía 8a: the star, untouched here).
 */
@Composable
fun SettingsScreen(
    backupViewModel: BackupViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenArchived: () -> Unit,
) {
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()
    val archivedHabits by settingsViewModel.archivedHabits.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri?.let { backupViewModel.export(context.contentResolver, it) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { backupViewModel.loadImport(context.contentResolver, it) }
        }

    val exportDone = stringResource(R.string.msg_export_done)
    val importDone = stringResource(R.string.msg_import_done)
    val invalidFile = stringResource(R.string.msg_invalid_file)
    val ioError = stringResource(R.string.msg_io_error)

    LaunchedEffect(backupState.message) {
        val text =
            when (backupState.message) {
                BackupMessage.EXPORT_DONE -> exportDone
                BackupMessage.IMPORT_DONE -> importDone
                BackupMessage.INVALID_FILE -> invalidFile
                BackupMessage.IO_ERROR -> ioError
                // Folder/schedule/encryption UI (passphrase sheet, encryption snackbars, its own
                // MISSING_KEY snackbar) is task 9/10's — no text here yet keeps this snackbar
                // silent for them instead of guessing at copy that isn't this task's to write.
                BackupMessage.WRONG_PASSPHRASE,
                BackupMessage.ENCRYPTION_ON,
                BackupMessage.ENCRYPTION_OFF,
                BackupMessage.MISSING_KEY,
                null,
                -> null
            }
        if (text != null) {
            snackbar.showSnackbar(text)
            backupViewModel.consumeMessage()
        }
    }

    // POST_NOTIFICATIONS is only asked once, the moment the first global reminder shows up — a
    // permission prompt with no reminder to justify it yet reads as the app fishing for access.
    var notifDenied by remember { mutableStateOf(false) }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notifDenied = !granted
        }

    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }
    // Granting SCHEDULE_EXACT_ALARM happens in the system Settings app, which doesn't kill this
    // process — nothing recomposes this screen on its own, so the notice is re-checked on every
    // ON_RESUME (the user coming back from that system screen) rather than computed once.
    var exactAlarmsBlocked by remember { mutableStateOf(isExactAlarmsBlocked(alarmManager)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exactAlarmsBlocked = isExactAlarmsBlocked(alarmManager)
    }

    Scaffold(
        containerColor = Papel,
        snackbarHost = { SnackbarHost(snackbar) { BitoSnackbar(it) } },
    ) { padding ->
        // Without scroll the last card gets whatever height is left and collapses once the
        // persistent bottom bar + section subtitles outgrow the viewport.
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsHeader(onBack)
            settings?.let { current ->
                DaySectionCard(cutoffMinutes = current.dayCutoffMinutes, onSetCutoff = settingsViewModel::setCutoff)
                RemindersSectionCard(
                    reminderMinutes = current.globalReminderMinutes,
                    reviewMinutes = current.reviewTimeMinutes,
                    exactAlarmsBlocked = exactAlarmsBlocked,
                    notifDenied = notifDenied,
                    celebrationEnabled = current.perfectDayCelebration,
                    onSetCelebration = settingsViewModel::setPerfectDayCelebration,
                    onAddReminder = { minutes ->
                        val isFirstReminder = current.globalReminderMinutes.isEmpty()
                        settingsViewModel.addReminder(minutes)
                        if (isFirstReminder && Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onEditReminder = { old, new ->
                        settingsViewModel.removeReminder(old)
                        settingsViewModel.addReminder(new)
                    },
                    onRemoveReminder = settingsViewModel::removeReminder,
                    onSetReviewTime = settingsViewModel::setReviewTime,
                    onOpenExactAlarmSettings = {
                        // The row is only shown when exactAlarmsBlocked is true (itself SDK 31+
                        // gated), but the guard is repeated here so the constant reference itself
                        // is provably safe, not just reachability-safe.
                        if (Build.VERSION.SDK_INT >= 31) {
                            // Some OEM/Go builds don't ship this settings screen at all
                            // (ActivityNotFoundException); nothing more useful to do than no-op.
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        }
                    },
                )
                HabiSectionCard(soundsEnabled = current.habiSoundsEnabled, onSetHabiSounds = settingsViewModel::setHabiSounds)
            }
            GeneralSectionCard(archivedCount = archivedHabits.size, onOpenArchived = onOpenArchived)
            BackupsCard(
                onExport = { exportLauncher.launch(backupViewModel.suggestedFileName()) },
                // SAF can't filter on a custom ".bito" extension, so accept anything and let
                // loadImport's preview/validation reject the wrong file.
                onImport = { importLauncher.launch(arrayOf("*/*")) },
            )
        }
    }

    backupState.preview?.let { preview ->
        ImportPreviewSheet(preview = preview, onConfirm = backupViewModel::confirmImport, onDismiss = backupViewModel::dismissImport)
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

/** E7: the cutoff only shifts logging going forward — already-sealed days are never touched. */
@Composable
private fun DaySectionCard(
    cutoffMinutes: Int,
    onSetCutoff: (Int) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.day_section_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.day_section_body), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        Spacer(Modifier.height(4.dp))
        SettingsRow(label = stringResource(R.string.cutoff_label), value = formatClock(cutoffMinutes), onClick = { showSheet = true })
    }
    if (showSheet) {
        CutoffSheet(
            initialMinutes = cutoffMinutes,
            onConfirm = {
                onSetCutoff(it)
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

/**
 * Reminders card: one row per configured global hour (tap edits, X removes), an add row, the
 * daily-review hour, and — only when relevant — the exact-alarm and notification-permission
 * notices. `ReminderSync` (a different lane) observes the repository and reprograms alarms; this
 * screen only ever writes through [SettingsViewModel].
 */
@Composable
private fun RemindersSectionCard(
    reminderMinutes: List<Int>,
    reviewMinutes: Int,
    exactAlarmsBlocked: Boolean,
    notifDenied: Boolean,
    celebrationEnabled: Boolean,
    onSetCelebration: (Boolean) -> Unit,
    onAddReminder: (Int) -> Unit,
    onEditReminder: (old: Int, new: Int) -> Unit,
    onRemoveReminder: (Int) -> Unit,
    onSetReviewTime: (Int) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
) {
    var editingReminder by remember { mutableStateOf<Int?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showReviewSheet by remember { mutableStateOf(false) }

    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reminders_section_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.reminders_section_body), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        Spacer(Modifier.height(4.dp))
        reminderMinutes.forEach { minutes ->
            ReminderHourRow(minutes = minutes, onEdit = { editingReminder = minutes }, onRemove = { onRemoveReminder(minutes) })
        }
        if (reminderMinutes.isEmpty()) {
            Text(stringResource(R.string.reminder_none_hint), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            Spacer(Modifier.height(4.dp))
        }
        SettingsRow(label = stringResource(R.string.reminder_add), onClick = { showAddSheet = true })
        SettingsRow(label = stringResource(R.string.review_label), value = formatClock(reviewMinutes), onClick = { showReviewSheet = true })
        Text(stringResource(R.string.review_hint), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_celebration_label), style = MaterialTheme.typography.bodyLarge, color = Tinta)
                Text(
                    stringResource(R.string.settings_celebration_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = TintaSuave,
                )
            }
            Switch(
                checked = celebrationEnabled,
                onCheckedChange = onSetCelebration,
                modifier = Modifier.testTag("celebration-switch"),
            )
        }
        if (exactAlarmsBlocked) {
            SettingsRow(
                label = stringResource(R.string.reminder_exact_notice),
                onClick = onOpenExactAlarmSettings,
            )
        }
        if (notifDenied) {
            Text(stringResource(R.string.notif_permission_hint), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
    }

    editingReminder?.let { old ->
        TimePickerSheet(
            title = stringResource(R.string.reminders_section_title),
            initialMinutes = old,
            onConfirm = {
                onEditReminder(old, it)
                editingReminder = null
            },
            onDismiss = { editingReminder = null },
        )
    }
    if (showAddSheet) {
        TimePickerSheet(
            title = stringResource(R.string.reminder_add),
            initialMinutes = DEFAULT_REMINDER_MINUTES,
            onConfirm = {
                onAddReminder(it)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }
    if (showReviewSheet) {
        TimePickerSheet(
            title = stringResource(R.string.review_label),
            initialMinutes = reviewMinutes,
            onConfirm = {
                onSetReviewTime(it)
                showReviewSheet = false
            },
            onDismiss = { showReviewSheet = false },
        )
    }
}

/**
 * A configured reminder hour. The open-picker tap and the remove button are siblings, not
 * nested — an icon button inside a clickable row gives the row two overlapping touch targets
 * fighting for the same gesture (same reasoning as habitform's `ReminderRow`).
 */
@Composable
private fun ReminderHourRow(
    minutes: Int,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(onClick = onEdit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatClock(minutes), style = MaterialTheme.typography.bodyLarge, color = Tinta)
        }
        IconButton(onClick = onRemove) {
            Icon(BitoIcons.X, contentDescription = stringResource(R.string.reminder_remove), tint = TintaSuave)
        }
    }
}

/** One row, [Switch] pattern EXACT to habitform's `BinaryModeRow`: label + hint on the left, the toggle on the right. */
@Composable
private fun HabiSectionCard(
    soundsEnabled: Boolean,
    onSetHabiSounds: (Boolean) -> Unit,
) {
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_habi_section), style = MaterialTheme.typography.titleMedium, color = Tinta)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_habi_sounds), style = MaterialTheme.typography.bodyLarge, color = Tinta)
                Text(
                    stringResource(R.string.settings_habi_sounds_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = TintaSuave,
                )
            }
            Switch(
                checked = soundsEnabled,
                onCheckedChange = onSetHabiSounds,
                modifier = Modifier.testTag("habi-sounds-switch"),
            )
        }
    }
}

/** Hidden outright when there is nothing archived — a group with a single, sometimes-absent row. */
@Composable
private fun GeneralSectionCard(
    archivedCount: Int,
    onOpenArchived: () -> Unit,
) {
    if (archivedCount == 0) return
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.general_section_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
        Spacer(Modifier.height(4.dp))
        SettingsRow(label = pluralStringResource(R.plurals.archived_habits_row, archivedCount, archivedCount), onClick = onOpenArchived)
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
        SettingsRow(BitoIcons.Download, stringResource(R.string.backup_export), onClick = onExport)
        SettingsRow(BitoIcons.Upload, stringResource(R.string.backup_import), onClick = onImport)
    }
}

/** Generalized from the old BackupRow: any tappable settings line, with an optional value and icon. */
@Composable
private fun SettingsRow(
    icon: ImageVector? = null,
    label: String,
    value: String? = null,
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
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Hoja, modifier = Modifier.size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Tinta, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
        }
    }
}

/**
 * A ±30min stepper instead of [TimePickerSheet]: the cutoff only makes sense inside a 0:00–6:00
 * window, and a clock dial would let people pick times the app then silently clamps.
 */
@Composable
private fun CutoffSheet(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableIntStateOf(initialMinutes) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.cutoff_label), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { minutes = (minutes - CUTOFF_STEP_MINUTES).coerceIn(0, MAX_CUTOFF_MINUTES) }) {
                    Icon(BitoIcons.Minus, contentDescription = null, tint = Tinta)
                }
                Spacer(Modifier.width(16.dp))
                Text(formatClock(minutes), style = MaterialTheme.typography.displayLarge, color = Tinta)
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = { minutes = (minutes + CUTOFF_STEP_MINUTES).coerceIn(0, MAX_CUTOFF_MINUTES) }) {
                    Icon(BitoIcons.Plus, contentDescription = null, tint = Tinta)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.cutoff_note), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            Spacer(Modifier.height(16.dp))
            PillButton(text = stringResource(R.string.save), onClick = { onConfirm(minutes) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** "HH:MM", zero-padded, 24h — matches [TimePickerSheet]'s is24Hour clock. */
private fun formatClock(minutes: Int): String {
    val hh = (minutes / 60).toString().padStart(2, '0')
    val mm = (minutes % 60).toString().padStart(2, '0')
    return "$hh:$mm"
}

private fun isExactAlarmsBlocked(alarmManager: AlarmManager?): Boolean =
    Build.VERSION.SDK_INT >= 31 && alarmManager?.canScheduleExactAlarms() == false

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
            val habitsCount = pluralStringResource(R.plurals.import_preview_habits, preview.habits, preview.habits)
            val entriesCount = pluralStringResource(R.plurals.import_preview_entries, preview.entries, preview.entries)
            Text(
                stringResource(R.string.import_preview_counts, habitsCount, entriesCount),
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
