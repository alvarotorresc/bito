package com.alvarotc.bito.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/** Seam so AutoBackupRunner (T5) is testable without SAF. */
interface BackupSink {
    suspend fun write(
        treeUri: Uri,
        fileName: String,
        bytes: ByteArray,
    )

    suspend fun rotate(
        treeUri: Uri,
        keep: Int,
    )
}

private val BACKUP_NAME_PATTERN = Regex("""bito-backup-\d{4}-\d{2}-\d{2}-\d{4}\.bito""")

/** Whether [name] is a bito auto-backup file — shared so [BackupViewModel]'s in-folder count uses the exact same rule [rotationVictims] rotates by, instead of a second regex drifting out of sync. */
internal fun isBackupName(name: String): Boolean = BACKUP_NAME_PATTERN.matches(name)

/**
 * All SAF I/O for auto backups. The .tmp dance is the M3 deferral closed:
 * an interrupted write must never leave a half backup with the real name.
 */
class SafBackupWriter(private val context: Context) : BackupSink {
    override suspend fun write(
        treeUri: Uri,
        fileName: String,
        bytes: ByteArray,
    ) {
        val root =
            DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IOException("Cannot open backup folder for $treeUri")

        val tmpName = "$fileName.tmp"
        // An orphaned .tmp from ANY earlier run (its own name carries a different date/time
        // than today's), or a previous file with today's exact final name (the worker re-ran
        // in the same minute), must go before we start fresh.
        val files = root.listFiles()
        val stale = staleWriteTargets(files.mapNotNull { it.name }, fileName).toSet()
        files.forEach { existing -> if (existing.name in stale) existing.delete() }

        val tmpDoc =
            root.createFile("application/octet-stream", tmpName)
                ?: throw IOException("Cannot create $tmpName in backup folder")

        try {
            // Use the DocumentFile createFile actually returned, not a fresh lookup by name:
            // some providers mangle displayName when the mime doesn't match the extension.
            val stream =
                context.contentResolver.openOutputStream(tmpDoc.uri)
                    ?: throw IOException("Cannot open output stream for $tmpName")
            stream.use { it.write(bytes) }

            if (!tmpDoc.renameTo(fileName)) {
                throw IOException("Failed to rename $tmpName to $fileName")
            }
        } catch (e: IOException) {
            tmpDoc.delete() // best-effort: never leave the half-written tmp behind
            throw e
        } catch (e: SecurityException) {
            // The persisted tree grant can be revoked (or the volume can vanish) between
            // fromTreeUri() succeeding and the actual write — the canonical SAF failure.
            tmpDoc.delete()
            throw IOException("Failed to write $fileName", e)
        }
    }

    override suspend fun rotate(
        treeUri: Uri,
        keep: Int,
    ) {
        val root =
            DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IOException("Cannot open backup folder for $treeUri")

        val files = root.listFiles()
        val victims = rotationVictims(files.mapNotNull { it.name }, keep).toSet()

        files.forEach { doc ->
            if (doc.name in victims) doc.delete()
        }
    }
}

/**
 * Pure pre-write cleanup policy, JVM-testable: which existing names to delete before writing
 * [fileName] — any orphaned `.tmp` left by an earlier run's bito backup, plus a previous file
 * that already carries this exact final name. Never touches a name from something else sharing
 * the folder (a `.tmp` whose stripped name isn't a bito backup survives).
 */
internal fun staleWriteTargets(
    names: List<String>,
    fileName: String,
): List<String> =
    names.filter { name ->
        name == fileName ||
            (name.endsWith(".tmp") && BACKUP_NAME_PATTERN.matches(name.removeSuffix(".tmp")))
    }

/** Pure rotation policy, JVM-testable: which existing names to delete, keeping the newest [keep]. */
internal fun rotationVictims(
    names: List<String>,
    keep: Int,
): List<String> =
    names
        .filter { BACKUP_NAME_PATTERN.matches(it) }
        // The name embeds the date, so lexicographic order is chronological order.
        .sortedDescending()
        .drop(keep)
