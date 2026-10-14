package com.alvarotc.bito.data.habla

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.PropuestaVigente
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PropuestaVigenteStoreTest {
    private lateinit var db: BitoDatabase
    private lateinit var store: PropuestaVigenteStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        store = PropuestaVigenteStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun vigente(
        ambito: Ambito,
        habitId: String = "",
        situacion: Situacion = Situacion.SALUDO,
    ) = PropuestaVigente(
        ambito, habitId, 20_000, situacion, null,
        listOf(ClaveTrozo(Bolsa.NU_SALUDO, Personality.NEUTRA, 1)), emptyMap(),
        listOf(Respuesta.VALE), Destino.Respirar, null, 5L,
    )

    @Test
    fun `saves and reads by scope and habit`() =
        runTest {
            store.guardar(vigente(Ambito.HOY))
            store.guardar(vigente(Ambito.FICHA, "h1", Situacion.FI_NUEVO))
            assertEquals(Situacion.SALUDO, store.leer(Ambito.HOY)!!.situacion)
            assertEquals(Situacion.FI_NUEVO, store.leer(Ambito.FICHA, "h1")!!.situacion)
            assertNull(store.leer(Ambito.FICHA, "h2"))
        }

    @Test
    fun `saving again replaces and removing deletes`() =
        runTest {
            store.guardar(vigente(Ambito.HOY))
            store.guardar(vigente(Ambito.HOY, situacion = Situacion.NOCHE))
            assertEquals(Situacion.NOCHE, store.leer(Ambito.HOY)!!.situacion)
            store.borrar(Ambito.HOY)
            assertNull(store.leer(Ambito.HOY))
        }

    @Test
    fun `emptying removes every scope`() =
        runTest {
            store.guardar(vigente(Ambito.HOY))
            store.guardar(vigente(Ambito.CAMINO))
            store.vaciar()
            assertNull(store.leer(Ambito.HOY))
            assertNull(store.leer(Ambito.CAMINO))
        }

    @Test
    fun `an ambiguous ref is resolved by the task table in both read paths`() =
        runTest {
            db.taskDao().upsert(taskEntity(id = "t1"))
            store.guardar(vigente(Ambito.HOY, situacion = Situacion.GORDO_SIN_EMPEZAR).copy(ref = "t1"))
            store.guardar(vigente(Ambito.FICHA, "h1", Situacion.GORDO_SIN_EMPEZAR).copy(ref = "h1"))

            assertEquals(Destino.Tarea("t1"), store.leer(Ambito.HOY)!!.destino)
            assertEquals(Destino.Habito("h1"), store.leer(Ambito.FICHA, "h1")!!.destino)

            val vigentes = MemoriaRepository(db).observe(flowOf(20_000)).first().vigentes.associateBy { it.ambito }
            assertEquals(Destino.Tarea("t1"), vigentes.getValue(Ambito.HOY).destino)
            assertEquals(Destino.Habito("h1"), vigentes.getValue(Ambito.FICHA).destino)
        }
}
