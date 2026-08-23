package com.alvarotc.bito.data.backup

import androidx.room.withTransaction
import com.alvarotc.bito.data.db.BitoDatabase
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
) {
    suspend fun exportJson(nowMillis: Long): String {
        val prefs = settings.settings.first()
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
                )
            }
        return BackupCodec.encode(file)
    }

    /** Same payload as [exportJson], encrypted with the stored key when settings ask for it. */
    suspend fun exportBytes(nowMillis: Long): ByteArray {
        val json = exportJson(nowMillis)
        val prefs = settings.settings.first()
        if (!prefs.backupEncryption) return json.toByteArray(Charsets.UTF_8)
        val derived = keyStore.load() ?: throw MissingKeyException()
        return BackupCrypto.encrypt(json, derived)
    }

    fun isEncrypted(bytes: ByteArray): Boolean = BackupCrypto.isEncrypted(bytes)

    fun decryptToJson(
        bytes: ByteArray,
        passphrase: CharArray,
    ): String = BackupCrypto.decrypt(bytes, passphrase)

    fun preview(text: String): BackupPreview = BackupCodec.decode(text).toPreview()

    suspend fun import(text: String) {
        val file = BackupCodec.decode(text) // validate before touching anything
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
            db.habitDao().deleteAll()
            file.habits.forEach { db.habitDao().upsert(it.toEntity()) }
            file.targetChanges.forEach { db.targetChangeDao().upsert(it.toEntity()) }
            file.pauseIntervals.forEach { db.pauseIntervalDao().upsert(it.toEntity()) }
            file.entries.forEach { db.entryDao().insert(it.toEntity()) }
            file.daySeals.forEach { db.daySealDao().insert(it.toEntity()) }
            file.pointsLedger.forEach { db.pointsLedgerDao().insert(it.toEntity()) }
            file.freezerUses.forEach { db.freezerUseDao().insert(it.toEntity()) }
            file.badges.forEach { db.badgeDao().insert(it.toEntity()) }
            file.customizationItems.forEach { db.customizationItemDao().upsert(it.toEntity()) }
        }
        settings.update { file.settings.toSettings() }
    }
}
