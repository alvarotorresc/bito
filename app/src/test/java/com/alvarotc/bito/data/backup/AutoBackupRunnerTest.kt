package com.alvarotc.bito.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.settings.AutoBackupError
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val FOLDER = "content://tree/backups"

/**
 * The runner is WorkManager-free by design (tech doc M8 T5), so this drives it directly on
 * the JVM: a real in-memory [BitoDatabase] (mirrors [BackupRoundTripTest]'s setup) plus a
 * [FakeSink] recording every call instead of touching SAF.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoBackupRunnerTest {
    private lateinit var db: BitoDatabase
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var keyStore: BackupKeyStore
    private lateinit var backup: BackupRepository
    private lateinit var sink: FakeSink

    // 2023-11-14 22:13:20 UTC — picked only so the formatted file name is easy to eyeball.
    private val fixedNow = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        // A unique file per test avoids the "multiple DataStores active on the same file"
        // crash documented on AppStartupTest.
        val store =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
            ) { context.filesDir.resolve("t-${UUID.randomUUID()}.preferences_pb") }
        settingsRepo = SettingsRepository(store)
        keyStore = BackupKeyStore(context.filesDir.resolve("keystore-${UUID.randomUUID()}").apply { mkdirs() })
        backup = BackupRepository(db, settingsRepo, keyStore, "0.7.0-test")
        sink = FakeSink()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun runner(hasPermission: Boolean = true) =
        AutoBackupRunner(
            settings = settingsRepo,
            backup = backup,
            sink = sink,
            hasPersistedPermission = { hasPermission },
            now = { fixedNow },
            zone = { ZoneId.of("UTC") },
        )

    @Test
    fun `no folder means skipped and nothing written`() =
        runTest {
            settingsRepo.update { it.copy(backupFolderUri = null) }

            val outcome = runner().run()

            assertEquals(AutoBackupRunner.Outcome.SKIPPED, outcome)
            assertTrue(sink.writes.isEmpty())
            assertNull(settingsRepo.settings.first().lastAutoBackupError)
        }

    @Test
    fun `lost folder permission records FOLDER error and skips`() =
        runTest {
            settingsRepo.update { it.copy(backupFolderUri = FOLDER) }

            val outcome = runner(hasPermission = false).run()

            assertEquals(AutoBackupRunner.Outcome.SKIPPED, outcome)
            assertTrue(sink.writes.isEmpty())
            assertEquals(AutoBackupError.FOLDER, settingsRepo.settings.first().lastAutoBackupError)
        }

    @Test
    fun `missing key with encryption on records MISSING_KEY and skips`() =
        runTest {
            settingsRepo.update { it.copy(backupFolderUri = FOLDER, backupEncryption = true) }
            // No key ever saved to keyStore.

            val outcome = runner().run()

            assertEquals(AutoBackupRunner.Outcome.SKIPPED, outcome)
            assertTrue(sink.writes.isEmpty())
            assertEquals(AutoBackupError.MISSING_KEY, settingsRepo.settings.first().lastAutoBackupError)
        }

    @Test
    fun `sink failure records WRITE error and asks for retry`() =
        runTest {
            settingsRepo.update { it.copy(backupFolderUri = FOLDER) }
            sink.writeFailure = IOException("disk full")

            val outcome = runner().run()

            assertEquals(AutoBackupRunner.Outcome.RETRY, outcome)
            assertEquals(AutoBackupError.WRITE, settingsRepo.settings.first().lastAutoBackupError)
        }

    // DocumentFile.listFiles()/createFile() (SafBackupWriter's pre-write cleanup) run outside
    // its own IOException/SecurityException try block, so a grant revoked between the
    // hasPersistedPermission check above and the actual SAF call reaches the runner as a bare
    // SecurityException — the controller ruling that refined this brief.
    @Test
    fun `sink permission revoked mid write records FOLDER error and skips`() =
        runTest {
            settingsRepo.update { it.copy(backupFolderUri = FOLDER) }
            sink.writeFailure = SecurityException("grant revoked")

            val outcome = runner().run()

            assertEquals(AutoBackupRunner.Outcome.SKIPPED, outcome)
            assertTrue(sink.writes.isEmpty())
            assertEquals(AutoBackupError.FOLDER, settingsRepo.settings.first().lastAutoBackupError)
        }

    // Any other exception (RuntimeException, SQLiteException, ...) from the sink must still be
    // mapped to an outcome instead of escaping run() and leaving the last-known status stale.
    @Test
    fun `sink throwing an unexpected exception records WRITE error and asks for retry`() =
        runTest {
            settingsRepo.update { it.copy(backupFolderUri = FOLDER) }
            sink.writeFailure = RuntimeException("boom")

            val outcome = runner().run()

            assertEquals(AutoBackupRunner.Outcome.RETRY, outcome)
            assertEquals(AutoBackupError.WRITE, settingsRepo.settings.first().lastAutoBackupError)
        }

    @Test
    fun `success writes then rotates with the configured copies`() =
        runTest {
            settingsRepo.update { it.copy(backupFolderUri = FOLDER, backupCopies = 3) }

            val outcome = runner().run()

            assertEquals(AutoBackupRunner.Outcome.DONE, outcome)
            assertEquals(1, sink.writes.size)
            assertEquals(listOf(Uri.parse(FOLDER) to 3), sink.rotates)
        }

    @Test
    fun `success stamps lastAutoBackupAtMillis and clears the error`() =
        runTest {
            settingsRepo.update {
                it.copy(backupFolderUri = FOLDER, lastAutoBackupError = AutoBackupError.WRITE)
            }

            val outcome = runner().run()

            assertEquals(AutoBackupRunner.Outcome.DONE, outcome)
            val prefs = settingsRepo.settings.first()
            assertEquals(fixedNow, prefs.lastAutoBackupAtMillis)
            assertNull(prefs.lastAutoBackupError)
        }

    // Controller ruling: rotate() failing after a successful write is non-fatal — the backup
    // itself is already safe on disk, so this still stamps success; the next run rotates.
    @Test
    fun `rotate failure after a successful write still stamps success`() =
        runTest {
            settingsRepo.update { it.copy(backupFolderUri = FOLDER) }
            sink.rotateFailure = IOException("rotate boom")

            val outcome = runner().run()

            assertEquals(AutoBackupRunner.Outcome.DONE, outcome)
            val prefs = settingsRepo.settings.first()
            assertEquals(fixedNow, prefs.lastAutoBackupAtMillis)
            assertNull(prefs.lastAutoBackupError)
        }

    @Test
    fun `file name carries the bito-backup date pattern`() =
        runTest {
            settingsRepo.update { it.copy(backupFolderUri = FOLDER) }

            runner().run()

            assertEquals(1, sink.writes.size)
            assertEquals("bito-backup-2023-11-14-2213.bito", sink.writes.single().fileName)
        }
}

private class FakeSink : BackupSink {
    data class Write(val treeUri: Uri, val fileName: String, val bytes: ByteArray)

    val writes = mutableListOf<Write>()
    val rotates = mutableListOf<Pair<Uri, Int>>()
    var writeFailure: Exception? = null
    var rotateFailure: Exception? = null

    override suspend fun write(
        treeUri: Uri,
        fileName: String,
        bytes: ByteArray,
    ) {
        writeFailure?.let { throw it }
        writes += Write(treeUri, fileName, bytes)
    }

    override suspend fun rotate(
        treeUri: Uri,
        keep: Int,
    ) {
        rotateFailure?.let { throw it }
        rotates += treeUri to keep
    }
}
