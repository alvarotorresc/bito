package com.alvarotc.bito.data.backup

import android.net.Uri
import com.alvarotc.bito.data.settings.AutoBackupError
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The whole auto-backup pass, WorkManager-free so tests drive it directly (tech doc M8 T5).
 * [Outcome] mirrors Worker Results: DONE/SKIPPED end the attempt, RETRY asks WorkManager to
 * try again on its own backoff.
 */
class AutoBackupRunner(
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val sink: BackupSink,
    // prod: resolver.persistedUriPermissions.any { it.uri.toString() == uri && it.isWritePermission }
    private val hasPersistedPermission: (String) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    enum class Outcome { DONE, SKIPPED, RETRY }

    suspend fun run(): Outcome {
        val prefs = settings.settings.first()
        val folderUri = prefs.backupFolderUri ?: return Outcome.SKIPPED

        if (!hasPersistedPermission(folderUri)) {
            // Retrying doesn't fix a revoked grant; only the user re-picking the folder does.
            settings.update { it.copy(lastAutoBackupError = AutoBackupError.FOLDER) }
            return Outcome.SKIPPED
        }

        val nowMillis = now() // captured once so the file name, export, and stamp all agree
        val bytes =
            try {
                backup.exportBytes(nowMillis)
            } catch (e: MissingKeyException) {
                settings.update { it.copy(lastAutoBackupError = AutoBackupError.MISSING_KEY) }
                return Outcome.SKIPPED
            } catch (e: CancellationException) {
                // Never swallow cancellation as a failure — WorkManager stopping this coroutine
                // must propagate, not get recorded as a write error.
                throw e
            } catch (e: Exception) {
                // Anything else (DataStore IOException, SQLiteException, ...) is an unknown
                // failure, not a missing key — still worth a retry rather than leaving the
                // last-known status stale forever.
                settings.update { it.copy(lastAutoBackupError = AutoBackupError.WRITE) }
                return Outcome.RETRY
            }

        val treeUri = Uri.parse(folderUri)
        val fileName = fileNameFor(nowMillis)
        try {
            sink.write(treeUri, fileName, bytes)
        } catch (e: SecurityException) {
            // The grant can be revoked between the check above and the actual SAF call
            // (TOCTOU) — same fix as a missing grant: the user re-picks the folder.
            settings.update { it.copy(lastAutoBackupError = AutoBackupError.FOLDER) }
            return Outcome.SKIPPED
        } catch (e: IOException) {
            settings.update { it.copy(lastAutoBackupError = AutoBackupError.WRITE) }
            return Outcome.RETRY
        } catch (e: CancellationException) {
            // Same as above: cancellation must propagate, not be recorded as a write error.
            throw e
        } catch (e: Exception) {
            // Any other failure from the SAF call (RuntimeException, SQLiteException, ...) still
            // means the file may or may not be on disk — treat it like a write failure so the
            // status isn't left stale and WorkManager gets a chance to retry.
            settings.update { it.copy(lastAutoBackupError = AutoBackupError.WRITE) }
            return Outcome.RETRY
        }

        // The backup itself is already safe on disk; a rotation failure must not undo that.
        // The next run's rotate() call cleans up what this one couldn't.
        runCatching { sink.rotate(treeUri, prefs.backupCopies) }
        settings.update { it.copy(lastAutoBackupAtMillis = nowMillis, lastAutoBackupError = null) }
        return Outcome.DONE
    }

    private fun fileNameFor(millis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(zone())
        return "bito-backup-${formatter.format(Instant.ofEpochMilli(millis))}.bito"
    }
}
