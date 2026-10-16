package com.alvarotc.bito.data.sesion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Evento
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.PlanSesion
import com.alvarotc.bito.domain.sesion.RegistroSesion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** La sesión en curso en `focus.preferences_pb`, como una cadena JSON con versión (spec §14.2). */
@OptIn(ExperimentalCoroutinesApi::class)
class SesionStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clave = stringPreferencesKey("sesion_v1")

    private fun TestScope.dataStore(
        name: String,
        job: Job = Job(),
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun registro(
        objetivo: Objetivo,
        eventos: List<Evento> = emptyList(),
        filtroPrevio: Int? = null,
        arranques: Int? = null,
    ) = RegistroSesion(
        id = "7f1c3a52-9a4e-4a43-8a8e-2f7c1d0b9e11",
        plan =
            PlanSesion(
                objetivo = objetivo,
                minutosFoco = 45,
                entrada = Entrada.DIRECTO,
                bloques = 3,
                descansoCorto = 10,
                descansoLargo = 20,
                rondas = 2,
                descansoSolo = false,
                fondo = SessionBackground.BROWN,
                noMolestar = true,
            ),
        eventos = eventos,
        inicioPared = 1_800_000_000_000L,
        anclaElapsed = 50_000_000L,
        arranque = 1_799_950_000_000L,
        filtroPrevio = filtroPrevio,
        inicioDia = 20_123,
        arranques = arranques,
    )

    private val todosLosEventos =
        listOf(
            Evento.Pausa(60_000L),
            Evento.Seguir(120_000L),
            Evento.Sumar(180_000L, 15),
            Evento.Saltar(240_000L),
            Evento.EmpezarDescanso(300_000L),
        )

    @Test
    fun `a fresh store has no session`() =
        runTest {
            val store = SesionStore(dataStore("fresh"))
            assertNull(store.registro.first())
            assertNull(store.leer())
        }

    @Test
    fun `a write to another key of the shared file does not re-emit the record`() =
        runTest {
            val ds = dataStore("distinct")
            val store = SesionStore(ds)
            store.guardar(registro(Objetivo.Libre))
            val emitidos = mutableListOf<RegistroSesion?>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { store.registro.toList(emitidos) }
            ds.edit { it[stringPreferencesKey("avisos_retenidos")] = "x" }
            ds.edit { it[stringPreferencesKey("avisos_retenidos")] = "y" }
            job.cancelAndJoin()
            assertEquals(1, emitidos.size)
            assertTrue(emitidos.single() != null)
        }

    @Test
    fun `updating only touches the stored session with that id and never brings a closed one back`() =
        runTest {
            val store = SesionStore(dataStore("actualizar"))
            val r = registro(Objetivo.Libre, filtroPrevio = 2)
            assertNull(store.actualizar(r.id) { it.copy(filtroPrevio = null) })
            assertNull(store.leer())

            store.guardar(r)
            assertNull(store.actualizar("otra") { it.copy(filtroPrevio = null) })
            assertEquals(r, store.leer())

            assertEquals(r.copy(filtroPrevio = null), store.actualizar(r.id) { it.copy(filtroPrevio = null) })
            assertEquals(r.copy(filtroPrevio = null), store.leer())

            assertNull(store.actualizar(r.id) { null })
            assertNull(store.leer())
            assertNull(store.actualizar(r.id) { it.copy(filtroPrevio = 9) })
            assertNull(store.leer())
        }

    @Test
    fun `two writers updating at once lose nothing`() =
        runBlocking {
            val ds =
                PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + Job())) { File(tmp.root, "dos.preferences_pb") }
            val store = SesionStore(ds)
            val r = registro(Objetivo.Libre)
            store.guardar(r)
            val a = async(Dispatchers.Default) { repeat(40) { i -> store.actualizar(r.id) { it.copy(eventos = it.eventos + Evento.Sumar(i * 1L, 1)) } } }
            val b = async(Dispatchers.Default) { repeat(40) { store.actualizar(r.id) { it.copy(inicioDia = it.inicioDia + 1) } } }
            a.await()
            b.await()
            val final = store.leer()!!
            assertEquals(40, final.eventos.size)
            assertEquals(r.inicioDia + 40, final.inicioDia)
        }

    @Test
    fun `a record round-trips with every event and every goal`() =
        runTest {
            val store = SesionStore(dataStore("roundtrip"))
            for (r in listOf(
                registro(Objetivo.Tarea("t1"), todosLosEventos, filtroPrevio = 1, arranques = 41),
                registro(Objetivo.Habito("h9"), todosLosEventos.take(2)),
                registro(Objetivo.Libre),
            )) {
                store.guardar(r)
                assertEquals(r, store.leer())
                assertEquals(r, store.registro.first())
            }
        }

    @Test
    fun `the record is one JSON string with its version`() =
        runTest {
            val ds = dataStore("json")
            SesionStore(ds).guardar(registro(Objetivo.Libre))
            val json = ds.data.first()[clave]!!
            assertTrue(json, json.contains("\"v\":1"))
        }

    @Test
    fun `a record saved without the boot counter still reads`() =
        runTest {
            val ds = dataStore("sincontador")
            val store = SesionStore(ds)
            store.guardar(registro(Objetivo.Libre, arranques = 41))
            val guardado = Json.parseToJsonElement(ds.data.first()[clave]!!).jsonObject
            assertTrue(guardado.containsKey("arranques"))
            val viejo = JsonObject(guardado - "arranques").toString()
            ds.edit { it[clave] = viejo }
            assertEquals(registro(Objetivo.Libre), store.leer())
            assertTrue(viejo, viejo.contains("\"v\":1"))
        }

    @Test
    fun `an unreadable or old record means no session`() =
        runTest {
            val ds = dataStore("old")
            val store = SesionStore(ds)
            for (malo in listOf("{no es json", "{\"v\":2,\"id\":\"x\"}", "")) {
                ds.edit { it[clave] = malo }
                assertNull(malo, store.registro.first())
                assertNull(malo, store.leer())
                assertFalse(malo, ds.data.first().contains(clave))
            }
            // Una versión vieja con la forma de hoy tampoco vale.
            store.guardar(registro(Objetivo.Libre))
            val actual = ds.data.first()[clave]!!
            ds.edit { it[clave] = actual.replace("\"v\":1", "\"v\":0") }
            assertNull(store.leer())
        }

    @Test
    fun `saving clears the old focus keys`() =
        runTest {
            val ds = dataStore("legacy")
            ds.edit {
                it[stringPreferencesKey("task_id")] = "t1"
                it[longPreferencesKey("started_at_millis")] = 1L
                it[longPreferencesKey("ends_at_millis")] = 2L
                it[longPreferencesKey("ends_at_elapsed")] = 3L
                it[longPreferencesKey("boot_millis")] = 4L
            }
            SesionStore(ds).guardar(registro(Objetivo.Tarea("t1")))
            val claves = ds.data.first().asMap().keys.map { it.name }
            assertEquals(listOf("sesion_v1"), claves)
        }

    @Test
    fun `two stores on the same file read the same record`() =
        runTest {
            val primero = Job()
            val r = registro(Objetivo.Tarea("t1"), todosLosEventos)
            SesionStore(dataStore("proceso", primero)).guardar(r)
            // Matar el proceso: el primer DataStore muere y otro abre el mismo archivo.
            primero.cancelAndJoin()
            val otro = SesionStore(dataStore("proceso"))
            assertEquals(r, otro.leer())
            assertEquals(r, otro.registro.first())
        }

    @Test
    fun `clearing the session keeps other keys of the file`() =
        runTest {
            val ds = dataStore("clear")
            val otra = stringPreferencesKey("avisos_retenidos")
            ds.edit { it[otra] = "[]" }
            val store = SesionStore(ds)
            store.guardar(registro(Objetivo.Libre))
            store.borrar()
            assertNull(store.leer())
            assertEquals("[]", ds.data.first()[otra])
        }
}
