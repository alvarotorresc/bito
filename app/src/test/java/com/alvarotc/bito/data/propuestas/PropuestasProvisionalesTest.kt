package com.alvarotc.bito.data.propuestas

import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habi.HabiVoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class PropuestasProvisionalesTest {
    private val vacio: DomainState = domainState()

    private fun fuente(nombre: String) =
        PropuestasProvisionales(
            flowOf(vacio),
            flowOf(Settings(userName = nombre, personality = Personality.NEUTRA)),
            { 0L },
            { ZoneId.of("UTC") },
        )

    @Test
    fun `today greets by name without actions`() =
        runTest {
            val p = fuente("Álvaro").propuesta(Ambito.HOY).first()!!
            assertTrue(p.acciones.isEmpty())
            val texto = p.texto as TextoHabi.Recurso
            assertEquals(HabiVoice.greetingRes(Mood.NORMAL, Personality.NEUTRA), texto.res)
            assertEquals(listOf<Any>("Álvaro"), texto.args)
        }

    @Test
    fun `without a name the fallback travels as a nested text`() =
        runTest {
            val p = fuente("").propuesta(Ambito.HOY).first()!!
            assertEquals(listOf<Any>(TextoHabi(R.string.habi_name_fallback)), (p.texto as TextoHabi.Recurso).args)
        }

    @Test
    fun `the habit sheet gets Habi's comment without actions`() =
        runTest {
            val p = fuente("Álvaro").propuesta(Ambito.FICHA, "h1").first()!!
            assertTrue(p.acciones.isEmpty())
            assertEquals(HabiVoice.bubbleRes(Mood.NORMAL, Personality.NEUTRA), (p.texto as TextoHabi.Recurso).res)
        }

    @Test
    fun `answering does nothing and does not throw`() =
        runTest {
            val fuenteSaludo = fuente("Álvaro")
            val antes = fuenteSaludo.propuesta(Ambito.HOY).first()
            assertEquals(Efecto.Ninguno, fuenteSaludo.responder("saludo", Respuesta.HOY_NO))
            fuenteSaludo.guardarGordo(listOf(RefGordo(TipoRefGordo.HABITO, "h1")))
            assertEquals(antes, fuenteSaludo.propuesta(Ambito.HOY).first())
        }
}
