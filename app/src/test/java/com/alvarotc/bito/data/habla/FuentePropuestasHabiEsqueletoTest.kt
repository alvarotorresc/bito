package com.alvarotc.bito.data.habla

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.habla.CatalogoHablaVacio
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.notifications.AvisosHabiNulo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/** El esqueleto de la Ola 0: en Hoy saluda como la pieza 3 y lo gordo ya se guarda. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FuentePropuestasHabiEsqueletoTest {
    private lateinit var db: BitoDatabase
    private val ahora = 1_755_252_000_000L // 2025-08-15T10:00:00Z
    private val utc = ZoneId.of("UTC")
    private val hoy = LogicalDays.logicalDayOf(ahora, 0, utc)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun fuente(nombre: String) =
        FuentePropuestasHabi(
            estado = flowOf(domainState()),
            ajustes = flowOf(Settings(userName = nombre, personality = Personality.NEUTRA)),
            recordatorios = flowOf(emptyMap()),
            memoria = MemoriaRepository(db) { ahora },
            vigentes = PropuestaVigenteStore(db),
            gordo = LoGordoRepository(db) { ahora },
            aceptados = AvisosAceptadosRepository(db),
            catalogo = { CatalogoHablaVacio },
            avisos = AvisosHabiNulo,
            permisos = { PermisosAvisos(notificaciones = true, alarmasExactas = true) },
            now = { ahora },
            zone = { utc },
        )

    @Test
    fun `today greets as before, without actions and with the greeting id`() =
        runTest {
            val p = fuente("Álvaro").propuesta(Ambito.HOY).first()!!

            assertEquals("SALUDO::$hoy", p.id)
            assertTrue(p.acciones.isEmpty())
            val texto = p.texto as TextoHabi.Recurso
            assertEquals(HabiVoice.greetingRes(Mood.NORMAL, Personality.NEUTRA), texto.res)
            assertEquals(listOf<Any>("Álvaro"), texto.args)
        }

    @Test
    fun `without a name the greeting carries the fallback`() =
        runTest {
            val p = fuente("  ").propuesta(Ambito.HOY).first()!!
            assertEquals(listOf<Any>(TextoHabi(R.string.habi_name_fallback)), (p.texto as TextoHabi.Recurso).args)
        }

    @Test
    fun `the habit sheet and Camino say what they said, the close and the rest say nothing`() =
        runTest {
            val f = fuente("Álvaro")
            val comentario = HabiVoice.bubbleRes(Mood.NORMAL, Personality.NEUTRA)
            assertEquals(comentario, (f.propuesta(Ambito.FICHA, "h1").first()!!.texto as TextoHabi.Recurso).res)
            assertEquals(comentario, (f.propuesta(Ambito.CAMINO).first()!!.texto as TextoHabi.Recurso).res)
            assertNull(f.propuesta(Ambito.CIERRE).first())
            assertNull(f.propuesta(Ambito.DESCANSO).first())
        }

    @Test
    fun `answering has no effect yet`() =
        runTest {
            assertEquals(Efecto.Ninguno, fuente("Álvaro").responder("SALUDO::$hoy", Respuesta.VALE))
        }

    @Test
    fun `saving the big thing leaves its refs on today`() =
        runTest {
            val refs = listOf(RefGordo(TipoRefGordo.TAREA, "t1"), RefGordo(TipoRefGordo.HABITO, "h1"))

            fuente("Álvaro").guardarGordo(refs)

            assertEquals(refs, LoGordoRepository(db).observe(listOf(hoy)).first()[hoy])
        }
}
