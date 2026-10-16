package com.alvarotc.bito.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class SesionDaoTest {
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

    private fun fila(
        id: String,
        objetivoId: String? = "t1",
        dia: Int = 20_000,
        inicio: Long = 1_000L,
        minutos: Int = 25,
    ) = SesionEntity(id, if (objetivoId == null) "LIBRE" else "TAREA", objetivoId, dia, inicio, inicio + 1_500_000L, minutos, 1, 4, "HECHA")

    @Test
    fun `insert and read back keeps the ten fields, a free session with a null target`() =
        runTest {
            val libre = fila("s1", objetivoId = null)
            db.sesionDao().insert(libre)
            assertEquals(listOf(libre), db.sesionDao().all())
        }

    @Test
    fun `the sessions of a day come ordered by start`() =
        runTest {
            db.sesionDao().insert(fila("b", inicio = 5_000L))
            db.sesionDao().insert(fila("a", inicio = 2_000L))
            db.sesionDao().insert(fila("c", dia = 20_001, inicio = 1L))
            assertEquals(listOf("a", "b"), db.sesionDao().observeDeDia(20_000).first().map { it.id })
        }

    @Test
    fun `minutes of a target on a day add up and are zero when there is nothing`() =
        runTest {
            db.sesionDao().insert(fila("a", minutos = 25))
            db.sesionDao().insert(fila("b", minutos = 10))
            db.sesionDao().insert(fila("c", objetivoId = "t2", minutos = 99))
            db.sesionDao().insert(fila("d", dia = 20_001, minutos = 99))
            assertEquals(35, db.sesionDao().minutosDe("t1", 20_000))
            assertEquals(0, db.sesionDao().minutosDe("nadie", 20_000))
        }

    @Test
    fun `deleting by target removes only its sessions`() =
        runTest {
            db.sesionDao().insert(fila("a"))
            db.sesionDao().insert(fila("b", objetivoId = "t2"))
            db.sesionDao().borrarDe("t1")
            assertEquals(listOf("b"), db.sesionDao().all().map { it.id })
            db.sesionDao().deleteAll()
            assertTrue(db.sesionDao().all().isEmpty())
        }
}
