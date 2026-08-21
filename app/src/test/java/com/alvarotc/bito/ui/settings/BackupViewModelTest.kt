package com.alvarotc.bito.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // Same scheduler backs Dispatchers.Main, Room's executors and the VM's injected
    // ioDispatcher, so advanceUntilIdle() reaches every hop deterministically.
    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_786_788_000_000L // 2026-08-15T10:00:00Z
    private val utc = ZoneId.of("UTC")

    private lateinit var db: BitoDatabase
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var backup: BackupRepository
    private lateinit var resolver: ContentResolver
    private lateinit var vm: BackupViewModel

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
        backup = BackupRepository(db, settingsRepo, BackupKeyStore(tmp.root), "0.3.0-test")
        resolver = context.contentResolver
        vm = BackupViewModel(backup, now = { fixedNow }, zone = { utc }, ioDispatcher = dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
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
            assertEquals(BackupMessage.EXPORT_DONE, vm.state.value.message)
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

            val preview = vm.state.value.preview
            assertNotNull(preview)
            assertEquals(1, preview!!.habits)

            vm.confirmImport()
            advanceUntilIdle()

            assertEquals("h1", db.habitDao().all().single().id)
            assertEquals(BackupMessage.IMPORT_DONE, vm.state.value.message)
            assertNull(vm.state.value.preview)
        }

    @Test
    fun `loadImport with garbage bytes reports INVALID_FILE and confirmImport is a no-op`() =
        runTest {
            val uri = Uri.parse("content://bito/garbage.bito")
            shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream("not a backup".toByteArray()))

            vm.loadImport(resolver, uri)
            advanceUntilIdle()

            assertNull(vm.state.value.preview)
            assertEquals(BackupMessage.INVALID_FILE, vm.state.value.message)

            vm.consumeMessage()
            vm.confirmImport()
            advanceUntilIdle()

            assertTrue(db.habitDao().all().isEmpty())
            assertNull(vm.state.value.message)
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
            assertNotNull(vm.state.value.preview)

            val before = db.habitDao().all()
            vm.dismissImport()

            assertNull(vm.state.value.preview)
            assertEquals(before, db.habitDao().all())

            vm.confirmImport()
            advanceUntilIdle()

            assertEquals(before, db.habitDao().all())
        }

    @Test
    fun `suggestedFileName formats the fixed clock and zone`() {
        assertEquals("bito-backup-2026-08-15-1000.bito", vm.suggestedFileName())
    }
}
