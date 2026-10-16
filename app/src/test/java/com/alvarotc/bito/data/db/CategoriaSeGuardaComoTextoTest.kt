package com.alvarotc.bito.data.db

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.backup.BackupKeyStore
import com.alvarotc.bito.data.backup.BackupRepository
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.CustomizationCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Añadir un valor a `CustomizationCategory` no cambia el esquema: Room lo guarda por nombre (spec §7.1). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoriaSeGuardaComoTextoTest {
    private lateinit var db: BitoDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var keyStore: BackupKeyStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        val store =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
            ) { context.filesDir.resolve("t-${UUID.randomUUID()}.preferences_pb") }
        settings = SettingsRepository(store)
        keyStore = BackupKeyStore(context.filesDir.resolve("keystore-${UUID.randomUUID()}").apply { mkdirs() })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `customization_items category column is TEXT in the real database`() {
        val cursor = db.openHelper.writableDatabase.query("PRAGMA table_info(customization_items)")
        val tipos = mutableMapOf<String, String>()
        cursor.use {
            while (it.moveToNext()) tipos[it.getString(it.getColumnIndexOrThrow("name"))] = it.getString(it.getColumnIndexOrThrow("type"))
        }
        assertEquals("TEXT", tipos["category"])
    }

    @Test
    fun `a GARDEN_OBJECT row is saved and read back`() =
        runTest {
            db.customizationItemDao().upsert(
                customizationItemEntity(itemId = "garden-banco", category = CustomizationCategory.GARDEN_OBJECT, equipped = true),
            )
            val fila = db.customizationItemDao().byId("garden-banco")
            assertNotNull(fila)
            assertEquals(CustomizationCategory.GARDEN_OBJECT, fila!!.category)
            val texto =
                db.openHelper.writableDatabase
                    .query("SELECT category FROM customization_items WHERE itemId = 'garden-banco'")
                    .use {
                        it.moveToFirst()
                        it.getString(0)
                    }
            assertEquals("GARDEN_OBJECT", texto)
        }

    @Test
    fun `a backup with a GARDEN_OBJECT row restores it`() =
        runTest {
            val backup = BackupRepository(db, settings, keyStore, "0.3.0-test")
            db.customizationItemDao().upsert(
                customizationItemEntity(itemId = "garden-regadera", category = CustomizationCategory.GARDEN_OBJECT, equipped = true),
            )
            val json = backup.exportJson(1_000L)
            db.customizationItemDao().deleteAll()
            assertTrue(db.customizationItemDao().all().isEmpty())

            backup.import(json)

            val fila = db.customizationItemDao().byId("garden-regadera")
            assertEquals(CustomizationCategory.GARDEN_OBJECT, fila?.category)
            assertTrue(fila?.equipped == true)
        }

    @Test
    fun `the exported schema 6 has no converter for the category`() {
        val esquema = File("schemas/com.alvarotc.bito.data.db.BitoDatabase/6.json").readText()
        val sql = Regex("""CREATE TABLE IF NOT EXISTS `\$\{TABLE_NAME}` \(`itemId` TEXT NOT NULL, `category` ([A-Z ]+),""").find(esquema)
        assertNotNull("customization_items no aparece en el esquema 6", sql)
        assertEquals("TEXT NOT NULL", sql!!.groupValues[1].trim())
        assertFalse(esquema.contains("CustomizationCategory"))
    }
}
