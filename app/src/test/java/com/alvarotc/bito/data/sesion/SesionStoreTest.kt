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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
    fun `a record round-trips with every event and every goal`() =
        runTest {
            val store = SesionStore(dataStore("roundtrip"))
            for (r in listOf(
                registro(Objetivo.Tarea("t1"), todosLosEventos, filtroPrevio = 1),
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
