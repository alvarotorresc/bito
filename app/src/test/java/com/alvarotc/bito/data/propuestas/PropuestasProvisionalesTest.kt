package com.alvarotc.bito.data.propuestas

import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
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
            assertEquals(HabiVoice.greetingRes(Mood.NORMAL, Personality.NEUTRA), p.texto.res)
            assertEquals(listOf<Any>("Álvaro"), p.texto.args)
        }

    @Test
    fun `without a name the fallback travels as a nested text`() =
        runTest {
            val p = fuente("").propuesta(Ambito.HOY).first()!!
            assertEquals(listOf<Any>(TextoHabi(R.string.habi_name_fallback)), p.texto.args)
        }

    @Test
    fun `the habit sheet gets Habi's comment without actions`() =
        runTest {
            val p = fuente("Álvaro").propuesta(Ambito.FICHA, "h1").first()!!
            assertTrue(p.acciones.isEmpty())
            assertEquals(HabiVoice.bubbleRes(Mood.NORMAL, Personality.NEUTRA), p.texto.res)
        }

    @Test
    fun `answering leaves the proposal unchanged`() =
        runTest {
            val fuenteSaludo = fuente("Álvaro")
            val antes = fuenteSaludo.propuesta(Ambito.HOY).first()
            fuenteSaludo.responder("saludo", Respuesta.HOY_NO)
            val despues = fuenteSaludo.propuesta(Ambito.HOY).first()
            assertEquals(antes, despues)
        }
}
