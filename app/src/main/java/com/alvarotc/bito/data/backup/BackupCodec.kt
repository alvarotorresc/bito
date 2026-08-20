package com.alvarotc.bito.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class SchemaProbe(val schemaVersion: Int = -1)

object BackupCodec {
    // v2 (M6): + settings.habiSoundsEnabled (defaults to true when absent).
    // v3 (M7): + settings.perfectDayCelebratedDay (-1 when absent) and settings.badgesSeenUntilMillis (0 when absent).
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
        // Version migrations hook in here when SCHEMA_VERSION grows.
        return runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw BackupFormatException("Corrupted backup file", it) }
    }
}
