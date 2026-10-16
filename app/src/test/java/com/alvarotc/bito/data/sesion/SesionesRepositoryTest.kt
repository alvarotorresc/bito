package com.alvarotc.bito.data.sesion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.ResultadoSesion
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
class SesionesRepositoryTest {
    private lateinit var db: BitoDatabase
    private lateinit var repo: SesionesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        repo = SesionesRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun cerrada(
        id: String,
        objetivo: Objetivo = Objetivo.Tarea("t1"),
        dia: Int = DAY_ZERO,
        minutos: Int = 25,
        resultado: ResultadoSesion = ResultadoSesion.HECHA,
    ) = SesionCerrada(id, objetivo, dia, 1_000L, 2_000L, minutos, 1, 4, resultado)

    @Test
    fun `a session under one minute is not saved`() =
        runTest {
            repo.guardar(cerrada("s0", minutos = 0))
            repo.guardar(cerrada("s1", minutos = 1))
            assertEquals(listOf("s1"), repo.deDia(DAY_ZERO).first().map { it.id })
        }

    @Test
    fun `every kind of target and result survives the round trip`() =
        runTest {
            val todas =
                listOf(
                    cerrada("a", Objetivo.Tarea("t1"), resultado = ResultadoSesion.SIGO_LUEGO),
                    cerrada("b", Objetivo.Habito("h1"), resultado = ResultadoSesion.LO_DEJO),
                    cerrada("c", Objetivo.Libre, resultado = ResultadoSesion.SIN_RESPUESTA),
                )
            todas.forEach { repo.guardar(it) }
            assertEquals(todas.sortedBy { it.id }, repo.deDia(DAY_ZERO).first().sortedBy { it.id })
        }

    @Test
    fun `minutes of a target on a day add up`() =
        runTest {
            repo.guardar(cerrada("a", minutos = 25))
            repo.guardar(cerrada("b", minutos = 10))
            repo.guardar(cerrada("d", dia = DAY_ZERO + 1, minutos = 40))
            assertEquals(35, repo.minutosDe("t1", DAY_ZERO))
            assertEquals(0, repo.minutosDe("nadie", DAY_ZERO))
        }

    @Test
    fun `deleting a task or habit deletes its sessions`() =
        runTest {
            val tareas = TasksRepository(db, repo)
            val habitos = HabitsRepository(db, repo)
            tareas.create(taskEntity(id = "t1"))
            db.habitDao().upsert(habitEntity(id = "h1"))
            repo.guardar(cerrada("a", Objetivo.Tarea("t1")))
            repo.guardar(cerrada("b", Objetivo.Habito("h1")))
            repo.guardar(cerrada("c", Objetivo.Libre))

            tareas.delete("t1")
            assertEquals(listOf("b", "c"), repo.deDia(DAY_ZERO).first().map { it.id }.sorted())
            habitos.delete("h1")
            assertEquals(listOf("c"), repo.deDia(DAY_ZERO).first().map { it.id })
        }

    @Test
    fun `borrarDe on an unknown id leaves everything`() =
        runTest {
            repo.guardar(cerrada("a"))
            repo.borrarDe("otro")
            assertTrue(repo.deDia(DAY_ZERO).first().isNotEmpty())
        }
}
