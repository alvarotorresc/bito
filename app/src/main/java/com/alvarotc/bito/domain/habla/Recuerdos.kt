package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.propuestas.Ambito

/**
 * Qué recuerdo puede decir Habi hoy (spec §5.11, decisión 8 de §15). Solo lo bueno o lo neutro,
 * como mucho uno al día en cualquier superficie, nunca un evento ya mencionado y nada de más de
 * [Ritmo.RECUERDO_MAX_DIAS] días. Los avisos no piden recuerdo.
 */
object Recuerdos {
    private const val BIEN = 0
    private const val DURO = 2
    private const val VUELTA_DESDE_DIAS = 14
    private const val RACHA_MIN = 7
    private const val DURO_DESDE_DIAS = 3
    private const val DURO_HASTA_DIAS = 30
    private const val PATRON_SEMANAS = 6
    private const val PATRON_DUROS = 3
    private const val PATRON = "patron:"

    /**
     * El primero que se cumple en este orden: `re_racha_recuperada`, `re_vuelta`, `re_duro_pasado`,
     * `re_gordo_hecho`, `re_cumpliste_hora`, `re_bien_ayer` y, solo con [enCierre], `re_patron_animo`.
     * Null si hoy ya se mencionó alguno.
     */
    fun elegible(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
        enCierre: Boolean,
    ): Recuerdo? {
        val hoy = h.hoy
        val menciones = m.eventos.filter { it.tipo == TipoMemoria.MENCION }
        if (menciones.any { it.dia == hoy }) return null
        val mencionados = menciones.mapNotNull { it.ref }.toSet()
        // Recientes primero; a igual día, por id, para que el orden no dependa de cómo llegan.
        val eventos =
            m.eventos
                .filter { it.tipo != TipoMemoria.MENCION && it.id !in mencionados && hoy - it.dia in 0..Ritmo.RECUERDO_MAX_DIAS }
                .sortedWith(compareByDescending<EventoMemoria> { it.dia }.thenBy { it.id })
        return rachaRecuperada(h, mencionados)
            ?: vuelta(h, eventos, m)
            ?: duroPasado(h, eventos, m)
            ?: gordoHecho(h, eventos)
            ?: cumplisteHora(h, eventos)
            ?: bienAyer(hoy, eventos)
            ?: if (enCierre) patronAnimo(hoy, m.eventos, menciones, ctx) else null
    }

    /**
     * Dónde puede ir un recuerdo, como cierre de la frase (spec §5.11): saludo, ayer se escapó,
     * vuelta, el cierre del día y el sello (su núcleo). El redactor lo pregunta aquí.
     */
    fun admite(
        situacion: Situacion,
        bolsaNucleo: Bolsa,
    ): Boolean = situacion in CON_RECUERDO || situacion.ambito == Ambito.CIERRE || bolsaNucleo in SELLOS

    private val CON_RECUERDO = setOf(Situacion.SALUDO, Situacion.AYER_SE_ESCAPO, Situacion.VUELTA)
    private val SELLOS = setOf(Bolsa.CIE_SELLO, Bolsa.CIE_SELLO_PERFECTO)

    /** La racha acaba de llegar a 8 tras haber roto una de 7 o más; el evento es el propio día. */
    private fun rachaRecuperada(
        h: Hechos,
        mencionados: Set<String>,
    ): Recuerdo? =
        h.habitos
            .filter { it.rachaRotaRecuperada && !it.enPausa }
            .map { it to "racha:${it.id}:${h.hoy}" }
            .firstOrNull { (_, id) -> id !in mencionados }
            ?.let { (habito, id) -> Recuerdo(Bolsa.RE_RACHA_RECUPERADA, id, huecosRacha(habito)) }

    /**
     * La última vuelta, si fue hace 14 días o más, con el hábito de mejor racha (7 o más). Una vuelta
     * anterior no cuenta: la última corta su «desde entonces».
     */
    private fun vuelta(
        h: Hechos,
        eventos: List<EventoMemoria>,
        m: Memoria,
    ): Recuerdo? {
        val ultima = m.eventos.filter { it.tipo == TipoMemoria.VUELTA }.maxByOrNull { it.dia } ?: return null
        val evento = eventos.firstOrNull { it.id == ultima.id } ?: return null
        if (h.hoy - evento.dia < VUELTA_DESDE_DIAS) return null
        val habito = h.habitos.filter { !it.enPausa && it.racha >= RACHA_MIN }.maxByOrNull { it.racha } ?: return null
        return Recuerdo(Bolsa.RE_VUELTA, evento.id, huecosRacha(habito))
    }

    /**
     * El último día duro, si fue hace 3 a 30 días, y ayer o hoy fue bien (ánimo «Bien») o hoy va
     * perfecto. Un duro anterior no cuenta: «aquel día duro» es el último.
     */
    private fun duroPasado(
        h: Hechos,
        eventos: List<EventoMemoria>,
        m: Memoria,
    ): Recuerdo? {
        val duros = m.eventos.filter { it.tipo == TipoMemoria.ANIMO && it.valor == DURO && it.dia <= h.hoy }
        val ultimo = duros.maxByOrNull { it.dia } ?: return null
        if (h.hoy - ultimo.dia !in DURO_DESDE_DIAS..DURO_HASTA_DIAS) return null
        val evento = eventos.firstOrNull { it.id == ultimo.id } ?: return null
        val bien = m.eventos.any { it.tipo == TipoMemoria.ANIMO && it.valor == BIEN && h.hoy - it.dia in 0..1 }
        val perfecto = h.tuDiaTotal > 0 && h.tuDiaHechos == h.tuDiaTotal
        return if (bien || perfecto) Recuerdo(Bolsa.RE_DURO_PASADO, evento.id, emptyMap()) else null
    }

    /**
     * Lo gordo de ayer, entero: `valor` = hechos * 10 + total. Un parcial no se recuerda. `{tarea}` es la primera
     * tarea de lo gordo hecha ayer (HUECOS.md); si lo gordo de ayer no tenía tareas, no hay recuerdo.
     */
    private fun gordoHecho(
        h: Hechos,
        eventos: List<EventoMemoria>,
    ): Recuerdo? {
        val tarea = h.tareaGordoAyerHecha ?: return null
        return eventos
            .firstOrNull { e ->
                e.tipo == TipoMemoria.GORDO_HECHO && e.dia == h.hoy - 1 && e.valor.let { it == null || (it / 10 > 0 && it / 10 == it % 10) }
            }?.let { Recuerdo(Bolsa.RE_GORDO_HECHO, it.id, mapOf(Hueco.TAREA to ValorHueco.Texto(tarea))) }
    }

    /** Un aviso aceptado de ayer que se cumplió: `valor` = minuto, `ref` = hábito si sigue aquí. */
    private fun cumplisteHora(
        h: Hechos,
        eventos: List<EventoMemoria>,
    ): Recuerdo? {
        val e =
            eventos
                .filter { it.tipo == TipoMemoria.ACEPTADO_CUMPLIDO && it.dia == h.hoy - 1 && it.valor != null }
                .minByOrNull { it.creadoMillis } ?: return null
        val huecos = mutableMapOf<Hueco, ValorHueco>(Hueco.HORA to ValorHueco.Hora(e.valor!!))
        h.habitos.firstOrNull { it.id == e.ref }?.let { huecos[Hueco.HABITO] = ValorHueco.Texto(it.nombre) }
        return Recuerdo(Bolsa.RE_CUMPLISTE_HORA, e.id, huecos)
    }

    private fun bienAyer(
        hoy: LogicalDay,
        eventos: List<EventoMemoria>,
    ): Recuerdo? =
        eventos
            .firstOrNull { it.tipo == TipoMemoria.ANIMO && it.valor == BIEN && it.dia == hoy - 1 }
            ?.let { Recuerdo(Bolsa.RE_BIEN_AYER, it.id, emptyMap()) }

    /**
     * 3 o más de las 6 últimas veces de este día de la semana fueron duras. No tiene evento propio:
     * se identifica como `patron:<día de la semana>:<hoy>` y vuelve como pronto a los 30 días.
     */
    private fun patronAnimo(
        hoy: LogicalDay,
        todos: List<EventoMemoria>,
        menciones: List<EventoMemoria>,
        ctx: Contexto,
    ): Recuerdo? {
        if (menciones.any { it.ref.orEmpty().startsWith(PATRON) && hoy - it.dia < Ritmo.PATRON_ANIMO_CADA_DIAS }) return null
        val dias = (1..PATRON_SEMANAS).map { hoy - 7 * it }.toSet()
        val duros = todos.filter { it.tipo == TipoMemoria.ANIMO && it.valor == DURO && it.dia in dias }.map { it.dia }.distinct()
        if (duros.size < PATRON_DUROS) return null
        return Recuerdo(
            Bolsa.RE_PATRON_ANIMO,
            "$PATRON${ctx.diaSemana}:$hoy",
            mapOf(Hueco.DIA_SEMANA to ValorHueco.DiaSemana(ctx.diaSemana)),
        )
    }

    private fun huecosRacha(habito: HabitoHoy): Map<Hueco, ValorHueco> =
        mapOf(Hueco.HABITO to ValorHueco.Texto(habito.nombre), Hueco.RACHA to ValorHueco.Numero(habito.racha))
}
