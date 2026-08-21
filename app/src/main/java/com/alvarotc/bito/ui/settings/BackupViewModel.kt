package com.alvarotc.bito.ui.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.backup.Argon2Params
import com.alvarotc.bito.data.backup.BackupCrypto
import com.alvarotc.bito.data.backup.BackupFormatException
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupPreview
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.backup.BackupWorker
import com.alvarotc.bito.data.backup.MissingKeyException
import com.alvarotc.bito.data.backup.WrongPassphraseException
import com.alvarotc.bito.data.settings.AutoBackupError
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class BackupMessage {
    EXPORT_DONE,
    IMPORT_DONE,
    INVALID_FILE,
    IO_ERROR,
    WRONG_PASSPHRASE,
    ENCRYPTION_ON,
    ENCRYPTION_OFF,

    // Missing from the task-8 brief's literal enum snippet, but the same brief's export()
    // walkthrough ("captura MissingKeyException → message = MISSING_KEY") and its required
    // "export with encryption on and no key reports MISSING_KEY" test both need it. Following
    // the narrative + test name over the snippet, appended last so no existing ordinal shifts.
    MISSING_KEY,
}

data class BackupUiState(
    val preview: BackupPreview? = null,
    val message: BackupMessage? = null,
    val busy: Boolean = false,
    val folderName: String? = null,
    val frequency: BackupFrequency = BackupFrequency.DAILY,
    val copies: Int = 5,
    val encryptionOn: Boolean = false,
    val encryptionNeedsKey: Boolean = false,
    val lastAutoBackupAtMillis: Long? = null,
    val lastAutoBackupError: AutoBackupError? = null,
    val askImportPassphrase: Boolean = false,
)

/**
 * Backs the Ajustes backup card: manual export/import over SAF streams, the folder/schedule/
 * encryption settings mirror, and the "back up now" one-shot trigger (tech doc §5.4).
 * `ioDispatcher` and `cryptoDispatcher` are not part of the task-8 brief's literal contract —
 * they're added so tests can pin ContentResolver stream I/O and Argon2 key derivation to the
 * same test scheduler as everything else, keeping `advanceUntilIdle()` deterministic instead of
 * racing a real Dispatchers.IO/Default thread.
 */
class BackupViewModel(
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
    private val keyStore: BackupKeyStore,
    backupNow: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Argon2 cost for the key [enableEncryption] derives. Production default (~100-300ms);
     * tests inject a much cheaper tier (e.g. `Argon2Params(64, 1, 1)`) so derivation doesn't
     * dominate the suite's runtime. Does NOT affect [submitImportPassphrase]'s cost — that
     * re-derives using whatever params are embedded in the container it's decrypting.
     */
    private val deriveParams: Argon2Params = Argon2Params(),
) : ViewModel() {
    // Plain constructor parameter, not `private val`: it never becomes a class member, so it
    // can't collide with the public backupNow() callback below — same name, per the brief.
    private val triggerBackupNow: () -> Unit = backupNow

    private val backupState = MutableStateFlow(BackupUiState())

    /**
     * Mirrors [backupState] (preview/message/busy/askImportPassphrase) plus the backup-related
     * [SettingsRepository] fields. `keyStore.load()` runs again on every settings emission — a
     * single small file read, cheap enough not to warrant its own dispatcher hop.
     */
    val state: StateFlow<BackupUiState> =
        combine(backupState, settings.settings) { base, prefs ->
            base.copy(
                folderName = prefs.backupFolderUri?.let(::folderNameOf),
                frequency = prefs.backupFrequency,
                copies = prefs.backupCopies,
                encryptionOn = prefs.backupEncryption,
                encryptionNeedsKey = prefs.backupEncryption && keyStore.load() == null,
                lastAutoBackupAtMillis = prefs.lastAutoBackupAtMillis,
                lastAutoBackupError = prefs.lastAutoBackupError,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    private var pendingImportText: String? = null
    private var pendingImportBytes: ByteArray? = null

    fun suggestedFileName(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(zone())
        return "bito-backup-${formatter.format(Instant.ofEpochMilli(now()))}.bito"
    }

    /** Encrypts when settings ask for it; a missing key never lies as [BackupMessage.IO_ERROR]. */
    fun export(
        resolver: ContentResolver,
        uri: Uri,
    ) {
        viewModelScope.launch {
            backupState.update { it.copy(busy = true) }
            val result =
                runCatching {
                    withContext(ioDispatcher) {
                        val bytes = backup.exportBytes(now())
                        resolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: throw IOException("Could not open output stream for $uri")
                    }
                }
            val message =
                when {
                    result.isSuccess -> BackupMessage.EXPORT_DONE
                    result.exceptionOrNull() is MissingKeyException -> BackupMessage.MISSING_KEY
                    else -> BackupMessage.IO_ERROR
                }
            backupState.update { it.copy(busy = false, message = message) }
        }
    }

    /**
     * Reads the file; an encrypted one asks for a passphrase ([submitImportPassphrase]) instead
     * of previewing directly. The raw text is held so [confirmImport] doesn't re-read it.
     */
    fun loadImport(
        resolver: ContentResolver,
        uri: Uri,
    ) {
        viewModelScope.launch {
            backupState.update { it.copy(busy = true) }
            val result =
                runCatching {
                    withContext(ioDispatcher) {
                        resolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IOException("Could not open input stream for $uri")
                    }
                }
            result
                .onSuccess { bytes -> onImportBytesRead(bytes) }
                .onFailure { error -> onImportLoadFailed(error) }
        }
    }

    private suspend fun onImportBytesRead(bytes: ByteArray) {
        if (backup.isEncrypted(bytes)) {
            pendingImportBytes = bytes
            pendingImportText = null
            backupState.update { it.copy(busy = false, preview = null, askImportPassphrase = true) }
            return
        }
        val result =
            runCatching {
                withContext(ioDispatcher) {
                    val text = bytes.decodeToString()
                    text to backup.preview(text)
                }
            }
        result
            .onSuccess { (text, preview) ->
                pendingImportText = text
                backupState.update { it.copy(busy = false, preview = preview, askImportPassphrase = false) }
            }.onFailure { error -> onImportLoadFailed(error) }
    }

    private fun onImportLoadFailed(error: Throwable) {
        pendingImportText = null
        pendingImportBytes = null
        val message = if (error is BackupFormatException) BackupMessage.INVALID_FILE else BackupMessage.IO_ERROR
        backupState.update { it.copy(busy = false, preview = null, message = message, askImportPassphrase = false) }
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

    /**
     * Persists the new folder verbatim as `uri.toString()` — the auto-backup worker's permission
     * check matches against this exact string, so it must be the same string the screen passed
     * to `takePersistableUriPermission`. Releases the previous folder's grant, if there was one
     * and it differs from the new one, and clears a stale [AutoBackupError] so the UI never
     * blames the new folder for the old one's failure.
     */
    fun setFolder(
        resolver: ContentResolver,
        uri: Uri,
    ) {
        val newUri = uri.toString()
        viewModelScope.launch {
            val oldUri = settings.settings.first().backupFolderUri
            if (oldUri != null && oldUri != newUri) {
                runCatching {
                    resolver.releasePersistableUriPermission(
                        Uri.parse(oldUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            settings.update { it.copy(backupFolderUri = newUri, lastAutoBackupError = null) }
        }
    }

    fun setFrequency(frequency: BackupFrequency) = write { it.copy(backupFrequency = frequency) }

    fun setCopies(copies: Int) = write { it.copy(backupCopies = copies.coerceIn(1, 10)) }

    /** No-op when no folder is configured — T9/T10 disable this row in that case too. */
    fun backupNow() {
        viewModelScope.launch {
            if (settings.settings.first().backupFolderUri != null) {
                triggerBackupNow()
            }
        }
    }

    /**
     * Derives the key on [cryptoDispatcher] (Argon2 ≈ 100-300ms at production cost), stores it,
     * then flips the setting. The passphrase is wiped as soon as derivation is done, success or
     * failure. A failed derivation/save (e.g. [BackupKeyStore.save]'s IOException) reports
     * [BackupMessage.IO_ERROR] instead of leaving [BackupUiState.busy] stuck forever.
     */
    fun enableEncryption(passphrase: CharArray) {
        viewModelScope.launch {
            backupState.update { it.copy(busy = true) }
            val result =
                try {
                    runCatching {
                        withContext(cryptoDispatcher) {
                            keyStore.save(BackupCrypto.deriveKey(passphrase, BackupCrypto.newSalt(), deriveParams))
                        }
                    }
                } finally {
                    passphrase.fill(' ')
                }
            if (result.isSuccess) {
                settings.update { it.copy(backupEncryption = true) }
                backupState.update { it.copy(busy = false, message = BackupMessage.ENCRYPTION_ON) }
            } else {
                backupState.update { it.copy(busy = false, message = BackupMessage.IO_ERROR) }
            }
        }
    }

    fun disableEncryption() {
        viewModelScope.launch {
            keyStore.clear()
            settings.update { it.copy(backupEncryption = false) }
            backupState.update { it.copy(message = BackupMessage.ENCRYPTION_OFF) }
        }
    }

    /**
     * Decrypts [pendingImportBytes] with [passphrase] on [cryptoDispatcher], then previews it
     * exactly like an unencrypted file. A wrong passphrase keeps the sheet open for a retry; any
     * other failure (corrupted container) closes it like a normal failed import.
     */
    fun submitImportPassphrase(passphrase: CharArray) {
        val bytes = pendingImportBytes ?: return
        viewModelScope.launch {
            backupState.update { it.copy(busy = true) }
            val result =
                try {
                    runCatching {
                        withContext(cryptoDispatcher) {
                            val text = backup.decryptToJson(bytes, passphrase)
                            text to backup.preview(text)
                        }
                    }
                } finally {
                    passphrase.fill(' ')
                }
            result
                .onSuccess { (text, preview) ->
                    pendingImportBytes = null
                    pendingImportText = text
                    backupState.update { it.copy(busy = false, preview = preview, askImportPassphrase = false) }
                }.onFailure { error ->
                    if (error is WrongPassphraseException) {
                        backupState.update { it.copy(busy = false, message = BackupMessage.WRONG_PASSPHRASE) }
                    } else {
                        pendingImportBytes = null
                        val message = if (error is BackupFormatException) BackupMessage.INVALID_FILE else BackupMessage.IO_ERROR
                        backupState.update { it.copy(busy = false, message = message, askImportPassphrase = false) }
                    }
                }
        }
    }

    fun dismissImportPassphrase() {
        pendingImportBytes = null
        backupState.update { it.copy(askImportPassphrase = false) }
    }

    fun consumeMessage() = backupState.update { it.copy(message = null) }

    private fun write(transform: (Settings) -> Settings) {
        viewModelScope.launch { settings.update(transform) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application =
                        requireNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                            "BackupViewModel.factory requires an Application in CreationExtras"
                        }
                    BackupViewModel(
                        container.backup,
                        container.settings,
                        container.keyStore,
                        backupNow = { BackupWorker.oneShot(application) },
                    )
                }
            }
    }
}

/** Readable tail of a SAF tree uri, e.g. "primary:Documents/Bito" → "Documents/Bito". */
private fun folderNameOf(uriString: String): String? = Uri.parse(uriString).lastPathSegment?.substringAfterLast(':')
