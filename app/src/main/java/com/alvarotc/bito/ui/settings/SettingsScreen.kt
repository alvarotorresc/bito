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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.data.backup.BackupPreview
import com.alvarotc.bito.data.settings.AutoBackupError
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.BitoSnackbar
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SegmentedPills
import com.alvarotc.bito.ui.components.TimePickerSheet
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import android.provider.Settings as AndroidSettings

private const val DEFAULT_REMINDER_MINUTES = 8 * 60
private const val MIN_BACKUP_COPIES = 1
private const val MAX_BACKUP_COPIES = 10

/** tech doc 5.2: any shorter and Argon2 is defending an easily-guessed passphrase. */
private const val MIN_PASSPHRASE_LENGTH = 8

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
    val folderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                backupViewModel.setFolder(context.contentResolver, it)
            }
        }

    val exportDone = stringResource(R.string.msg_export_done)
    val importDone = stringResource(R.string.msg_import_done)
    val invalidFile = stringResource(R.string.msg_invalid_file)
    val ioError = stringResource(R.string.msg_io_error)
    val encryptionOnSnack = stringResource(R.string.backup_encrypt_enabled_snack)
    val encryptionOffSnack = stringResource(R.string.backup_encrypt_disabled_snack)
    val missingKeySnack = stringResource(R.string.backup_missing_key_snack)

    // WRONG_PASSPHRASE has no snackbar copy — ImportPassphraseSheet paints it inline instead. But
    // by the time that sheet recomposes, this same effect has already called consumeMessage() and
    // backupState.message is back to null, so the sheet can't just compare against state.message
    // at render time. Latching it into this separate flag (set here, cleared by the sheet itself
    // on its next submit/dismiss — see ImportPassphraseSheet's call site below) survives the
    // consume without racing it.
    var wrongPassphraseShown by remember { mutableStateOf(false) }

    LaunchedEffect(backupState.message) {
        // Every non-null message must be consumed, whether or not it has copy — otherwise a
        // message with no text (WRONG_PASSPHRASE) sticks in state forever, and a second
        // identical message right after never re-fires this effect (same key, no transition
        // through null in between).
        val message = backupState.message ?: return@LaunchedEffect
        if (message == BackupMessage.WRONG_PASSPHRASE) {
            wrongPassphraseShown = true
        }
        val text =
            when (message) {
                BackupMessage.EXPORT_DONE -> exportDone
                BackupMessage.IMPORT_DONE -> importDone
                BackupMessage.INVALID_FILE -> invalidFile
                BackupMessage.IO_ERROR -> ioError
                BackupMessage.ENCRYPTION_ON -> encryptionOnSnack
                BackupMessage.ENCRYPTION_OFF -> encryptionOffSnack
                BackupMessage.MISSING_KEY -> missingKeySnack
                // The import passphrase sheet shows this inline (see wrongPassphraseShown above)
                // instead of a snackbar — a snackbar could time out and vanish before the user
                // finishes reading it, right next to a text field asking them to try again.
                BackupMessage.WRONG_PASSPHRASE -> null
            }
        if (text != null) {
            snackbar.showSnackbar(text)
        }
        backupViewModel.consumeMessage()
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
                state = backupState,
                onPickFolder = { folderLauncher.launch(null) },
                onSetFrequency = backupViewModel::setFrequency,
                onSetCopies = backupViewModel::setCopies,
                onBackupNow = backupViewModel::backupNow,
                onExport = { exportLauncher.launch(backupViewModel.suggestedFileName()) },
                // SAF can't filter on a custom ".bito" extension, so accept anything and let
                // loadImport's preview/validation reject the wrong file.
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onEnableEncryption = backupViewModel::enableEncryption,
                onDisableEncryption = backupViewModel::disableEncryption,
            )
        }
    }

    backupState.preview?.let { preview ->
        ImportPreviewSheet(preview = preview, onConfirm = backupViewModel::confirmImport, onDismiss = backupViewModel::dismissImport)
    }

    if (backupState.askImportPassphrase) {
        ImportPassphraseSheet(
            busy = backupState.busy,
            showWrongPassphrase = wrongPassphraseShown,
            onConfirm = { passphrase ->
                // A fresh attempt: drop any stale inline error before the new one (if any) lands.
                wrongPassphraseShown = false
                backupViewModel.submitImportPassphrase(passphrase)
            },
            onDismiss = {
                wrongPassphraseShown = false
                backupViewModel.dismissImportPassphrase()
            },
        )
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

/**
 * THE star of Ajustes (guía 8a): a Hoja-bordered card, not a solid-accent one.
 *
 * The encryption row's two local sheets (create/re-create, off-confirm) live here, not hoisted to
 * [SettingsScreen] — same split as [DaySectionCard]'s [CutoffSheet]: they're pure UI state with no
 * VM-owned flag behind them, unlike the import passphrase sheet which mirrors [BackupUiState.askImportPassphrase].
 */
@Composable
private fun BackupsCard(
    state: BackupUiState,
    onPickFolder: () -> Unit,
    onSetFrequency: (BackupFrequency) -> Unit,
    onSetCopies: (Int) -> Unit,
    onBackupNow: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onEnableEncryption: (CharArray) -> Unit,
    onDisableEncryption: () -> Unit,
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var showDisableConfirm by remember { mutableStateOf(false) }

    BitoCard(border = Hoja, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backups_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.backups_body), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        Spacer(Modifier.height(12.dp))
        SettingsRow(
            BitoIcons.Folder,
            stringResource(R.string.backup_folder),
            value = state.folderName ?: stringResource(R.string.backup_folder_none),
            onClick = onPickFolder,
        )
        if (state.folderName != null) {
            val frequencyOptions = listOf(stringResource(R.string.backup_frequency_daily), stringResource(R.string.backup_frequency_weekly))
            SegmentedPills(
                options = frequencyOptions,
                selectedIndex = if (state.frequency == BackupFrequency.WEEKLY) 1 else 0,
                onSelect = { onSetFrequency(if (it == 1) BackupFrequency.WEEKLY else BackupFrequency.DAILY) },
                fillWidth = true,
            )
            Spacer(Modifier.height(8.dp))
            CopiesStepperRow(copies = state.copies, onSetCopies = onSetCopies)
            BackupStatusLine(state)
            SettingsRow(BitoIcons.RefreshCw, stringResource(R.string.backup_now), onClick = onBackupNow)
        }
        SettingsRow(
            BitoIcons.Lock,
            stringResource(R.string.backup_encrypt),
            value =
                when {
                    state.encryptionNeedsKey -> stringResource(R.string.backup_encrypt_needs_key)
                    state.encryptionOn -> stringResource(R.string.backup_encrypt_on)
                    else -> stringResource(R.string.backup_encrypt_off)
                },
            onClick = {
                // ON and healthy is the only case that turns it off; everything else (OFF, or ON
                // but needing a key) opens the same create/re-create sheet.
                if (state.encryptionOn && !state.encryptionNeedsKey) {
                    showDisableConfirm = true
                } else {
                    showCreateSheet = true
                }
            },
        )
        SettingsRow(BitoIcons.Download, stringResource(R.string.backup_export), onClick = onExport)
        SettingsRow(BitoIcons.Upload, stringResource(R.string.backup_import), onClick = onImport)
    }

    if (showCreateSheet) {
        PassphraseCreateSheet(
            busy = state.busy,
            onConfirm = { passphrase ->
                onEnableEncryption(passphrase)
                showCreateSheet = false
            },
            onDismiss = { showCreateSheet = false },
        )
    }
    if (showDisableConfirm) {
        EncryptionOffConfirmDialog(
            onConfirm = {
                onDisableEncryption()
                showDisableConfirm = false
            },
            onDismiss = { showDisableConfirm = false },
        )
    }
}

/** Non-destructive by design (tech doc 5.2): old encrypted backups stay readable with their own passphrase. */
@Composable
private fun EncryptionOffConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tarjeta,
        title = { Text(stringResource(R.string.backup_encrypt), color = Tinta) },
        text = { Text(stringResource(R.string.backup_encrypt_off_note), color = TintaSuave) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_encrypt_off), color = Tinta)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = TintaSuave)
            }
        },
    )
}

/**
 * Enable, or re-create after [BackupUiState.encryptionNeedsKey] (the stored key file is gone but
 * the setting is still on) — same sheet either way, [BackupViewModel.enableEncryption] just
 * overwrites whatever key was or wasn't there. The two locals below hold the raw passphrase only
 * for as long as the sheet is open; every way out (confirm or cancel) blanks them before returning.
 */
@Composable
private fun PassphraseCreateSheet(
    busy: Boolean,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val canConfirm = !busy && passphrase.length >= MIN_PASSPHRASE_LENGTH && passphrase == repeat

    fun dismissAndClear() {
        passphrase = ""
        repeat = ""
        onDismiss()
    }

    // skipPartiallyExpanded (TimePickerSheet precedent): two fields, the Peligro warning and two
    // buttons don't reliably fit in the half-expanded height, which would clip the confirm button
    // below the viewport.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = ::dismissAndClear, sheetState = sheetState, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.backup_passphrase)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("passphrase-field"),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = repeat,
                onValueChange = { repeat = it },
                label = { Text(stringResource(R.string.backup_passphrase_repeat)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("passphrase-repeat-field"),
            )
            Spacer(Modifier.height(12.dp))
            // The one legitimate Peligro use in this screen (tech doc 5.2): every other warning
            // here is Brasa, but a passphrase Bito cannot recover is genuinely irreversible.
            Text(stringResource(R.string.backup_encrypt_warning), style = MaterialTheme.typography.labelMedium, color = Peligro)
            Spacer(Modifier.height(16.dp))
            PillButton(
                stringResource(R.string.save),
                onClick = {
                    val chars = passphrase.toCharArray()
                    passphrase = ""
                    repeat = ""
                    onConfirm(chars)
                },
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth().testTag("passphrase-confirm"),
            )
            Spacer(Modifier.height(8.dp))
            GhostPillButton(stringResource(R.string.cancel), onClick = ::dismissAndClear, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * [showWrongPassphrase] is [SettingsScreen]'s latched flag, not a live read of
 * `BackupUiState.message` — see the comment at that flag's declaration for why a direct read
 * would race [BackupViewModel.consumeMessage].
 */
@Composable
private fun ImportPassphraseSheet(
    busy: Boolean,
    showWrongPassphrase: Boolean,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }

    fun dismissAndClear() {
        passphrase = ""
        onDismiss()
    }

    // Same skipPartiallyExpanded reasoning as PassphraseCreateSheet: the inline error line only
    // appears after a wrong guess, and a half-expanded sheet would clip it (or the confirm button)
    // right when the user most needs to see it.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = ::dismissAndClear, sheetState = sheetState, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.backup_import), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.backup_passphrase)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = showWrongPassphrase,
                modifier = Modifier.fillMaxWidth().testTag("import-passphrase-field"),
            )
            if (showWrongPassphrase) {
                Spacer(Modifier.height(4.dp))
                // Brasa, not Peligro — that color is reserved for the irrecoverable-passphrase
                // warning in PassphraseCreateSheet, and a wrong guess here is just a retry.
                Text(stringResource(R.string.backup_passphrase_wrong), style = MaterialTheme.typography.labelMedium, color = Brasa)
            }
            Spacer(Modifier.height(16.dp))
            PillButton(
                stringResource(R.string.save),
                onClick = {
                    val chars = passphrase.toCharArray()
                    passphrase = ""
                    onConfirm(chars)
                },
                enabled = passphrase.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth().testTag("import-passphrase-confirm"),
            )
            Spacer(Modifier.height(8.dp))
            GhostPillButton(stringResource(R.string.cancel), onClick = ::dismissAndClear, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** −/value/+ pattern mirrored from [CutoffSheet]'s stepper; the honest clamp is 1..10, matching [BackupViewModel.setCopies]. */
@Composable
private fun CopiesStepperRow(
    copies: Int,
    onSetCopies: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.backup_copies),
            style = MaterialTheme.typography.bodyLarge,
            color = Tinta,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onSetCopies(copies - 1) }, enabled = copies > MIN_BACKUP_COPIES) {
            Icon(BitoIcons.Minus, contentDescription = null, tint = if (copies > MIN_BACKUP_COPIES) Tinta else TintaSuave)
        }
        Text(copies.toString(), style = MaterialTheme.typography.bodyLarge, color = Tinta)
        IconButton(onClick = { onSetCopies(copies + 1) }, enabled = copies < MAX_BACKUP_COPIES) {
            Icon(BitoIcons.Plus, contentDescription = null, tint = if (copies < MAX_BACKUP_COPIES) Tinta else TintaSuave)
        }
    }
}

/**
 * An error (Tinta + Info icon — Peligro is destructive-only, this is informational) wins over the
 * last-success timestamp; with neither (no auto-backup has run yet), the line is simply absent.
 */
@Composable
private fun BackupStatusLine(state: BackupUiState) {
    val text =
        when (state.lastAutoBackupError) {
            AutoBackupError.FOLDER -> stringResource(R.string.backup_error_folder)
            AutoBackupError.WRITE -> stringResource(R.string.backup_error_write)
            AutoBackupError.MISSING_KEY -> stringResource(R.string.backup_error_missing_key)
            null -> state.lastAutoBackupAtMillis?.let { stringResource(R.string.backup_last_ok, formatBackupTimestamp(it)) }
        } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(BitoIcons.Info, contentDescription = null, tint = Tinta, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Tinta)
    }
    Spacer(Modifier.height(4.dp))
}

/** No shared millis+time formatter exists yet (Dates.kt only formats [LogicalDay]/[YearMonth] calendar values). */
private fun formatBackupTimestamp(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

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
