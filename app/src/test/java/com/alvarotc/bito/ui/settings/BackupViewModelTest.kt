package com.alvarotc.bito.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.backup.Argon2Params
import com.alvarotc.bito.data.backup.BackupCrypto
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.AutoBackupError
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId

// Argon2 at production cost (~100-300ms) would dominate this suite's runtime; this tier is
// cheap but still in BackupCrypto's validated range (memoryKib >= 8 * parallelism).
private val TEST_ARGON2_PARAMS = Argon2Params(memoryKib = 64, iterations = 1, parallelism = 1)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // Same scheduler backs Dispatchers.Main, Room's executors and the VM's injected
    // ioDispatcher/cryptoDispatcher, so advanceUntilIdle() reaches every hop deterministically.
    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_786_788_000_000L // 2026-08-15T10:00:00Z
    private val utc = ZoneId.of("UTC")

    private lateinit var db: BitoDatabase
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var keyStore: BackupKeyStore
    private lateinit var backup: BackupRepository
    private lateinit var resolver: ContentResolver
    private lateinit var vm: BackupViewModel
    private var backupNowCalls = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        settingsRepo =
            SettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
                ) { File(tmp.root, "backup-vm.preferences_pb") },
            )
        keyStore = BackupKeyStore(tmp.root)
        backup = BackupRepository(db, settingsRepo, keyStore, "0.3.0-test")
        resolver = context.contentResolver
        vm =
            BackupViewModel(
                backup,
                settingsRepo,
                keyStore,
                backupNow = { backupNowCalls++ },
                now = { fixedNow },
                zone = { utc },
                ioDispatcher = dispatcher,
                cryptoDispatcher = dispatcher,
                deriveParams = TEST_ARGON2_PARAMS,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    // state is a stateIn(..., WhileSubscribed(5_000), ...) flow: it only mirrors settings while
    // it has a subscriber, mirroring TodayViewModelTest's helper for the same reason. Launching
    // on an UnconfinedTestDispatcher (rather than backgroundScope's default StandardTestDispatcher)
    // registers the subscription at the launch call itself instead of waiting for a later
    // dispatch — with a plain launch, state.value can still be its BackupUiState() seed after
    // advanceUntilIdle() the first time this runs in a given test.
    private fun TestScope.state(): BackupUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        advanceUntilIdle()
        return vm.state.value
    }

    @Test
    fun `export writes the full JSON to the uri's stream and sets EXPORT_DONE`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            val uri = Uri.parse("content://bito/export.bito")
            val out = ByteArrayOutputStream()
            shadowOf(resolver).registerOutputStream(uri, out)

            vm.export(resolver, uri)
            advanceUntilIdle()

            assertEquals(backup.exportJson(fixedNow), out.toString(Charsets.UTF_8.name()))
            assertEquals(BackupMessage.EXPORT_DONE, state().message)
        }

    @Test
    fun `loadImport with a valid exported file previews then confirmImport applies it`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            val exported = backup.exportJson(fixedNow)
            db.habitDao().deleteAll() // prove confirmImport actually restores the row, not a no-op success
            val uri = Uri.parse("content://bito/import.bito")
            shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(exported.toByteArray()))

            vm.loadImport(resolver, uri)
            advanceUntilIdle()

            val preview = state().preview
            assertNotNull(preview)
            assertEquals(1, preview!!.habits)

            vm.confirmImport()
            advanceUntilIdle()

            assertEquals("h1", db.habitDao().all().single().id)
            assertEquals(BackupMessage.IMPORT_DONE, state().message)
            assertNull(state().preview)
        }

    @Test
    fun `loadImport with garbage bytes reports INVALID_FILE and confirmImport is a no-op`() =
        runTest {
            val uri = Uri.parse("content://bito/garbage.bito")
            shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream("not a backup".toByteArray()))

            vm.loadImport(resolver, uri)
            advanceUntilIdle()

            assertNull(state().preview)
            assertEquals(BackupMessage.INVALID_FILE, state().message)

            vm.consumeMessage()
            vm.confirmImport()
            advanceUntilIdle()

            assertTrue(db.habitDao().all().isEmpty())
            assertNull(state().message)
        }

    @Test
    fun `dismissImport clears preview without touching the db`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            val exported = backup.exportJson(fixedNow)
            val uri = Uri.parse("content://bito/import2.bito")
            shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(exported.toByteArray()))

            vm.loadImport(resolver, uri)
            advanceUntilIdle()
            assertNotNull(state().preview)

            val before = db.habitDao().all()
            vm.dismissImport()

            assertNull(state().preview)
            assertEquals(before, db.habitDao().all())

            vm.confirmImport()
            advanceUntilIdle()

            assertEquals(before, db.habitDao().all())
        }

    @Test
    fun `suggestedFileName formats the fixed clock and zone`() {
        assertEquals("bito-backup-2026-08-15-1000.bito", vm.suggestedFileName())
    }

    @Test
    fun `state mirrors folder, frequency, copies and encryption from settings`() =
        runTest {
            settingsRepo.update {
                it.copy(
                    backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ABito",
                    backupFrequency = BackupFrequency.WEEKLY,
                    backupCopies = 7,
                    backupEncryption = true,
                )
            }
            keyStore.save(BackupCrypto.deriveKey("secret".toCharArray(), BackupCrypto.newSalt(), TEST_ARGON2_PARAMS))

            val ui = state()

            assertEquals("Bito", ui.folderName)
            assertEquals(BackupFrequency.WEEKLY, ui.frequency)
            assertEquals(7, ui.copies)
            assertTrue(ui.encryptionOn)
            assertFalse(ui.encryptionNeedsKey)
        }

    @Test
    fun `folder name is the readable tail of the tree uri`() =
        runTest {
            settingsRepo.update {
                it.copy(backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FBito")
            }

            assertEquals("Documents/Bito", state().folderName)
        }

    @Test
    fun `setCopies clamps to one through ten`() =
        runTest {
            vm.setCopies(-3)
            assertEquals(1, state().copies)

            vm.setCopies(42)
            assertEquals(10, state().copies)

            vm.setCopies(6)
            assertEquals(6, state().copies)
        }

    @Test
    fun `setFrequency updates the setting`() =
        runTest {
            vm.setFrequency(BackupFrequency.WEEKLY)

            assertEquals(BackupFrequency.WEEKLY, state().frequency)
        }

    @Test
    fun `setFolder persists the exact tree uri string`() =
        runTest {
            val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ABito")

            vm.setFolder(resolver, uri)
            advanceUntilIdle()

            assertEquals(uri.toString(), settingsRepo.settings.first().backupFolderUri)
        }

    @Test
    fun `changing the folder clears a stale error`() =
        runTest {
            settingsRepo.update {
                it.copy(backupFolderUri = "content://old/tree/old", lastAutoBackupError = AutoBackupError.WRITE)
            }

            vm.setFolder(resolver, Uri.parse("content://bito/tree/new"))

            assertNull(state().lastAutoBackupError)
        }

    @Test
    fun `backupNow is a no-op when no folder is configured`() =
        runTest {
            vm.backupNow()
            advanceUntilIdle()

            assertEquals(0, backupNowCalls)
        }

    @Test
    fun `backupNow triggers the injected worker when a folder is configured`() =
        runTest {
            settingsRepo.update { it.copy(backupFolderUri = "content://bito/tree/folder") }

            vm.backupNow()
            advanceUntilIdle()

            assertEquals(1, backupNowCalls)
        }

    @Test
    fun `enableEncryption stores a key and flips the setting`() =
        runTest {
            vm.enableEncryption("secret".toCharArray())

            val ui = state()
            assertEquals(BackupMessage.ENCRYPTION_ON, ui.message)
            assertTrue(ui.encryptionOn)
            assertNotNull(keyStore.load())
        }

    @Test
    fun `enableEncryption reports IO_ERROR and clears busy when saving the key fails`() =
        runTest {
            // A key store rooted in a directory that doesn't exist: BackupKeyStore.save's
            // FileOutputStream throws, proving enableEncryption doesn't strand busy=true forever.
            val brokenKeyStore = BackupKeyStore(File(tmp.root, "missing-dir"))
            val brokenVm =
                BackupViewModel(
                    backup,
                    settingsRepo,
                    brokenKeyStore,
                    backupNow = {},
                    now = { fixedNow },
                    zone = { utc },
                    ioDispatcher = dispatcher,
                    cryptoDispatcher = dispatcher,
                    deriveParams = TEST_ARGON2_PARAMS,
                )

            brokenVm.enableEncryption("secret".toCharArray())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { brokenVm.state.collect() }
            advanceUntilIdle()

            val ui = brokenVm.state.value
            assertEquals(BackupMessage.IO_ERROR, ui.message)
            assertFalse(ui.busy)
            assertFalse(ui.encryptionOn)
        }

    @Test
    fun `disableEncryption clears the key and the setting`() =
        runTest {
            vm.enableEncryption("secret".toCharArray())
            advanceUntilIdle()

            vm.disableEncryption()

            val ui = state()
            assertEquals(BackupMessage.ENCRYPTION_OFF, ui.message)
            assertFalse(ui.encryptionOn)
            assertNull(keyStore.load())
        }

    @Test
    fun `export with encryption on and no key reports MISSING_KEY`() =
        runTest {
            settingsRepo.update { it.copy(backupEncryption = true) }
            val uri = Uri.parse("content://bito/missing-key.bito")
            val out = ByteArrayOutputStream()
            shadowOf(resolver).registerOutputStream(uri, out)

            vm.export(resolver, uri)
            advanceUntilIdle()

            assertEquals(BackupMessage.MISSING_KEY, state().message)
        }

    @Test
    fun `encryptionNeedsKey is true when the setting is on but the key file is gone`() =
        runTest {
            settingsRepo.update { it.copy(backupEncryption = true) }

            assertTrue(state().encryptionNeedsKey)
        }

    @Test
    fun `loading an encrypted file asks for the import passphrase`() =
        runTest {
            settingsRepo.update { it.copy(backupEncryption = true) }
            keyStore.save(BackupCrypto.deriveKey("secret".toCharArray(), BackupCrypto.newSalt(), TEST_ARGON2_PARAMS))
            val encrypted = backup.exportBytes(fixedNow)
            val uri = Uri.parse("content://bito/encrypted.bito")
            shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(encrypted))

            vm.loadImport(resolver, uri)
            advanceUntilIdle()

            assertTrue(state().askImportPassphrase)
        }

    @Test
    fun `a wrong import passphrase reports WRONG_PASSPHRASE and keeps asking`() =
        runTest {
            settingsRepo.update { it.copy(backupEncryption = true) }
            keyStore.save(BackupCrypto.deriveKey("right-pass".toCharArray(), BackupCrypto.newSalt(), TEST_ARGON2_PARAMS))
            val encrypted = backup.exportBytes(fixedNow)
            val uri = Uri.parse("content://bito/wrong-pass.bito")
            shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(encrypted))
            vm.loadImport(resolver, uri)
            advanceUntilIdle()

            vm.submitImportPassphrase("wrong-pass".toCharArray())
            advanceUntilIdle()

            val ui = state()
            assertEquals(BackupMessage.WRONG_PASSPHRASE, ui.message)
            assertTrue(ui.askImportPassphrase)
        }

    @Test
    fun `the right import passphrase lands on the normal preview`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            settingsRepo.update { it.copy(backupEncryption = true) }
            keyStore.save(BackupCrypto.deriveKey("right-pass".toCharArray(), BackupCrypto.newSalt(), TEST_ARGON2_PARAMS))
            val encrypted = backup.exportBytes(fixedNow)
            val uri = Uri.parse("content://bito/right-pass.bito")
            shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(encrypted))
            vm.loadImport(resolver, uri)
            advanceUntilIdle()

            vm.submitImportPassphrase("right-pass".toCharArray())
            advanceUntilIdle()

            val ui = state()
            assertFalse(ui.askImportPassphrase)
            assertNotNull(ui.preview)
            assertEquals(1, ui.preview!!.habits)
        }

    @Test
    fun `dismissImportPassphrase closes the sheet and drops the pending bytes`() =
        runTest {
            settingsRepo.update { it.copy(backupEncryption = true) }
            keyStore.save(BackupCrypto.deriveKey("secret".toCharArray(), BackupCrypto.newSalt(), TEST_ARGON2_PARAMS))
            val encrypted = backup.exportBytes(fixedNow)
            val uri = Uri.parse("content://bito/dismiss.bito")
            shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(encrypted))
            vm.loadImport(resolver, uri)
            advanceUntilIdle()
            assertTrue(state().askImportPassphrase)

            vm.dismissImportPassphrase()
            assertFalse(state().askImportPassphrase)

            // A stale bytes reference shouldn't resurrect a preview after the sheet was dismissed.
            vm.submitImportPassphrase("secret".toCharArray())
            advanceUntilIdle()
            assertNull(state().preview)
        }
}
