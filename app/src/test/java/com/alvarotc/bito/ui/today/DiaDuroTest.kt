package com.alvarotc.bito.ui.today

import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Mostrada
import com.alvarotc.bito.domain.habla.PropuestaVigente
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** El día duro dura todo el día lógico (spec §5.5, §7.3): sale de lo que Habi recuerda de hoy, no solo de la vigente. */
class DiaDuroTest {
    private val hoy = 20_315

    private fun propuesta(id: String) = Propuesta(id, TextoHabi(R.string.habla_vale), emptyList())

    private fun respuesta(
        id: String,
        dia: Int = hoy,
        r: Respuesta = Respuesta.VALE,
    ) = EventoMemoria("e-$id", TipoMemoria.RESPUESTA, dia, id, r.ordinal, 1L)

    private fun vigente(
        situacion: Situacion,
        ref: String?,
        dia: Int = hoy,
    ) = PropuestaVigente(Ambito.HOY, "", dia, situacion, ref, emptyList(), emptyMap(), emptyList(), null, null, 1L)

    @Test
    fun `the live hard day proposal names its habit`() {
        assertEquals("agua", diaDuroDeHoy(propuesta("DIA_DURO:agua:$hoy"), Memoria(), hoy))
    }

    @Test
    fun `after answering Vale the remembered answer keeps the hard day`() {
        val memoria = Memoria(eventos = listOf(respuesta("DIA_DURO:agua:$hoy")))
        assertEquals("agua", diaDuroDeHoy(propuesta("CONFIRMA_VALE:agua:$hoy"), memoria, hoy))
        assertEquals("agua", diaDuroDeHoy(null, memoria, hoy))
    }

    @Test
    fun `a hard day vigente of today names its habit while another proposal shows`() {
        val memoria = Memoria(vigentes = listOf(vigente(Situacion.DIA_DURO, "agua")))
        assertEquals("agua", diaDuroDeHoy(propuesta("PERMISO_AVISOS::$hoy"), memoria, hoy))
    }

    @Test
    fun `a hard day shown today without a known habit still makes the rest wait`() {
        val memoria = Memoria(mostradasHoy = listOf(Mostrada(Situacion.DIA_DURO, null, 1L)))
        assertEquals("", diaDuroDeHoy(propuesta("PERMISO_AVISOS::$hoy"), memoria, hoy))
    }

    @Test
    fun `a hard day shown, replaced and not answered still names its habit after a restart`() {
        // La fuente apunta en la memoria qué día duro mostró: sin sesión, sin vigente y sin respuesta, se sabe cuál era.
        val memoria =
            Memoria(
                eventos = listOf(EventoMemoria("MOSTRADA:DIA_DURO:agua:$hoy", TipoMemoria.MOSTRADA, hoy, "DIA_DURO:agua:$hoy", null, 1L)),
                mostradasHoy = listOf(Mostrada(Situacion.DIA_DURO, null, 1L)),
            )
        assertEquals("agua", diaDuroDeHoy(propuesta("CERRAR_DIA::$hoy"), memoria, hoy))
    }

    @Test
    fun `the habit last seen this session wins over a shown hard day without ref`() {
        val memoria = Memoria(mostradasHoy = listOf(Mostrada(Situacion.DIA_DURO, null, 1L)))
        assertEquals("agua", diaDuroDeHoy(propuesta("PERMISO_AVISOS::$hoy"), memoria, hoy, vista = "agua"))
    }

    @Test
    fun `guard - what Habi remembers from yesterday is not a hard day today`() {
        val memoria =
            Memoria(
                eventos = listOf(respuesta("DIA_DURO:agua:${hoy - 1}", dia = hoy - 1)),
                vigentes = listOf(vigente(Situacion.DIA_DURO, "agua", dia = hoy - 1)),
            )
        assertNull(diaDuroDeHoy(null, memoria, hoy))
        assertNull(diaDuroDeHoy(propuesta("DIA_DURO:agua:${hoy - 1}"), Memoria(), hoy))
    }

    @Test
    fun `guard - answers to other situations are not a hard day`() {
        val memoria = Memoria(eventos = listOf(respuesta("HORA_HABITUAL:agua:$hoy")))
        assertNull(diaDuroDeHoy(propuesta("SALUDO::$hoy"), memoria, hoy))
    }
}
