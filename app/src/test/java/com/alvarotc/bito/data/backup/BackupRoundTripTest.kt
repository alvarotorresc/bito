package com.alvarotc.bito.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.breathingSessionEntity
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.daySealEntity
import com.alvarotc.bito.data.db.AvisoAceptadoEntity
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.HabiMemoriaEntity
import com.alvarotc.bito.data.db.HablaUsadaEntity
import com.alvarotc.bito.data.db.LoGordoEntity
import com.alvarotc.bito.data.db.PropuestaVigenteEntity
import com.alvarotc.bito.data.db.SesionEntity
import com.alvarotc.bito.data.db.TimeBucket
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.freezerUseEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pauseIntervalEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.data.settings.MarcaLibro
import com.alvarotc.bito.data.settings.SessionBackground
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
import com.alvarotc.bito.domain.sesion.Entrada
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
import kotlin.test.assertNull
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
            habiNightMinutes = 22 * 60,
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
            // Los cuatro de respiracion, todos fuera de su default: si alguno no viajara en el
            // fichero, el round-trip lo delataria.
            breathingReminderEnabled = true,
            breathingReminderTimeMinutes = 6 * 60 + 45,
            breathingMusicEnabled = true,
            breathingLastMode = BreathingMode.FOCUS,
            perfectDayCelebratedDay = 20679,
            badgesSeenUntilMillis = 4321L,
            // Fuera de su default (0.0): si no viajara en el fichero, el ida y vuelta lo delataria.
            gardenWaterHighMark = 37.5,
            // Fuera de su default (RESPIRAR, false): si no viajaran, el ida y vuelta lo delataria.
            sessionEntry = Entrada.DIRECTO,
            sessionDnd = true,
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
            customizationItemEntity(itemId = "hat-basic", category = CustomizationCategory.HEAD, equipped = true),
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

        db.sesionDao().insert(SesionEntity("s2", "LIBRE", null, DAY_ZERO + 1, 9_000L, 9_900_000L, 15, 1, 2, "SIN_RESPUESTA"))
        db.sesionDao().insert(SesionEntity("s1", "TAREA", "t1", DAY_ZERO, 4_000L, 5_500_000L, 25, 2, 4, "HECHA"))

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
            db.sesionDao().all().sortedBy { it.toString() },
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
    fun `a version 3 backup is rejected before anything is wiped`() =
        runTest {
            seedEverything()
            val before = snapshotAllTables()
            val older = backup.exportJson(nowMillis = 1_000L).replace("\"schemaVersion\": 7", "\"schemaVersion\": 3")

            assertFailsWith<BackupFormatException> { backup.import(older) }

            assertEquals(before, snapshotAllTables())
            assertEquals(seededSettings, settingsRepo.settings.first())
        }

    @Test
    fun `a version 4 backup is rejected before anything is wiped`() =
        runTest {
            seedEverything()
            val before = snapshotAllTables()
            val older = backup.exportJson(nowMillis = 1_000L).replace("\"schemaVersion\": 7", "\"schemaVersion\": 4")

            assertFailsWith<BackupFormatException> { backup.import(older) }

            assertEquals(before, snapshotAllTables())
            assertEquals(seededSettings, settingsRepo.settings.first())
        }

    @Test
    fun `a version 6 backup is rejected before anything is wiped`() =
        runTest {
            seedEverything()
            val before = snapshotAllTables()
            val older = backup.exportJson(nowMillis = 1_000L).replace("\"schemaVersion\": 7", "\"schemaVersion\": 6")

            assertFailsWith<BackupFormatException> { backup.import(older) }

            assertEquals(before, snapshotAllTables())
        }

    @Test
    fun `backup round trip keeps sessions and the two new settings`() =
        runTest {
            seedEverything()
            val exported = backup.exportJson(nowMillis = 1_000L)
            assertTrue(exported.contains("\"sessionEntry\": \"DIRECTO\""))
            db.sesionDao().deleteAll()
            settingsRepo.update { it.copy(sessionEntry = Entrada.RESPIRAR, sessionDnd = false) }

            backup.import(exported)

            assertEquals(listOf("s1", "s2"), db.sesionDao().all().map { it.id }.sorted())
            val back = settingsRepo.settings.first()
            assertEquals(Entrada.DIRECTO, back.sessionEntry)
            assertTrue(back.sessionDnd)
            assertEquals(2, backup.preview(exported).sesiones)
        }

    @Test
    fun `restoring calls cerrarSesionViva first`() =
        runTest {
            seedEverything()
            val exported = backup.exportJson(nowMillis = 1_000L)
            var sesionesAlLlamar = -1
            val conHook =
                BackupRepository(
                    db,
                    settingsRepo,
                    keyStore,
                    "0.3.0-test",
                    cerrarSesionViva = { sesionesAlLlamar = db.sesionDao().all().size },
                )

            conHook.import(exported)

            // Todavia estaban las dos filas: el hook corre antes de vaciar nada.
            assertEquals(2, sesionesAlLlamar)
        }

    @Test
    fun `a corrupt file does not call cerrarSesionViva`() =
        runTest {
            var llamado = false
            val conHook = BackupRepository(db, settingsRepo, keyStore, "0.3.0-test", cerrarSesionViva = { llamado = true })
            assertFailsWith<BackupFormatException> { conHook.import("garbage") }
            assertEquals(false, llamado)
        }

    @Test
    fun `the piece 2 settings travel in the backup`() =
        runTest {
            val custom =
                Settings(
                    habiMorningMinutes = 7 * 60, dndEnabled = true, dndFromMinutes = 22 * 60, dndToMinutes = 8 * 60,
                    pomoFocus = 50, pomoShort = 10, pomoLong = 30, pomoRounds = 3, pomoAutoBreak = false,
                    soundEnabled = false, volHabi = 2, volEffects = 3, volAmbient = 4, vibStrength = 5,
                    sessionBackground = SessionBackground.CAFE, reduceMotion = true,
                )
            settingsRepo.update { custom }
            val json = backup.exportJson(nowMillis = 1_000L)
            settingsRepo.update { Settings() }
            backup.import(json)
            val back = settingsRepo.settings.first()
            assertEquals(
                custom.copy(lastAutoBackupAtMillis = back.lastAutoBackupAtMillis, lastAutoBackupError = back.lastAutoBackupError),
                back,
            )
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

    @Test
    fun `memory and big thing survive a round trip`() =
        runTest {
            val memoria =
                listOf(
                    HabiMemoriaEntity("m1", "ANIMO", 100, null, 2, 11L),
                    HabiMemoriaEntity("m2", "HECHO", 101, "h1", null, 12L),
                )
            val gordo =
                listOf(
                    LoGordoEntity(101, 0, "HABITO", "h1", 13L),
                    LoGordoEntity(101, 1, "TAREA", "t1", 14L),
                )
            memoria.forEach { db.hablaDao().insertarEvento(it) }
            db.hablaDao().insertarGordo(gordo)

            val json = backup.exportJson(nowMillis = 9_000L)
            db.hablaDao().vaciarMemoria()
            db.hablaDao().vaciarGordo()
            backup.import(json)

            assertEquals(memoria, db.hablaDao().todosEventos())
            assertEquals(gordo, db.hablaDao().todoGordo())
            assertEquals(2, backup.preview(json).recuerdos)
        }

    @Test
    fun `restoring empties accepted notices, the current proposal and the used pieces and cancels their alarms`() =
        runTest {
            val canceladas = mutableListOf<String>()
            val conAvisos =
                BackupRepository(db, settingsRepo, keyStore, "0.3.0-test", cancelarAvisos = { canceladas += it })
            val json = conAvisos.exportJson(nowMillis = 9_000L)
            val dao = db.hablaDao()
            dao.insertarAviso(AvisoAceptadoEntity("a1", 100, "h1", 600, 0, "PENDIENTE", "p1", 1L))
            dao.insertarAviso(AvisoAceptadoEntity("a2", 100, "h1", 700, 0, "PENDIENTE", "p2", 2L))
            dao.insertarAviso(AvisoAceptadoEntity("a3", 100, "h1", 800, 0, "HECHO", "p3", 3L))
            dao.guardarVigente(PropuestaVigenteEntity("HOY", "", 100, "VUELTA", null, "", "{}", "", null, 4L))
            dao.insertarUsos(
                listOf(
                    HablaUsadaEntity(
                        voz = "NEUTRA",
                        bolsa = "B",
                        indice = 1,
                        combinacion = "c",
                        dia = 100,
                        usadoMillis = 5L,
                        situacion = null,
                    ),
                ),
            )

            conAvisos.import(json)

            assertEquals(listOf("a1", "a2"), canceladas.sorted())
            assertEquals(emptyList(), dao.avisosPendientes())
            assertNull(dao.aviso("a3"))
            assertNull(dao.vigente("HOY", ""))
            assertEquals(emptyList(), dao.usosDesde(0L).first())
        }

    @Test
    fun `if the restore fails no notice is cancelled`() =
        runTest {
            val canceladas = mutableListOf<String>()
            val conAvisos =
                BackupRepository(db, settingsRepo, keyStore, "0.3.0-test", cancelarAvisos = { canceladas += it })
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.hablaDao().insertarAviso(AvisoAceptadoEntity("a1", 100, "h1", 600, 0, "PENDIENTE", "p1", 1L))
            // Dos registros con el mismo id: el segundo insert aborta la transaccion a mitad.
            val roto =
                BackupCodec.encode(
                    BackupCodec.decode(conAvisos.exportJson(nowMillis = 9_000L)).let {
                        val e = BackupEntry("dup", "h1", 0, 1, 1L)
                        it.copy(entries = listOf(e, e))
                    },
                )

            assertFailsWith<Exception> { conAvisos.import(roto) }

            assertEquals(emptyList(), canceladas)
            assertEquals(listOf("a1"), db.hablaDao().avisosPendientes().map { it.id })
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
    fun `a file written before breathing settings existed restores them at their defaults`() =
        runTest {
            seedEverything()
            val legacy = backup.exportJson(nowMillis = 9_000L).let(::stripBreathingSettings)

            backup.import(legacy)

            val restored = settingsRepo.settings.first()
            assertEquals(false, restored.breathingReminderEnabled)
            assertEquals(22 * 60, restored.breathingReminderTimeMinutes)
            assertEquals(false, restored.breathingMusicEnabled)
            assertEquals(BreathingMode.CALM, restored.breathingLastMode)
            // Y el resto de ajustes sigue viajando: el recorte no se lleva nada mas.
            assertEquals(seededSettings.userName, restored.userName)
        }

    @Test
    fun `the celebrated ledger mark does not travel in the backup`() {
        val copia = Settings(celebratedLedgerMark = MarcaLibro(5L, "x")).toBackup().toSettings()
        assertNull(copia.celebratedLedgerMark)
    }

    /** Un fichero de la 1.1.0: sin la lista de sesiones y sin las cuatro claves nuevas de settings. */
    private fun stripBreathingSettings(json: String): String {
        val breathingKeys = setOf("breathingReminderEnabled", "breathingReminderTimeMinutes", "breathingMusicEnabled", "breathingLastMode")
        val root = Json.parseToJsonElement(json).jsonObject
        val settings = JsonObject(root.getValue("settings").jsonObject.filterKeys { it !in breathingKeys })
        val stripped = JsonObject(root.filterKeys { it != "breathingSessions" } + ("settings" to settings))
        return prettyJson.encodeToString(JsonObject.serializer(), stripped)
    }
}
