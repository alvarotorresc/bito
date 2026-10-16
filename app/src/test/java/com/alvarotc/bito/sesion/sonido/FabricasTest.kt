package com.alvarotc.bito.sesion.sonido

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.ui.sistema.TactoMudo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** Los colectores de ajustes de `crearAire` y `crearFondo` cuelgan del alcance que se les da y no sueltan excepciones (revisión final, I3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FabricasTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Ajustes cuyo archivo ya no está: leerlos lanza, como el DataStore de un contenedor muerto. */
    private val ajustesRotos =
        SettingsRepository(
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow { throw IOException("el directorio temporal ya no existe") }

                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException()
            },
        )

    private fun alcanceQueApunta(errores: MutableList<Throwable>) =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> errores += e })

    @Test
    fun `a failing settings read does not escape from the air or the background collectors`() {
        val errores = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val alcance = alcanceQueApunta(errores)

        crearAire(context, com.alvarotc.bito.sesion.SonidoGrabado(), TactoMudo, ajustesRotos, alcance)
        crearFondo(context, com.alvarotc.bito.sesion.SonidoGrabado(), ajustesRotos, alcance)
        runBlocking { alcance.coroutineContext[Job]!!.children.forEach { it.join() } }

        assertEquals(emptyList<Throwable>(), errores.toList())
        alcance.cancel()
    }

    @Test
    fun `cancelling the scope given to the factories stops their collectors`() {
        val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        crearAire(context, com.alvarotc.bito.sesion.SonidoGrabado(), TactoMudo, ajustesRotos, alcance)
        crearFondo(context, com.alvarotc.bito.sesion.SonidoGrabado(), ajustesRotos, alcance)
        val job = alcance.coroutineContext[Job]!!

        alcance.cancel()

        assertTrue(job.isCancelled)
        assertFalse(job.children.any { it.isActive })
    }
}
