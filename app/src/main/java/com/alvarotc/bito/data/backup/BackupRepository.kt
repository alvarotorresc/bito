package com.alvarotc.bito.data.backup

import androidx.room.withTransaction
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant

class MissingKeyException : Exception("Encryption enabled but no stored key")

/**
 * COMPLETE manual export/import (tech doc §5.4: the file holds 100% of the
 * state from the first usable build). Import validates BEFORE wiping and
 * replaces everything in one transaction — never a merge.
 */
class BackupRepository(
    private val db: BitoDatabase,
    private val settings: SettingsRepository,
    private val keyStore: BackupKeyStore,
    private val appVersion: String,
    /** Cancela las alarmas de los avisos aceptados que la restauracion borra. La cablea el contenedor. */
    private val cancelarAvisos: suspend (List<String>) -> Unit = {},
) {
    suspend fun exportJson(nowMillis: Long): String = exportJson(nowMillis, settings.settings.first())

    /** Same payload as [exportJson], encrypted with the stored key when settings ask for it. */
    suspend fun exportBytes(nowMillis: Long): ByteArray {
        val prefs = settings.settings.first()
        val json = exportJson(nowMillis, prefs)
        if (!prefs.backupEncryption) return json.toByteArray(Charsets.UTF_8)
        val derived = keyStore.load() ?: throw MissingKeyException()
        return BackupCrypto.encrypt(json, derived)
    }

    /** [exportJson]'s actual work, taking the already-read [prefs] so a caller that also needs a settings field (e.g. [exportBytes]'s encryption check) doesn't read the flow twice. */
    private suspend fun exportJson(
        nowMillis: Long,
        prefs: Settings,
    ): String {
        val file =
            db.withTransaction {
                BackupFile(
                    schemaVersion = BackupCodec.SCHEMA_VERSION,
                    appVersion = appVersion,
                    exportedAt = Instant.ofEpochMilli(nowMillis).toString(),
                    // SQLite gives no ordering guarantee on these SELECTs; sort by a stable
                    // key here so re-exporting the same state is byte-identical.
                    habits = db.habitDao().all().sortedWith(compareBy({ it.sortOrder }, { it.id })).map { it.toBackup() },
                    targetChanges =
                        db.targetChangeDao().all()
                            .sortedWith(compareBy({ it.habitId }, { it.effectiveFromDay }))
                            .map { it.toBackup() },
                    pauseIntervals =
                        db.pauseIntervalDao().all()
                            .sortedWith(compareBy({ it.habitId }, { it.startDay }))
                            .map { it.toBackup() },
                    entries = db.entryDao().all().sortedBy { it.id }.map { it.toBackup() },
                    daySeals = db.daySealDao().all().sortedBy { it.logicalDay }.map { it.toBackup() },
                    pointsLedger = db.pointsLedgerDao().all().sortedBy { it.id }.map { it.toBackup() },
                    freezerUses = db.freezerUseDao().all().sortedBy { it.id }.map { it.toBackup() },
                    badges = db.badgeDao().all().sortedBy { it.badgeId }.map { it.toBackup() },
                    customizationItems = db.customizationItemDao().all().sortedBy { it.itemId }.map { it.toBackup() },
                    settings = prefs.toBackup(),
                    // Ordenadas por id para que reexportar el mismo estado de un fichero identico.
                    tasks = db.taskDao().all().sortedBy { it.id }.map { it.toBackup() },
                    taskEvents = db.taskEventDao().all().sortedBy { it.id }.map { it.toBackup() },
                    breathingSessions = db.breathingSessionDao().all().sortedBy { it.id }.map { it.toBackup() },
                    habiMemoria = db.hablaDao().todosEventos().sortedBy { it.id }.map { it.toBackup() },
                    loGordo = db.hablaDao().todoGordo().sortedWith(compareBy({ it.dia }, { it.posicion })).map { it.toBackup() },
                )
            }
        return BackupCodec.encode(file)
    }

    fun isEncrypted(bytes: ByteArray): Boolean = BackupCrypto.isEncrypted(bytes)

    fun decryptToJson(
        bytes: ByteArray,
        passphrase: CharArray,
    ): String = BackupCrypto.decrypt(bytes, passphrase)

    fun preview(text: String): BackupPreview = BackupCodec.decode(text).toPreview()

    suspend fun import(text: String) {
        val file = BackupCodec.decode(text) // validate before touching anything
        // Las alarmas de los avisos aceptados se cancelan con los ids leidos antes de borrar las filas.
        val pendientes = db.hablaDao().avisosPendientes().map { it.id }
        if (pendientes.isNotEmpty()) cancelarAvisos(pendientes)
        db.withTransaction {
            // Children before parents on delete; parents before children on insert.
            db.entryDao().deleteAll()
            db.targetChangeDao().deleteAll()
            db.pauseIntervalDao().deleteAll()
            db.freezerUseDao().deleteAll()
            db.daySealDao().deleteAll()
            db.pointsLedgerDao().deleteAll()
            db.badgeDao().deleteAll()
            db.customizationItemDao().deleteAll()
            db.taskEventDao().deleteAll()
            db.habitDao().deleteAll()
            db.taskDao().deleteAll()
            db.breathingSessionDao().deleteAll()
            // La memoria y lo gordo se reemplazan; lo demas de Habi solo vale para hoy y se vacia.
            db.hablaDao().reemplazarMemoriaYGordo(file.habiMemoria.map { it.toEntity() }, file.loGordo.map { it.toEntity() })
            db.hablaDao().vaciarAvisos()
            db.hablaDao().vaciarVigentes()
            db.hablaDao().vaciarUsos()
            file.habits.forEach { db.habitDao().upsert(it.toEntity()) }
            file.targetChanges.forEach { db.targetChangeDao().upsert(it.toEntity()) }
            file.pauseIntervals.forEach { db.pauseIntervalDao().upsert(it.toEntity()) }
            file.entries.forEach { db.entryDao().insert(it.toEntity()) }
            file.daySeals.forEach { db.daySealDao().insert(it.toEntity()) }
            file.pointsLedger.forEach { db.pointsLedgerDao().insert(it.toEntity()) }
            file.freezerUses.forEach { db.freezerUseDao().insert(it.toEntity()) }
            file.badges.forEach { db.badgeDao().insert(it.toEntity()) }
            file.customizationItems.forEach { db.customizationItemDao().upsert(it.toEntity()) }
            file.tasks.forEach { db.taskDao().upsert(it.toEntity()) }
            file.taskEvents.forEach { db.taskEventDao().insert(it.toEntity()) }
            file.breathingSessions.forEach { db.breathingSessionDao().insert(it.toEntity()) }
        }
        settings.update { file.settings.toSettings() }
    }
}
