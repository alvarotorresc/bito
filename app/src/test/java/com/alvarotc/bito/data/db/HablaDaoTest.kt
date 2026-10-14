package com.alvarotc.bito.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HablaDaoTest {
    private lateinit var db: BitoDatabase
    private lateinit var dao: HablaDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).allowMainThreadQueries().build()
        dao = db.hablaDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun gordo(
        dia: Int,
        posicion: Int,
        ref: String,
    ) = LoGordoEntity(dia, posicion, "HABITO", ref, 1L)

    private fun aviso(
        id: String,
        estado: String,
        habitId: String = "h1",
    ) = AvisoAceptadoEntity(id, 20_000, habitId, 600, 0, estado, "p-$id", 1L)

    private fun vigente(
        ambito: String,
        habitId: String,
        situacion: String,
    ) = PropuestaVigenteEntity(ambito, habitId, 20_000, situacion, null, "comunes:1", "{}", "", null, 1L)

    private fun uso(
        voz: String,
        usadoMillis: Long,
    ) = HablaUsadaEntity(
        voz = voz,
        bolsa = "comunes",
        indice = 3,
        combinacion = "c",
        dia = 20_000,
        usadoMillis = usadoMillis,
        situacion = null,
    )

    @Test
    fun `eventosDesde keeps the days from the given one and borrarEventosAntesDe purges the older ones`() =
        runBlocking {
            dao.insertarEvento(HabiMemoriaEntity("a", "ANIMO", 100, null, 1, 1L))
            dao.insertarEvento(HabiMemoriaEntity("b", "ANIMO", 200, "h1", null, 2L))
            assertEquals(listOf("b"), dao.eventosDesde(150).first().map { it.id })
            dao.borrarEventosAntesDe(150)
            assertEquals(listOf("b"), dao.todosEventos().map { it.id })
        }

    @Test
    fun `reemplazarGordo leaves exactly the new rows for that day and touches no other day`() =
        runBlocking {
            dao.reemplazarGordo(10, listOf(gordo(10, 0, "a"), gordo(10, 1, "b"), gordo(10, 2, "c")))
            dao.reemplazarGordo(11, listOf(gordo(11, 0, "z")))
            dao.reemplazarGordo(10, listOf(gordo(10, 0, "x")))
            assertEquals(listOf("x"), dao.gordoDe(listOf(10)).first().map { it.refId })
            assertEquals(listOf("z"), dao.gordoDe(listOf(11)).first().map { it.refId })
        }

    @Test
    fun `borrarGordoDeRef removes only that ref`() =
        runBlocking {
            dao.reemplazarGordo(10, listOf(gordo(10, 0, "a"), gordo(10, 1, "b")))
            dao.reemplazarGordo(11, listOf(gordo(11, 0, "a")))
            dao.borrarGordoDeRef("a")
            assertEquals(listOf("b"), dao.todoGordo().map { it.refId })
        }

    @Test
    fun `reemplazarMemoriaYGordo swaps both tables`() =
        runBlocking {
            dao.insertarEvento(HabiMemoriaEntity("old", "ANIMO", 1, null, null, 1L))
            dao.reemplazarGordo(1, listOf(gordo(1, 0, "old")))
            dao.reemplazarMemoriaYGordo(
                listOf(HabiMemoriaEntity("new", "ANIMO", 2, null, null, 2L)),
                listOf(gordo(2, 0, "new")),
            )
            assertEquals(listOf("new"), dao.todosEventos().map { it.id })
            assertEquals(listOf("new"), dao.todoGordo().map { it.refId })
        }

    @Test
    fun `guardarVigente replaces by ambito and habit, and borrarVigente removes only that one`() =
        runBlocking {
            dao.guardarVigente(vigente("HOY", "", "SALUDO"))
            dao.guardarVigente(vigente("FICHA", "h1", "FI_A"))
            dao.guardarVigente(vigente("HOY", "", "NOCHE"))
            assertEquals("NOCHE", dao.vigente("HOY", "")!!.situacion)
            assertEquals("FI_A", dao.vigente("FICHA", "h1")!!.situacion)
            assertNull(dao.vigente("FICHA", "h2"))
            dao.borrarVigente("HOY", "")
            assertNull(dao.vigente("HOY", ""))
            assertNotNull(dao.vigente("FICHA", "h1"))
            dao.vaciarVigentes()
            assertNull(dao.vigente("FICHA", "h1"))
        }

    @Test
    fun `avisosPendientes returns only PENDIENTE and the rest of the avisos API works`() =
        runBlocking {
            dao.insertarAviso(aviso("a", "PENDIENTE"))
            dao.insertarAviso(aviso("b", "LANZADO"))
            dao.insertarAviso(aviso("c", "PENDIENTE", habitId = "h2"))
            assertEquals(listOf("a", "c"), dao.avisosPendientes().map { it.id })
            assertEquals(3, dao.avisosDe(20_000).first().size)
            dao.actualizarAviso(aviso("a", "HECHO"))
            assertEquals(listOf("c"), dao.avisosPendientes().map { it.id })
            dao.borrarAvisosDeHabito("h2")
            assertEquals(listOf("a", "b"), dao.avisosDe(20_000).first().map { it.id })
            dao.vaciarAvisos()
            assertEquals(0, dao.avisosDe(20_000).first().size)
        }

    @Test
    fun `borrarUsosAntesDe purges by usado_millis and usosDesde reads the rest`() =
        runBlocking {
            dao.insertarUsos(listOf(uso("NEUTRA", 100L), uso("NEUTRA", 200L), uso("DRAMATICA", 300L)))
            assertEquals(2, dao.usosDesde(200L).first().size)
            dao.borrarUsosAntesDe(250L)
            assertEquals(listOf("DRAMATICA"), dao.usosDesde(0L).first().map { it.voz })
        }
}
