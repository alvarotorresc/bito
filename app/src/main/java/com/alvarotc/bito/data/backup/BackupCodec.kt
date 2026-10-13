package com.alvarotc.bito.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class SchemaProbe(val schemaVersion: Int = -1)

object BackupCodec {
    // v5 (pieza 2): las claves nuevas de Ajustes (sonido, volúmenes, No molestar, pomodoro, avisos de
    // Habi, sonido de fondo y reducir animaciones). Sin compatibilidad hacia atrás: un fichero de otra
    // versión se rechaza entero, como no válido, antes de tocar nada.
    // v4 (Bito 2, pieza 1): catálogo nuevo de Habi y settings.gardenWaterHighMark. Las versiones 1 a 3
    // y sus migraciones de marcadores ya no existen.
    const val SCHEMA_VERSION = 5

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }
    private val probeJson = Json { ignoreUnknownKeys = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        // Primero la versión, para que «de una app más nueva» no se lea como fichero dañado.
        val version =
            runCatching { probeJson.decodeFromString(SchemaProbe.serializer(), text).schemaVersion }
                .getOrElse { throw BackupFormatException("Not a Bito backup", it) }
        if (version > SCHEMA_VERSION) throw BackupFormatException("Backup schema $version is newer than supported $SCHEMA_VERSION")
        if (version < SCHEMA_VERSION) throw BackupFormatException("Backup schema $version is older than supported $SCHEMA_VERSION")
        return runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw BackupFormatException("Corrupted backup file", it) }
    }
}
