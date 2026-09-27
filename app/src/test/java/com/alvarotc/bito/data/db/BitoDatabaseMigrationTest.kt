package com.alvarotc.bito.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
     * Ejecuta, contra [db], el DDL de las entidades y sus indices tal como los declara
     * `app/schemas/com.alvarotc.bito.data.db.BitoDatabase/$schemaVersion.json` (el `createSql` de
     * cada `entities[]`, con `${'$'}{TABLE_NAME}` resuelto al nombre real) mas los `setupQueries`
     * del propio esquema (el `room_master_table` que Room usa para su identity hash). Leer el DDL
     * del esquema en vez de tenerlo copiado a mano en el test es lo que garantiza que esta base v1
     * de partida es byte a byte la que Room generaria — sin esto, un DDL copiado a mano podria
     * desincronizarse del esquema real sin que ningun test lo notase.
     */
    private fun runSchemaSetup(
        db: SQLiteDatabase,
        schemaVersion: Int,
    ) {
        val schemaFile = File("schemas/com.alvarotc.bito.data.db.BitoDatabase/$schemaVersion.json")
        val database = JSONObject(schemaFile.readText()).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            if (entity.has("indices")) {
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName))
                }
            }
        }
        val setupQueries = database.getJSONArray("setupQueries")
        for (i in 0 until setupQueries.length()) {
            db.execSQL(setupQueries.getString(i))
        }
    }

    /**
     * `onValidateSchema` de Room comprueba TODAS las entidades tras migrar, no solo las que este
     * test lee. Sembrar de menos (p. ej. solo `habits`, `entries` y `points_ledger`) haria fallar
     * el test por tabla ausente con o sin `MIGRATION_1_2` enganchada, y la prueba nunca llegaria
     * a comprobar el DDL de la migracion. Por eso las nueve tablas de la v1 se crean aqui, va
     * [runSchemaSetup] contra el `1.json` real.
     */
    private fun seedVersionOne(context: Context) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)

        runSchemaSetup(db, schemaVersion = 1)

        db.execSQL(
            "INSERT INTO habits VALUES ('h1', 'Agua', 'COUNT', 'DAY', 'AT_LEAST', 8, 'vasos', " +
                "'COUNTER', 1, NULL, NULL, NULL, 'ACTIVE', 1000, 20000, NULL, NULL, 0)",
        )
        db.execSQL("INSERT INTO entries VALUES ('e1', 'h1', 20000, 3, 1000)")
        db.execSQL("INSERT INTO points_ledger VALUES ('p1', 1, 'HABIT_DONE', 'h1:20000', 20000, 1000)")
        // Sin esto, Room llama a onCreate y la migracion no se ejecuta nunca.
        db.execSQL("PRAGMA user_version = 1")
        assertEquals("el PRAGMA deja la base sembrada en la version 1 antes de abrirla con Room", 1, db.version)
        db.close()
    }

    /**
     * Una base v2 de verdad, la que tiene quien usa la 1.1.0: las nueve tablas de la v1 (via
     * [seedVersionOne]) mas las dos de tareas con el DDL EXACTO del `2.json`, una tarea con su
     * evento, y `PRAGMA user_version = 2` — sin el, Room tomaria el camino de onCreate y el test
     * pasaria sin ejecutar MIGRATION_2_3 ni una vez.
     */
    private fun seedVersionTwo(context: Context) {
        seedVersionOne(context)
        val db = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`firstStep` TEXT, `dueKind` TEXT NOT NULL, `dueDay` INTEGER, `status` TEXT NOT NULL, " +
                "`createdAtMillis` INTEGER NOT NULL, `createdOnDay` INTEGER NOT NULL, " +
                "`doneAtMillis` INTEGER, `doneOnDay` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_events` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `logicalDay` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_task_events_taskId_logicalDay` " +
                "ON `task_events` (`taskId`, `logicalDay`)",
        )
        db.execSQL("INSERT INTO tasks VALUES ('t1', 'Llamar al banco', NULL, 'NONE', NULL, 'OPEN', 1000, 20000, NULL, NULL)")
        db.execSQL("INSERT INTO task_events VALUES ('ev1', 't1', 'POSTPONED', 20000, 1000)")
        db.execSQL("PRAGMA user_version = 2")
        db.close()
    }

    private fun openMigrated(context: Context): BitoDatabase =
        Room.databaseBuilder(context, BitoDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Test
    fun `migrating a v1 database keeps habits, entries and the ledger intact`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionOne(context)

        val db =
            Room.databaseBuilder(context, BitoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        try {
            runBlocking {
                assertNotNull("el habito de la v1 sobrevive", db.habitDao().byId("h1"))
                assertEquals(1, db.entryDao().all().size)
                assertEquals(1, db.pointsLedgerDao().all().size)
                assertEquals(8, db.habitDao().byId("h1")!!.target)
            }
            assertEquals(3, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the task tables exist and are empty after migrating a v1 database`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionOne(context)

        val db =
            Room.databaseBuilder(context, BitoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

    @Test
    fun `migrating from two to three keeps habits, tasks and the ledger intact`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionTwo(context)

        val db = openMigrated(context)
        try {
            runBlocking {
                assertEquals(8, db.habitDao().byId("h1")!!.target)
                assertEquals(1, db.entryDao().all().size)
                assertEquals(1, db.pointsLedgerDao().all().size)
                assertEquals("Llamar al banco", db.taskDao().byId("t1")!!.title)
                assertEquals(listOf("ev1"), db.taskEventDao().all().map { it.id })
            }
            assertEquals(3, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the breathing table exists, is empty and writable after migrating from two`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionTwo(context)

        val db = openMigrated(context)
        try {
            runBlocking {
                assertTrue(db.breathingSessionDao().all().isEmpty())
                val session = BreathingSessionEntity("b1", BreathingMode.SLEEP, 5_000L, 114, true)
                db.breathingSessionDao().insert(session)
                assertEquals(listOf(session), db.breathingSessionDao().all())
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `a v1 database reaches three in one go`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionOne(context)

        val db = openMigrated(context)
        try {
            runBlocking {
                assertNotNull(db.habitDao().byId("h1"))
                assertTrue(db.taskDao().all().isEmpty())
                assertTrue(db.breathingSessionDao().all().isEmpty())
            }
            assertEquals(3, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
