package com.alvarotc.bito.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class SchemaProbe(val schemaVersion: Int = -1)

object BackupCodec {
    // v2 (M6): + settings.habiSoundsEnabled (defaults to true when absent).
    // v3 (M7): + settings.perfectDayCelebratedDay (-1 when absent) and settings.badgesSeenUntilMillis (0 when absent).
    // M8: reading a v1/v2 file now seals those two markers to the export moment instead of
    // leaving them at their type defaults (mejoras-qa «Pendientes M7» #4) — see migrateLegacyMarkers.
    // v1.0.0: v3 files also carry settings.logSoundEnabled / logHapticEnabled. NO bump — both
    // default to true when absent, which is exactly what an older file means (the switches did not
    // exist, so they were on), so there is nothing for decode to migrate. Bumping would only make
    // every 1.0.0 export unreadable to earlier builds for no gain.
    const val SCHEMA_VERSION = 3

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }
    private val probeJson = Json { ignoreUnknownKeys = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        // Probe the version first so "newer app" reads as such, not as corruption.
        val version =
            runCatching { probeJson.decodeFromString(SchemaProbe.serializer(), text).schemaVersion }
                .getOrElse { throw BackupFormatException("Not a Bito backup", it) }
        if (version > SCHEMA_VERSION) throw BackupFormatException("Backup schema $version is newer than supported $SCHEMA_VERSION")
        if (version < 1) throw BackupFormatException("Missing schemaVersion")
        val file =
            runCatching { json.decodeFromString(BackupFile.serializer(), text) }
                .getOrElse { throw BackupFormatException("Corrupted backup file", it) }
        return if (version < 3) migrateLegacyMarkers(file) else file
    }

    /**
     * v1/v2 files predate the celebration markers, so they decode with the type defaults
     * (-1 / 0L) — which would make the app celebrate every historical badge and perfect day at
     * once on first open after a restore. Sealing the markers to the export moment means nothing
     * OLDER than the backup gets replayed (mejoras-qa «Pendientes M7» #4).
     */
    private fun migrateLegacyMarkers(file: BackupFile): BackupFile {
        val badgesSeenUntilMillis = runCatching { Instant.parse(file.exportedAt).toEpochMilli() }.getOrDefault(0L)
        val perfectDayCelebratedDay = file.daySeals.maxOfOrNull { it.logicalDay } ?: -1
        return file.copy(
            settings =
                file.settings.copy(
                    badgesSeenUntilMillis = badgesSeenUntilMillis,
                    perfectDayCelebratedDay = perfectDayCelebratedDay,
                ),
        )
    }
}
