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

    /**
     * Una base v3 de verdad, la de la 1.3.0: la v2 de [seedVersionTwo], la tabla de respiración con
     * el DDL exacto de MIGRATION_2_3 y una fila del catálogo anterior con un eje que ya no existe
     * (PATTERN): si sobreviviera, Room no podría leerla.
     */
    private fun seedVersionThree(context: Context) {
        seedVersionTwo(context)
        val db = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `breathing_sessions` (`id` TEXT NOT NULL, `mode` TEXT NOT NULL, " +
                "`startedAtMillis` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, " +
                "`completed` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("INSERT INTO customization_items VALUES ('pattern-motas', 'PATTERN', 1000, 1)")
        db.execSQL("INSERT INTO badges VALUES ('streak-7', 1000)")
        db.execSQL("PRAGMA user_version = 3")
        db.close()
    }

    /**
     * Una base v4 de verdad: el esquema 4.json entero (las doce tablas y sus indices, via
     * [runSchemaSetup]) con un habito, una entrada y una tarea, y `PRAGMA user_version = 4` — sin
     * el, Room tomaria el camino de onCreate y MIGRATION_4_5 no se ejecutaria nunca.
     */
    private fun seedVersionFour(context: Context) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        runSchemaSetup(db, schemaVersion = 4)
        db.execSQL(
            "INSERT INTO habits VALUES ('h1', 'Agua', 'COUNT', 'DAY', 'AT_LEAST', 8, 'vasos', " +
                "'COUNTER', 1, NULL, NULL, NULL, 'ACTIVE', 1000, 20000, NULL, NULL, 0)",
        )
        db.execSQL("INSERT INTO entries VALUES ('e1', 'h1', 20000, 3, 1000)")
        db.execSQL("INSERT INTO tasks VALUES ('t1', 'Llamar al banco', NULL, 'NONE', NULL, 'OPEN', 1000, 20000, NULL, NULL)")
        db.execSQL("PRAGMA user_version = 4")
        assertEquals(4, db.version)
        db.close()
    }

    /** Una base v5 de verdad (5.json entero) con un habito y una tarea, y `PRAGMA user_version = 5`. */
    private fun seedVersionFive(context: Context) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        runSchemaSetup(db, schemaVersion = 5)
        db.execSQL(
            "INSERT INTO habits VALUES ('h1', 'Agua', 'COUNT', 'DAY', 'AT_LEAST', 8, 'vasos', " +
                "'COUNTER', 1, NULL, NULL, NULL, 'ACTIVE', 1000, 20000, NULL, NULL, 0)",
        )
        db.execSQL("INSERT INTO entries VALUES ('e1', 'h1', 20000, 3, 1000)")
        db.execSQL("INSERT INTO tasks VALUES ('t1', 'Llamar al banco', NULL, 'NONE', NULL, 'OPEN', 1000, 20000, NULL, NULL)")
        db.execSQL("INSERT INTO lo_gordo VALUES (20000, 0, 'TAREA', 't1', 1000)")
        db.execSQL("PRAGMA user_version = 5")
        assertEquals(5, db.version)
        db.close()
    }

    private fun openMigrated(context: Context): BitoDatabase =
        Room.databaseBuilder(context, BitoDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Test
    fun `migrating a v1 database keeps habits, entries and the ledger intact`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionOne(context)

        val db =
            Room.databaseBuilder(context, BitoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
        try {
            runBlocking {
                assertNotNull("el habito de la v1 sobrevive", db.habitDao().byId("h1"))
                assertEquals(1, db.entryDao().all().size)
                assertEquals(1, db.pointsLedgerDao().all().size)
                assertEquals(8, db.habitDao().byId("h1")!!.target)
            }
            assertEquals(6, db.openHelper.readableDatabase.version)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
            assertEquals(6, db.openHelper.readableDatabase.version)
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
    fun `a v1 database reaches six in one go`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionOne(context)

        val db = openMigrated(context)
        try {
            runBlocking {
                assertNotNull(db.habitDao().byId("h1"))
                assertTrue(db.taskDao().all().isEmpty())
                assertTrue(db.breathingSessionDao().all().isEmpty())
            }
            assertEquals(6, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `migrating from three to four empties only the old catalog`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionThree(context)

        val db = openMigrated(context)
        try {
            runBlocking {
                assertTrue(db.customizationItemDao().all().isEmpty())
                assertEquals(8, db.habitDao().byId("h1")!!.target)
                assertEquals(1, db.entryDao().all().size)
                assertEquals(1, db.pointsLedgerDao().all().size)
                assertEquals(listOf("streak-7"), db.badgeDao().all().map { it.badgeId })
                assertEquals("Llamar al banco", db.taskDao().byId("t1")!!.title)
            }
            assertEquals(6, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `migrating 4 to 5 keeps every row and creates the five Habi tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionFour(context)

        val db = openMigrated(context)
        try {
            runBlocking {
                assertEquals(1, db.habitDao().all().size)
                assertEquals(1, db.entryDao().all().size)
                assertEquals("Llamar al banco", db.taskDao().byId("t1")!!.title)
                db.hablaDao().insertarEvento(HabiMemoriaEntity("e1", "ANIMO", 20679, null, 2, 1L))
                assertEquals(1, db.hablaDao().todosEventos().size)
            }
            val tablas =
                db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
            assertTrue(
                tablas.containsAll(
                    listOf("habi_memoria", "lo_gordo", "aviso_aceptado", "propuesta_vigente", "habla_usada"),
                ),
            )
            assertEquals(6, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `migration 5 to 6 creates sesiones and keeps everything`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedVersionFive(context)

        val db = openMigrated(context)
        try {
            runBlocking {
                assertEquals(1, db.habitDao().all().size)
                assertEquals(1, db.entryDao().all().size)
                assertEquals("Llamar al banco", db.taskDao().byId("t1")!!.title)
                assertEquals(1, db.hablaDao().todoGordo().size)
                assertTrue(db.sesionDao().all().isEmpty())
                val sesion = SesionEntity("s1", "TAREA", "t1", 20_000, 1_000L, 2_500_000L, 25, 1, 4, "HECHA")
                db.sesionDao().insert(sesion)
                assertEquals(listOf(sesion), db.sesionDao().all())
            }
            val indices =
                db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='index'").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
            assertTrue(indices.contains("index_sesiones_objetivo_id_dia"))
            assertEquals(6, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
