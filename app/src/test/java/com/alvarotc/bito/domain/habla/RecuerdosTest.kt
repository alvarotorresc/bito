package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class RecuerdosTest {
    private fun ev(
        id: String,
        tipo: TipoMemoria,
        dia: LogicalDay,
        valor: Int? = null,
        ref: String? = null,
    ) = EventoMemoria(id, tipo, dia, ref, valor, 0L)

    private fun animo(
        id: String,
        haceDias: Int,
        valor: Int,
    ) = ev(id, TipoMemoria.ANIMO, TODAY - haceDias, valor)

    private fun mencion(
        ref: String,
        haceDias: Int = 1,
    ) = ev("m-$ref-$haceDias", TipoMemoria.MENCION, TODAY - haceDias, ref = ref)

    private fun vuelta(haceDias: Int) = ev("vuelta", TipoMemoria.VUELTA, TODAY - haceDias, 4)

    private val enRacha = habito(id = "h2", nombre = "Correr", racha = 7)
    private val recuperada = habito().copy(racha = 8, rachaRotaRecuperada = true)

    private fun elegible(
        h: Hechos = hechos(),
        eventos: List<EventoMemoria> = emptyList(),
        enCierre: Boolean = false,
    ) = Recuerdos.elegible(h, Memoria(eventos = eventos), ctx(), enCierre)

    @Test
    fun `a streak that came back is recalled with the habit and the streak`() {
        val r = elegible(hechos(habitos = listOf(recuperada)))
        assertEquals(
            Recuerdo(
                Bolsa.RE_RACHA_RECUPERADA,
                "racha:h1:$TODAY",
                mapOf(Hueco.HABITO to ValorHueco.Texto("Leer"), Hueco.RACHA to ValorHueco.Numero(8)),
            ),
            r,
        )
        assertNull(elegible(hechos(habitos = listOf(habito(racha = 8)))))
    }

    @Test
    fun `a comeback is recalled from fourteen days on with a streak of seven`() {
        val h = hechos(habitos = listOf(habito(racha = 3), enRacha))
        assertEquals(
            Recuerdo(
                Bolsa.RE_VUELTA,
                "vuelta",
                mapOf(Hueco.HABITO to ValorHueco.Texto("Correr"), Hueco.RACHA to ValorHueco.Numero(7)),
            ),
            elegible(h, listOf(vuelta(14))),
        )
        assertNull(elegible(h, listOf(vuelta(13))))
        assertNull(elegible(hechos(habitos = listOf(enRacha.copy(racha = 6))), listOf(vuelta(14))))
    }

    @Test
    fun `a hard day is recalled from three to thirty days after when yesterday went well`() {
        fun con(haceDias: Int) = elegible(eventos = listOf(animo("duro", haceDias, 2), animo("bien", 1, 0)))
        // Sin el duro que toca, sale el siguiente: el buen día de ayer.
        assertEquals(Bolsa.RE_BIEN_AYER, con(2)?.bolsa)
        assertEquals(Recuerdo(Bolsa.RE_DURO_PASADO, "duro", emptyMap()), con(3))
        assertEquals(Bolsa.RE_DURO_PASADO, con(30)?.bolsa)
        assertEquals(Bolsa.RE_BIEN_AYER, con(31)?.bolsa)
    }

    @Test
    fun `a hard day needs a good yesterday or a perfect today`() {
        val duro = animo("duro", 5, 2)
        assertNull(elegible(eventos = listOf(duro, animo("regular", 1, 1))))
        val perfecto = hechos(habitos = listOf(habito(hechoHoy = true)))
        assertEquals(Bolsa.RE_DURO_PASADO, elegible(perfecto, listOf(duro))?.bolsa)
        assertEquals(Bolsa.RE_DURO_PASADO, elegible(eventos = listOf(duro, animo("hoy", 0, 0)))?.bolsa)
    }

    @Test
    fun `the big thing done yesterday is recalled`() {
        val gordo = ev("gordo", TipoMemoria.GORDO_HECHO, TODAY - 1, 22)
        assertEquals(Recuerdo(Bolsa.RE_GORDO_HECHO, "gordo", emptyMap()), elegible(eventos = listOf(gordo)))
        assertNull(elegible(eventos = listOf(gordo.copy(dia = TODAY - 2))))
    }

    @Test
    fun `a time kept is recalled only from yesterday`() {
        val cumplido = ev("cumplido", TipoMemoria.ACEPTADO_CUMPLIDO, TODAY - 1, 18 * 60 + 30, ref = "h1")
        val h = hechos(habitos = listOf(habito()))
        assertEquals(
            Recuerdo(
                Bolsa.RE_CUMPLISTE_HORA,
                "cumplido",
                mapOf(Hueco.HORA to ValorHueco.Hora(18 * 60 + 30), Hueco.HABITO to ValorHueco.Texto("Leer")),
            ),
            elegible(h, listOf(cumplido)),
        )
        assertEquals(
            Recuerdo(Bolsa.RE_CUMPLISTE_HORA, "cumplido", mapOf(Hueco.HORA to ValorHueco.Hora(18 * 60 + 30))),
            elegible(hechos(), listOf(cumplido)),
        )
        assertNull(elegible(h, listOf(cumplido.copy(dia = TODAY))))
        assertNull(elegible(h, listOf(cumplido.copy(dia = TODAY - 2))))
    }

    @Test
    fun `a good yesterday is recalled`() {
        assertEquals(Recuerdo(Bolsa.RE_BIEN_AYER, "bien", emptyMap()), elegible(eventos = listOf(animo("bien", 1, 0))))
        assertNull(elegible(eventos = listOf(animo("bien", 2, 0))))
    }

    @Test
    fun `the mood pattern of the weekday only in the closing and once a month`() {
        val duros = (1..3).map { animo("duro$it", 7 * it, 2) }
        assertEquals(
            Recuerdo(Bolsa.RE_PATRON_ANIMO, "patron:FRIDAY:$TODAY", mapOf(Hueco.DIA_SEMANA to ValorHueco.DiaSemana(DayOfWeek.FRIDAY))),
            elegible(eventos = duros, enCierre = true),
        )
        assertNull(elegible(eventos = duros, enCierre = false))
        assertNull(elegible(eventos = duros.take(2) + animo("otro", 8, 2), enCierre = true))
        assertNull(elegible(eventos = duros + mencion("patron:FRIDAY:${TODAY - 29}", 29), enCierre = true))
        assertNotNull(elegible(eventos = duros + mencion("patron:FRIDAY:${TODAY - 30}", 30), enCierre = true))
    }

    @Test
    fun `the order of preference`() {
        val h = hechos(habitos = listOf(recuperada, enRacha))
        val eventos =
            listOf(
                vuelta(20),
                animo("duro", 4, 2),
                animo("bien", 1, 0),
                ev("gordo", TipoMemoria.GORDO_HECHO, TODAY - 1, 11),
                ev("cumplido", TipoMemoria.ACEPTADO_CUMPLIDO, TODAY - 1, 600, ref = "h1"),
            ) + (1..3).map { animo("duro-$it", 7 * it, 2) }
        val orden = mutableListOf<Bolsa>()
        var menciones = emptyList<EventoMemoria>()
        while (true) {
            val r = elegible(h, eventos + menciones, enCierre = true) ?: break
            orden += r.bolsa
            menciones = menciones + mencion(r.eventoId)
        }
        assertEquals(
            listOf(
                Bolsa.RE_RACHA_RECUPERADA,
                Bolsa.RE_VUELTA,
                Bolsa.RE_DURO_PASADO,
                Bolsa.RE_GORDO_HECHO,
                Bolsa.RE_CUMPLISTE_HORA,
                Bolsa.RE_BIEN_AYER,
                Bolsa.RE_PATRON_ANIMO,
            ),
            orden,
        )
    }

    @Test
    fun `a mention today blocks any other memory`() {
        val bien = animo("bien", 1, 0)
        assertNotNull(elegible(eventos = listOf(bien, mencion("otro", haceDias = 1))))
        assertNull(elegible(eventos = listOf(bien, mencion("otro", haceDias = 0))))
    }

    @Test
    fun `an event mentioned once is never mentioned again`() {
        val gordo = ev("gordo", TipoMemoria.GORDO_HECHO, TODAY - 1, 11)
        val bien = animo("bien", 1, 0)
        assertEquals(Bolsa.RE_GORDO_HECHO, elegible(eventos = listOf(gordo, bien))?.bolsa)
        assertEquals(Bolsa.RE_BIEN_AYER, elegible(eventos = listOf(gordo, bien, mencion("gordo")))?.bolsa)
        assertNull(elegible(eventos = listOf(gordo, bien, mencion("gordo"), mencion("bien", 60))))
        val h = hechos(habitos = listOf(recuperada))
        assertNull(elegible(h, listOf(mencion("racha:h1:$TODAY"))))
    }

    @Test
    fun `nothing older than ninety days`() {
        val h = hechos(habitos = listOf(enRacha))
        assertEquals(Bolsa.RE_VUELTA, elegible(h, listOf(vuelta(Ritmo.RECUERDO_MAX_DIAS)))?.bolsa)
        assertNull(elegible(h, listOf(vuelta(Ritmo.RECUERDO_MAX_DIAS + 1))))
    }

    @Test
    fun `what you did not do is never recalled`() {
        val aceptadoSinCumplir =
            listOf(
                respuesta(Situacion.HORA_HABITUAL, "h1", Respuesta.VALE, 0L, dia = TODAY - 1),
                ev("parcial", TipoMemoria.GORDO_HECHO, TODAY - 1, 12),
                ev("nada", TipoMemoria.GORDO_HECHO, TODAY - 1, 2),
                animo("regular", 1, 1),
            )
        val sinHacer = hechos(habitos = listOf(habito(), habito(id = "h2", falladoAyer = true)))
        assertNull(elegible(sinHacer, aceptadoSinCumplir, enCierre = true))
        val completo = ev("completo", TipoMemoria.GORDO_HECHO, TODAY - 1, 22)
        assertEquals(Bolsa.RE_GORDO_HECHO, elegible(sinHacer, aceptadoSinCumplir + completo, enCierre = true)?.bolsa)
    }
}
