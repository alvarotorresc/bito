package com.alvarotc.bito.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.backup.BackupFormatException
import com.alvarotc.bito.data.backup.BackupPreview
import com.alvarotc.bito.data.backup.BackupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class BackupMessage { EXPORT_DONE, IMPORT_DONE, INVALID_FILE, IO_ERROR }

data class BackupUiState(
    val preview: BackupPreview? = null,
    val message: BackupMessage? = null,
    val busy: Boolean = false,
)

/**
 * Backs the Ajustes backup card: manual export/import over SAF streams (tech
 * doc §5.4). `ioDispatcher` is not part of the task-12 brief's contract — it's
 * added so tests can pin the ContentResolver stream I/O to the same test
 * scheduler as everything else, keeping `advanceUntilIdle()` deterministic
 * instead of racing a real Dispatchers.IO thread.
 */
class BackupViewModel(
    private val backup: BackupRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val backupState = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = backupState.asStateFlow()

    private var pendingImportText: String? = null

    fun suggestedFileName(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(zone())
        return "bito-backup-${formatter.format(Instant.ofEpochMilli(now()))}.bito"
    }

    fun export(
        resolver: ContentResolver,
        uri: Uri,
    ) {
        viewModelScope.launch {
            backupState.update { it.copy(busy = true) }
            val result =
                runCatching {
                    withContext(ioDispatcher) {
                        val json = backup.exportJson(now())
                        resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                            ?: throw IOException("Could not open output stream for $uri")
                    }
                }
            backupState.update {
                it.copy(busy = false, message = if (result.isSuccess) BackupMessage.EXPORT_DONE else BackupMessage.IO_ERROR)
            }
        }
    }

    /** Reads and previews the file; the raw text is held so [confirmImport] doesn't re-read it. */
    fun loadImport(
        resolver: ContentResolver,
        uri: Uri,
    ) {
        viewModelScope.launch {
            backupState.update { it.copy(busy = true) }
            val result =
                runCatching {
                    withContext(ioDispatcher) {
                        val text =
                            resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                                ?: throw IOException("Could not open input stream for $uri")
                        text to backup.preview(text)
                    }
                }
            result
                .onSuccess { (text, preview) ->
                    pendingImportText = text
                    backupState.update { it.copy(busy = false, preview = preview) }
                }.onFailure { error ->
                    pendingImportText = null
                    val message = if (error is BackupFormatException) BackupMessage.INVALID_FILE else BackupMessage.IO_ERROR
                    backupState.update { it.copy(busy = false, preview = null, message = message) }
                }
        }
    }

    fun confirmImport() {
        val text = pendingImportText ?: return
        viewModelScope.launch {
            backupState.update { it.copy(busy = true) }
            val result = runCatching { withContext(ioDispatcher) { backup.import(text) } }
            pendingImportText = null
            backupState.update {
                it.copy(
                    busy = false,
                    preview = null,
                    message = if (result.isSuccess) BackupMessage.IMPORT_DONE else BackupMessage.IO_ERROR,
                )
            }
        }
    }

    fun dismissImport() {
        pendingImportText = null
        backupState.update { it.copy(preview = null) }
    }

    fun consumeMessage() = backupState.update { it.copy(message = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { BackupViewModel(container.backup) }
            }
    }
}
