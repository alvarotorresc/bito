package com.alvarotc.bito.ui.notifications

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.habla.CatalogoHablaVacio
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.habla.idPropuesta
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Accion
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** El receptor de las acciones de los avisos: lo que llevan las acciones publicadas es lo que el receptor lee. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AvisoHabiReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val hora = 14 * 60 + 30

    private val vigente =
        Propuesta(
            id = idPropuesta(Situacion.AYER_SE_ESCAPO, "h1", TODAY),
            texto = TextoHabi(R.string.habla_ahora),
            acciones =
                listOf(
                    Accion(Respuesta.VALE, TextoHabi(R.string.habla_vale), principal = true),
                    Accion(Respuesta.LUEGO, TextoHabi(R.string.habla_luego)),
                ),
            hora = hora,
        )

    private val respuestas = mutableListOf<Triple<String, Respuesta, Int?>>()

    private val fuente =
        object : FuentePropuestas {
            override fun propuesta(
                ambito: Ambito,
                habitId: String?,
            ): Flow<Propuesta?> = flowOf(vigente)

            override suspend fun responder(
                id: String,
                respuesta: Respuesta,
                hora: Int?,
            ): Efecto {
                respuestas += Triple(id, respuesta, hora)
                return Efecto.Ninguno
            }

            override suspend fun guardarGordo(refs: List<RefGordo>) = Unit
        }

    private val avisos =
        AvisosHabiAndroid(
            context,
            contenedor = { error("sin contenedor en los tests") },
            plan = { PlanDeHoy(hechos(), Memoria(), ctx()) },
            fuente = { fuente },
            aceptados = { error("una respuesta que no es «Vale» no toca los aceptados") },
            catalogo = { CatalogoHablaVacio },
        )

    @Before
    fun setUp() {
        NotificationChannels.ensure(context)
        shadowOf(manager).setNotificationsEnabled(true)
    }

    @Test
    fun `the receiver is declared in the manifest`() {
        val receivers = context.packageManager.queryBroadcastReceivers(Intent(context, AvisoHabiReceiver::class.java), 0)

        assertFalse(receivers.isEmpty())
    }

    @Test
    fun `an unknown action leaves the morning notice alone`() =
        runTest {
            Notifier.showManana(context, vigente, TextoHabi(R.string.habla_ahora), Personality.NEUTRA, Mood.NORMAL)

            avisos.accion(Intent("com.alvarotc.bito.accion.desconocida").putExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID, vigente.id))

            assertNotNull(shadowOf(manager).getNotification(Notifier.MANANA_ID))
            assertTrue(respuestas.isEmpty())

            avisos.accion(Intent(AvisoHabiReceiver.ACTION_HOY_NO).putExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID, vigente.id))

            assertNull(shadowOf(manager).getNotification(Notifier.MANANA_ID))
            assertEquals(1, respuestas.size)
        }

    @Test
    fun `the morning actions carry what the receiver reads`() =
        runTest {
            Notifier.showManana(context, vigente, TextoHabi(R.string.habla_ahora), Personality.NEUTRA, Mood.NORMAL)
            val acciones = shadowOf(manager).getNotification(Notifier.MANANA_ID)!!.actions

            val vale = shadowOf(acciones[0].actionIntent).savedIntent
            assertEquals(AvisoHabiReceiver::class.java.name, vale.component?.className)
            assertEquals(AvisoHabiReceiver.ACTION_VALE, vale.action)
            assertEquals(vigente.id, vale.getStringExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID))
            assertEquals(hora, vale.getIntExtra(AvisoHabiReceiver.EXTRA_HORA, -1))

            val luego = shadowOf(acciones[1].actionIntent).savedIntent
            assertEquals(AvisoHabiReceiver.ACTION_HOY_NO, luego.action)
            avisos.accion(luego)

            assertEquals(listOf<Triple<String, Respuesta, Int?>>(Triple(vigente.id, Respuesta.LUEGO, null)), respuestas)
            assertNull(shadowOf(manager).getNotification(Notifier.MANANA_ID))
        }

    @Test
    fun `the later action of an accepted reminder names its notice`() {
        Notifier.showRecordatorioAceptado(context, "a1", 0, "Leer", TextoHabi(R.string.habla_hecho), null, true, Personality.NEUTRA)

        val luego = shadowOf(shadowOf(manager).getNotification(Notifier.aceptadoId(0))!!.actions.single().actionIntent).savedIntent

        assertEquals(AvisoHabiReceiver::class.java.name, luego.component?.className)
        assertEquals(AvisoHabiReceiver.ACTION_MAS_TARDE, luego.action)
        assertEquals("a1", luego.getStringExtra(AvisoHabiReceiver.EXTRA_AVISO_ID))
    }
}
