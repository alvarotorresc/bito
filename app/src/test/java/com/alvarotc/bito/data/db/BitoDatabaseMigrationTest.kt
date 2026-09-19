package com.alvarotc.bito.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R1, el riesgo numero uno de M10: si el DDL escrito a mano no coincide con el que genera Room,
 * la app NO ABRE para quien ya tiene la 1.0.0. Esta prueba siembra una base v1 de verdad (las
 * nueve tablas con datos), la abre con la migracion enganchada y comprueba que las filas siguen
 * ahi y que las dos tablas nuevas existen.
 *
 * `PRAGMA user_version = 1` no es decorativo: sin el, Room toma el camino de onCreate en vez del
 * de onUpgrade, los CREATE TABLE IF NOT EXISTS funcionan igual y el test pasaria sin haber
 * ejecutado la migracion ni una vez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BitoDatabaseMigrationTest {
    private val name = "migration-test.db"

    /**
     * `onValidateSchema` de Room comprueba TODAS las entidades tras migrar, no solo las que este
     * test lee. Sembrar de menos (p. ej. solo `habits`, `entries` y `points_ledger`) haria fallar
     * el test por tabla ausente con o sin `MIGRATION_1_2` enganchada, y la prueba nunca llegaria
     * a comprobar el DDL de la migracion. Por eso las nueve tablas de la v1 se crean aqui, con el
     * DDL EXACTO (tablas e indices) del `1.json` que genera Room.
     */
    private fun seedVersionOne(context: Context) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)

        // habits
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `metric` TEXT NOT NULL, " +
                "`period` TEXT NOT NULL, `direction` TEXT NOT NULL, `target` INTEGER NOT NULL, `unit` TEXT, `logMode` " +
                "TEXT NOT NULL, `step` INTEGER NOT NULL, `timeBucket` TEXT, `timeOfDayMinutes` INTEGER, " +
                "`reminderMinutes` INTEGER, `status` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                "`createdOnDay` INTEGER NOT NULL, `archivedAtMillis` INTEGER, `archivedOnDay` INTEGER, `sortOrder` " +
                "INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )

        // target_changes
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `target_changes` (`habitId` TEXT NOT NULL, `effectiveFromDay` INTEGER NOT " +
                "NULL, `target` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `effectiveFromDay`), FOREIGN KEY(`habitId`) " +
                "REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_target_changes_habitId` ON `target_changes` (`habitId`)",
        )

        // pause_intervals
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pause_intervals` (`habitId` TEXT NOT NULL, `startDay` INTEGER NOT NULL, " +
                "`endDay` INTEGER, `note` TEXT, PRIMARY KEY(`habitId`, `startDay`), FOREIGN KEY(`habitId`) REFERENCES " +
                "`habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pause_intervals_habitId` ON `pause_intervals` (`habitId`)",
        )

        // entries
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `entries` (`id` TEXT NOT NULL, `habitId` TEXT NOT NULL, `logicalDay` INTEGER " +
                "NOT NULL, `value` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN " +
                "KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_entries_habitId_logicalDay` ON `entries` (`habitId`, `logicalDay`)",
        )

        // day_seals
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `day_seals` (`logicalDay` INTEGER NOT NULL, `sealedAtMillis` INTEGER NOT " +
                "NULL, PRIMARY KEY(`logicalDay`))",
        )

        // freezer_uses
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `freezer_uses` (`id` TEXT NOT NULL, `habitId` TEXT NOT NULL, `protectedDay` " +
                "INTEGER NOT NULL, `usedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`habitId`) " +
                "REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_freezer_uses_habitId_protectedDay` ON `freezer_uses` " +
                "(`habitId`, `protectedDay`)",
        )

        // points_ledger
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `points_ledger` (`id` TEXT NOT NULL, `delta` INTEGER NOT NULL, `reason` TEXT " +
                "NOT NULL, `refId` TEXT, `logicalDay` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, PRIMARY " +
                "KEY(`id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_points_ledger_reason_refId` ON `points_ledger` (`reason`, " +
                "`refId`)",
        )

        // badges
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `badges` (`badgeId` TEXT NOT NULL, `unlockedAtMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`badgeId`))",
        )

        // customization_items
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `customization_items` (`itemId` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "`acquiredAtMillis` INTEGER NOT NULL, `equipped` INTEGER NOT NULL, PRIMARY KEY(`itemId`))",
        )

        db.execSQL(
            "INSERT INTO habits VALUES ('h1', 'Agua', 'COUNT', 'DAY', 'AT_LEAST', 8, 'vasos', " +
                "'COUNTER', 1, NULL, NULL, NULL, 'ACTIVE', 1000, 20000, NULL, NULL, 0)",
        )
        db.execSQL("INSERT INTO entries VALUES ('e1', 'h1', 20000, 3, 1000)")
        db.execSQL("INSERT INTO points_ledger VALUES ('p1', 1, 'HABIT_DONE', 'h1:20000', 20000, 1000)")
        // Sin esto, Room llama a onCreate y la migracion no se ejecuta nunca.
        db.execSQL("PRAGMA user_version = 1")
        db.close()
    }

    @Test
    fun `migrating from one to two keeps habits, entries and the ledger intact`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionOne(context)

        val db =
            Room.databaseBuilder(context, BitoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
        try {
            runBlocking {
                assertNotNull("el habito de la v1 sobrevive", db.habitDao().byId("h1"))
                assertEquals(1, db.entryDao().all().size)
                assertEquals(1, db.pointsLedgerDao().all().size)
                assertEquals(8, db.habitDao().byId("h1")!!.target)
            }
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the new tables exist and are empty after migrating`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionOne(context)

        val db =
            Room.databaseBuilder(context, BitoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
        try {
            runBlocking {
                assertTrue(db.taskDao().all().isEmpty())
                assertTrue(db.taskEventDao().all().isEmpty())
                // Y se puede escribir en ellas: el DDL no es solo una tabla con el nombre correcto.
                db.taskDao().upsert(
                    TaskEntity(
                        id = "t1",
                        title = "Llamar",
                        firstStep = null,
                        dueKind = DueKind.NONE,
                        dueDay = null,
                        status = TaskStatus.OPEN,
                        createdAtMillis = 1_000L,
                        createdOnDay = 20_000,
                        doneAtMillis = null,
                        doneOnDay = null,
                    ),
                )
                assertEquals(1, db.taskDao().all().size)
            }
        } finally {
            db.close()
        }
    }
}
