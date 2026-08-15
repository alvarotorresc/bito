package com.alvarotc.bito.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseSchemaTest {
    private lateinit var db: BitoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `creates the nine bito tables`() {
        val tables =
            db.openHelper.readableDatabase
                .query("SELECT name FROM sqlite_master WHERE type = 'table'")
                .use { cursor ->
                    generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
                }
        val expected =
            listOf(
                "habits", "target_changes", "pause_intervals", "entries", "day_seals",
                "freezer_uses", "points_ledger", "badges", "customization_items",
            )
        assertTrue("missing: ${expected - tables.toSet()}", tables.containsAll(expected))
    }

    @Test
    fun `schema is version 1`() {
        assertEquals(1, db.openHelper.readableDatabase.version)
    }
}
