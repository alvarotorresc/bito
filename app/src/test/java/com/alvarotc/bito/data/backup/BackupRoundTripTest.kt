package com.alvarotc.bito.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.breathingSessionEntity
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.daySealEntity
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.TimeBucket
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.freezerUseEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pauseIntervalEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.data.targetChangeEntity
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.data.taskEventEntity
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRoundTripTest {
    private lateinit var db: BitoDatabase
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var keyStore: BackupKeyStore
    private lateinit var backup: BackupRepository

    // Small Argon2 cost so tests stay fast — mirrors BackupCryptoTest's fastParams.
    private val fastParams = Argon2Params(memoryKib = 64, iterations = 1, parallelism = 1)
    private val prettyJson = Json { prettyPrint = true }

    private val seededSettings =
        Settings(
            userName = "Álvaro",
            dayCutoffMinutes = 180,
            languageTag = "es",
            globalReminderMinutes = listOf(8 * 60, 22 * 60),
            reviewTimeMinutes = 22 * 60,
            perfectDayCelebration = false,
            personality = Personality.SARGENTO,
            backupFolderUri = "content://tree/backups",
            backupFrequency = BackupFrequency.WEEKLY,
            backupCopies = 3,
            backupEncryption = true,
            onboardingDone = true,
            habiSoundsEnabled = false,
            // Both "Al registrar" switches OFF on purpose: they default to true, so a seed left at
            // the default would let the round-trip assertion pass even if they never travelled in
            // the file at all (which is exactly the bug this pins).
            logSoundEnabled = false,
            logHapticEnabled = false,
            // Apagado a proposito: su default es true, asi que una semilla en el default dejaria
            // pasar el round-trip aunque el campo no viajara en el fichero.
            taskNoticesEnabled = false,
            perfectDayCelebratedDay = 20679,
            badgesSeenUntilMillis = 4321L,
        )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        val store =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
            ) { context.filesDir.resolve("t-${UUID.randomUUID()}.preferences_pb") }
        settingsRepo = SettingsRepository(store)
        keyStore = BackupKeyStore(context.filesDir.resolve("keystore-${UUID.randomUUID()}").apply { mkdirs() })
        backup = BackupRepository(db, settingsRepo, keyStore, "0.3.0-test")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedEverything() {
        // Habit with every nullable set.
        db.habitDao().upsert(
            habitEntity(
                id = "h1",
                unit = "vasos",
                timeBucket = TimeBucket.MORNING,
                reminderMinutes = 30,
                status = HabitStatus.ARCHIVED,
                archivedAtMillis = 5_000L,
                archivedOnDay = DAY_ZERO + 20,
                sortOrder = 1,
            ),
        )
        // Habit with all nullables null.
        db.habitDao().upsert(
            habitEntity(
                id = "h2",
                unit = null,
                timeBucket = null,
                timeOfDayMinutes = null,
                reminderMinutes = null,
                status = HabitStatus.ACTIVE,
                archivedAtMillis = null,
                archivedOnDay = null,
                sortOrder = 0,
            ),
        )

        db.targetChangeDao().upsert(targetChangeEntity(habitId = "h1", effectiveFromDay = DAY_ZERO + 5, target = 10))
        db.targetChangeDao().upsert(targetChangeEntity(habitId = "h1", effectiveFromDay = DAY_ZERO, target = 8))
        db.targetChangeDao().upsert(targetChangeEntity(habitId = "h2", effectiveFromDay = DAY_ZERO, target = 3))

        // One closed pause with a note, one open pause without.
        db.pauseIntervalDao().upsert(
            pauseIntervalEntity(habitId = "h1", startDay = DAY_ZERO, endDay = DAY_ZERO + 3, note = "viaje"),
        )
        db.pauseIntervalDao().upsert(
            pauseIntervalEntity(habitId = "h1", startDay = DAY_ZERO + 10, endDay = null, note = null),
        )

        db.entryDao().insert(entryEntity(id = "e2", habitId = "h1", logicalDay = DAY_ZERO + 1, value = 2))
        db.entryDao().insert(entryEntity(id = "e1", habitId = "h2", logicalDay = DAY_ZERO, value = 1))

        db.daySealDao().insert(daySealEntity(logicalDay = DAY_ZERO + 1, sealedAtMillis = 2_000L))
        db.daySealDao().insert(daySealEntity(logicalDay = DAY_ZERO, sealedAtMillis = 1_000L))

        // Ledger rows: one with refId, one without.
        db.pointsLedgerDao().insert(
            pointsLedgerEntity(id = "p1", reason = PointsReason.HABIT_DONE, refId = "h1:$DAY_ZERO", logicalDay = DAY_ZERO),
        )
        db.pointsLedgerDao().insert(
            pointsLedgerEntity(id = "p2", reason = PointsReason.BUY_ITEM, refId = null, logicalDay = DAY_ZERO + 1),
        )

        db.freezerUseDao().insert(freezerUseEntity(id = "f1", habitId = "h1", protectedDay = DAY_ZERO))
        db.badgeDao().insert(BadgeEntity("first-week", 6_000L))
        db.customizationItemDao().upsert(
            customizationItemEntity(itemId = "hat-basic", category = CustomizationCategory.UPPER, equipped = true),
        )

        db.breathingSessionDao().insert(
            breathingSessionEntity(
                id = "b2",
                mode = BreathingMode.FOCUS,
                startedAtMillis = 8_000L,
                durationSeconds = 37,
                completed = false,
            ),
        )
        db.breathingSessionDao().insert(
            breathingSessionEntity(
                id = "b1",
                mode = BreathingMode.SLEEP,
                startedAtMillis = 3_000L,
                durationSeconds = 114,
                completed = true,
            ),
        )

        settingsRepo.update { seededSettings }
    }

    private suspend fun snapshotAllTables(): List<List<Any>> =
        listOf(
            db.habitDao().all().sortedBy { it.toString() },
            db.targetChangeDao().all().sortedBy { it.toString() },
            db.pauseIntervalDao().all().sortedBy { it.toString() },
            db.entryDao().all().sortedBy { it.toString() },
            db.daySealDao().all().sortedBy { it.toString() },
            db.pointsLedgerDao().all().sortedBy { it.toString() },
            db.freezerUseDao().all().sortedBy { it.toString() },
            db.badgeDao().all().sortedBy { it.toString() },
            db.customizationItemDao().all().sortedBy { it.toString() },
            db.breathingSessionDao().all().sortedBy { it.toString() },
        )

    @Test
    fun `export - wipe - import restores the exact same state`() =
        runTest {
            seedEverything()
            val exported = backup.exportJson(nowMillis = 1_000L)
            val before = snapshotAllTables()

            // Pollute: prove import REPLACES, not merges.
            db.habitDao().upsert(habitEntity(id = "intruder"))
            settingsRepo.update { it.copy(userName = "Nadie") }

            backup.import(exported)

            assertEquals(before, snapshotAllTables())
            assertEquals(seededSettings, settingsRepo.settings.first())
            assertEquals(exported, backup.exportJson(nowMillis = 1_000L))
        }

    @Test
    fun `import of an invalid file changes nothing`() =
        runTest {
            seedEverything()
            val before = snapshotAllTables()
            assertFailsWith<BackupFormatException> { backup.import("garbage") }
            assertEquals(before, snapshotAllTables())
        }

    @Test
    fun `preview reports the file's own counts`() =
        runTest {
            seedEverything()
            val preview = backup.preview(backup.exportJson(nowMillis = 1_000L))
            assertEquals(2, preview.habits)
            assertEquals("0.3.0-test", preview.appVersion)
        }

    // Neither seeded habit in seedEverything() ever carries a non-null timeOfDayMinutes
    // (the entity invariant forbids setting it alongside timeBucket, and the round-trip's
    // two-habit shape is pinned by the `preview.habits == 2` assertion above). Cover the
    // field directly on the mapper instead, both directions.
    @Test
    fun `habit mapper round-trips a non-null timeOfDayMinutes`() {
        val entity = habitEntity(id = "h3", timeBucket = null, timeOfDayMinutes = 450, reminderMinutes = 540)
        val backupHabit = entity.toBackup()
        assertEquals(450, backupHabit.timeOfDayMinutes)
        assertEquals(entity, backupHabit.toEntity())
    }

    @Test
    fun `local auto backup state does not travel in exports`() =
        runTest {
            seedEverything()
            settingsRepo.update { it.copy(lastAutoBackupAtMillis = 1234567L, lastAutoBackupError = null) }
            val exported = backup.exportJson(nowMillis = 1_000L)
            // Device-local backup status should not appear in the export.
            assert(!exported.contains("lastAutoBackup"))
            assert(!exported.contains("last_auto_backup"))
        }

    @Test
    fun `exportBytes is plain utf-8 json when encryption is off`() =
        runTest {
            seedEverything() // seededSettings has backupEncryption = true
            settingsRepo.update { it.copy(backupEncryption = false) }

            val json = backup.exportJson(nowMillis = 1_000L)
            val bytes = backup.exportBytes(nowMillis = 1_000L)

            assertEquals(json, bytes.toString(Charsets.UTF_8))
        }

    @Test
    fun `exportBytes round-trips through decryptToJson when encryption is on`() =
        runTest {
            seedEverything() // seededSettings has backupEncryption = true
            val passphrase = "correct horse battery".toCharArray()
            keyStore.save(BackupCrypto.deriveKey(passphrase, BackupCrypto.newSalt(), fastParams))

            val json = backup.exportJson(nowMillis = 1_000L)
            val bytes = backup.exportBytes(nowMillis = 1_000L)

            assertTrue(backup.isEncrypted(bytes))
            assertEquals(json, backup.decryptToJson(bytes, passphrase))
        }

    @Test
    fun `exportBytes throws MissingKeyException when encryption is on with no key`() =
        runTest {
            seedEverything() // seededSettings has backupEncryption = true, no key ever saved
            assertFailsWith<MissingKeyException> { backup.exportBytes(nowMillis = 1_000L) }
        }

    // BackupCodecTest proves the v1/v2 -> v3 marker migration at the codec level (decode alone).
    // These two prove the M8 story end to end through the real repository: BackupRepository.import
    // must actually WRITE the codec's migrated markers into the persisted SettingsRepository, not
    // just decode them and drop them on the floor. seedEverything()'s day seals (DAY_ZERO,
    // DAY_ZERO + 1) and its pre-existing seededSettings markers (perfectDayCelebratedDay = 20679,
    // badgesSeenUntilMillis = 4321L) are both far from the migrated values asserted below, so a
    // repository that silently persisted the FILE's own stale/stripped fields instead of the
    // decoded migration result would fail these assertions, not pass them by coincidence.
    @Test
    fun `importing a v1 backup persists the sealed migration markers into Settings`() =
        runTest {
            seedEverything()
            val exported = backup.exportJson(nowMillis = 5_000L)
            val v1Json =
                exported
                    .replace("\"schemaVersion\": 3", "\"schemaVersion\": 1")
                    .replace(Regex(",?\\s*\"habiSoundsEnabled\":\\s*(true|false)"), "")
                    .replace(Regex(",?\\s*\"perfectDayCelebratedDay\":\\s*-?\\d+"), "")
                    .replace(Regex(",?\\s*\"badgesSeenUntilMillis\":\\s*\\d+"), "")
                    .replace(Regex(",?\\s*\"logSoundEnabled\":\\s*(true|false)"), "")
                    .replace(Regex(",?\\s*\"logHapticEnabled\":\\s*(true|false)"), "")

            backup.import(v1Json)

            val persisted = settingsRepo.settings.first()
            assertEquals(5_000L, persisted.badgesSeenUntilMillis) // sealed to the export moment
            assertEquals(DAY_ZERO + 1, persisted.perfectDayCelebratedDay) // sealed to the last day seal
            assertTrue(persisted.habiSoundsEnabled) // v1 default: sounds always on
            // The "Al registrar" switches postdate every stored backup: absent means the switches
            // did not exist yet, i.e. on. seededSettings has both OFF, so a decoder that carried
            // the file's own (stripped) values over would land on false here, not true.
            assertTrue(persisted.logSoundEnabled)
            assertTrue(persisted.logHapticEnabled)
        }

    @Test
    fun `importing a v2 backup persists the sealed markers while keeping v2's own sounds field`() =
        runTest {
            seedEverything() // seededSettings has habiSoundsEnabled = false
            val exported = backup.exportJson(nowMillis = 6_000L)
            val v2Json =
                exported
                    .replace("\"schemaVersion\": 3", "\"schemaVersion\": 2")
                    .replace(Regex(",?\\s*\"perfectDayCelebratedDay\":\\s*-?\\d+"), "")
                    .replace(Regex(",?\\s*\"badgesSeenUntilMillis\":\\s*\\d+"), "")
                    .replace(Regex(",?\\s*\"logSoundEnabled\":\\s*(true|false)"), "")
                    .replace(Regex(",?\\s*\"logHapticEnabled\":\\s*(true|false)"), "")

            backup.import(v2Json)

            val persisted = settingsRepo.settings.first()
            assertEquals(6_000L, persisted.badgesSeenUntilMillis)
            assertEquals(DAY_ZERO + 1, persisted.perfectDayCelebratedDay)
            // v2 already carries its own sounds field (unlike v1) — it must survive untouched,
            // not get swept into the "always on" v1 default.
            assertEquals(false, persisted.habiSoundsEnabled)
            // v2 predates the "Al registrar" switches just as v1 does: absent, so both come back on.
            assertTrue(persisted.logSoundEnabled)
            assertTrue(persisted.logHapticEnabled)
        }

    @Test
    fun `tasks and their events survive a full round trip`() =
        runTest {
            db.taskDao().upsert(
                taskEntity(
                    id = "t1",
                    title = "Llamar al banco",
                    firstStep = "Buscar el numero",
                    dueKind = DueKind.DATE,
                    dueDay = DAY_ZERO + 3,
                    status = TaskStatus.DONE,
                    doneAtMillis = 7_000L,
                    doneOnDay = DAY_ZERO + 1,
                ),
            )
            db.taskDao().upsert(taskEntity(id = "t2", title = "Papeleo", dueKind = DueKind.NONE, dueDay = null))
            db.taskEventDao().insert(taskEventEntity(id = "e1", taskId = "t2", kind = TaskEventKind.POSTPONED))

            val json = backup.exportJson(nowMillis = 9_000L)
            db.taskEventDao().deleteAll()
            db.taskDao().deleteAll()
            backup.import(json)

            assertEquals(2, db.taskDao().all().size)
            assertEquals("Llamar al banco", db.taskDao().byId("t1")!!.title)
            assertEquals(DAY_ZERO + 1, db.taskDao().byId("t1")!!.doneOnDay)
            assertEquals(listOf("e1"), db.taskEventDao().all().map { it.id })
        }

    @Test
    fun `re-exporting the same state is byte-identical`() =
        runTest {
            db.taskDao().upsert(taskEntity(id = "b"))
            db.taskDao().upsert(taskEntity(id = "a"))
            db.taskEventDao().insert(taskEventEntity(id = "e2", taskId = "a"))
            db.taskEventDao().insert(taskEventEntity(id = "e1", taskId = "b"))

            assertEquals(backup.exportJson(nowMillis = 9_000L), backup.exportJson(nowMillis = 9_000L))
        }

    @Test
    fun `a v3 file written before tasks existed still imports, with empty lists`() =
        runTest {
            // Un fichero de la 1.0.0: mismo schemaVersion 3, sin las dos claves nuevas.
            db.taskDao().upsert(taskEntity(id = "t1"))
            val legacy = backup.exportJson(nowMillis = 9_000L).let(::stripTaskKeys)

            backup.import(legacy)

            assertTrue(db.taskDao().all().isEmpty())
            assertTrue(db.taskEventDao().all().isEmpty())
        }

    /**
     * Simula un fichero escrito antes de que `tasks`/`taskEvents` existieran: quita esas dos
     * claves del objeto raiz operando sobre el arbol de kotlinx.serialization.json, nunca con
     * manipulacion de texto (a diferencia de los `.replace` de version que ya usan los tests de
     * v1/v2 mas arriba, que solo tachan campos SUELTOS de `settings`, no una clave de lista entera
     * del objeto raiz).
     */
    private fun stripTaskKeys(json: String): String {
        val root = Json.parseToJsonElement(json).jsonObject
        val stripped = JsonObject(root.filterKeys { it != "tasks" && it != "taskEvents" })
        return prettyJson.encodeToString(JsonObject.serializer(), stripped)
    }

    @Test
    fun `breathing sessions survive a full round trip`() =
        runTest {
            val sessions =
                listOf(
                    breathingSessionEntity(
                        id = "b1",
                        mode = BreathingMode.CALM,
                        startedAtMillis = 1_000L,
                        durationSeconds = 120,
                        completed = true,
                    ),
                    breathingSessionEntity(
                        id = "b2",
                        mode = BreathingMode.FOCUS,
                        startedAtMillis = 2_000L,
                        durationSeconds = 12,
                        completed = false,
                    ),
                )
            sessions.forEach { db.breathingSessionDao().insert(it) }

            val json = backup.exportJson(nowMillis = 9_000L)
            db.breathingSessionDao().deleteAll()
            backup.import(json)

            assertEquals(sessions, db.breathingSessionDao().all())
        }

    // Un test que solo compara exportJson() contra si mismo no puede fallar por un mapeador o un
    // orden mal hechos: siempre exporta lo mismo del mismo estado. Aqui el segundo export sale de
    // una base de datos DISTINTA, poblada por import() a partir del primer export — si el mapeador
    // de ida y vuelta o el orden de export cambiaran algo, este test lo detecta.
    @Test
    fun `re-exporting breathing sessions is byte-identical`() =
        runTest {
            db.breathingSessionDao().insert(breathingSessionEntity(id = "z", startedAtMillis = 1_000L))
            db.breathingSessionDao().insert(breathingSessionEntity(id = "a", startedAtMillis = 1_000L))

            val exported = backup.exportJson(nowMillis = 9_000L)

            val context = ApplicationProvider.getApplicationContext<Context>()
            val freshDb = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
            try {
                val freshStore =
                    PreferenceDataStoreFactory.create(
                        scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
                    ) { context.filesDir.resolve("t-${UUID.randomUUID()}.preferences_pb") }
                val freshSettings = SettingsRepository(freshStore)
                val freshKeyStore = BackupKeyStore(context.filesDir.resolve("keystore-${UUID.randomUUID()}").apply { mkdirs() })
                val freshBackup = BackupRepository(freshDb, freshSettings, freshKeyStore, "0.3.0-test")

                freshBackup.import(exported)
                val reExported = freshBackup.exportJson(nowMillis = 9_000L)

                assertEquals(exported, reExported)
            } finally {
                freshDb.close()
            }
        }

    @Test
    fun `a v3 file written before breathing existed still imports, with an empty list`() =
        runTest {
            // Un fichero de la 1.1.0: mismo schemaVersion 3, sin la clave breathingSessions.
            db.breathingSessionDao().insert(breathingSessionEntity(id = "b1"))
            val legacy = backup.exportJson(nowMillis = 9_000L).let(::stripBreathingKey)

            backup.import(legacy)

            assertTrue(db.breathingSessionDao().all().isEmpty())
        }

    /** Quita `breathingSessions` del objeto raiz sobre el arbol JSON, como [stripTaskKeys]. */
    private fun stripBreathingKey(json: String): String {
        val root = Json.parseToJsonElement(json).jsonObject
        val stripped = JsonObject(root.filterKeys { it != "breathingSessions" })
        return prettyJson.encodeToString(JsonObject.serializer(), stripped)
    }
}
