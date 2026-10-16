package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.habla.reglas.ReglaAyerSeEscapo
import com.alvarotc.bito.domain.habla.reglas.ReglaCerrarDia
import com.alvarotc.bito.domain.habla.reglas.ReglaDiaDuro
import com.alvarotc.bito.domain.habla.reglas.ReglaDiaFlojo
import com.alvarotc.bito.domain.habla.reglas.ReglaElegirGordo
import com.alvarotc.bito.domain.habla.reglas.ReglaGordoSinEmpezar
import com.alvarotc.bito.domain.habla.reglas.ReglaHoraHabitual
import com.alvarotc.bito.domain.habla.reglas.ReglaPermisoAvisos
import com.alvarotc.bito.domain.habla.reglas.ReglaRachaCerca
import com.alvarotc.bito.domain.habla.reglas.ReglaSaludo
import com.alvarotc.bito.domain.habla.reglas.ReglaTareaVence
import com.alvarotc.bito.domain.habla.reglas.ReglaTodoHecho
import com.alvarotc.bito.domain.habla.reglas.ReglaVuelta
import com.alvarotc.bito.domain.habla.reglas.ReglasCamino
import com.alvarotc.bito.domain.habla.reglas.ReglasCierre
import com.alvarotc.bito.domain.habla.reglas.ReglasFicha
import com.alvarotc.bito.domain.habla.reglas.Respuestas
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * Elige qué dice Habi en un ámbito (spec §4.3, §5.2): la primera situación de [Prioridades] con
 * candidatas que pase los enfriamientos. La ficha, Camino y el cierre salen de sus reglas; el
 * descanso no dice nada.
 */
object Motor {
    fun elegir(
        ambito: Ambito,
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
        habitId: String? = null,
    ): Candidata? =
        when (ambito) {
            Ambito.HOY -> elegirHoy(h, m, ctx)
            Ambito.FICHA -> elegirFicha(h, m, ctx, habitId)
            Ambito.CAMINO -> primeraPorPrioridad(Prioridades.CAMINO, ReglasCamino.candidatas(h, m, ctx))
            Ambito.CIERRE -> primeraPorPrioridad(Prioridades.CIERRE, ReglasCierre.candidatas(h, m, ctx))
            Ambito.DESCANSO -> null
        }

    /** Las reglas de Hoy, por situación. */
    internal val REGLAS_HOY: Map<Situacion, Regla> =
        mapOf(
            Situacion.PERMISO_AVISOS to ReglaPermisoAvisos,
            Situacion.VUELTA to ReglaVuelta,
            Situacion.DIA_DURO to ReglaDiaDuro,
            Situacion.CERRAR_DIA to ReglaCerrarDia,
            Situacion.TODO_HECHO to ReglaTodoHecho,
            Situacion.ELEGIR_GORDO to ReglaElegirGordo,
            Situacion.GORDO_SIN_EMPEZAR to ReglaGordoSinEmpezar,
            Situacion.AYER_SE_ESCAPO to ReglaAyerSeEscapo,
            Situacion.RACHA_CERCA to ReglaRachaCerca,
            Situacion.DIA_FLOJO to ReglaDiaFlojo,
            Situacion.HORA_HABITUAL to ReglaHoraHabitual,
            Situacion.TAREA_VENCE to ReglaTareaVence,
            Situacion.SALUDO to ReglaSaludo,
        )

    private const val MILLIS_MIN = 60_000L
    private const val FICHA_OCULTA_DIAS = 7
    private const val ANIMO_REGULAR = 1
    private val POR_PESO = compareByDescending<Candidata> { it.peso }.thenBy { it.ref }
    private val RESPUESTAS_QUE_ESPERAN = setOf(Respuesta.VALE, Respuesta.AHORA, Respuesta.LUEGO, Respuesta.HOY_NO)

    /**
     * Hoy (spec §5.1, §5.2, §5.5): con el día sellado, la noche. Si no, la primera situación de
     * [Prioridades.HOY] con candidatas que pasen los filtros, y dentro de ella la de más peso
     * (empate: `ref`). Los filtros solo tocan a las situaciones que cuentan para el tope; las que
     * no cuentan («Cerrar el día», «Todo hecho») solo esperan tras su propio «Todavía no».
     */
    internal fun elegirHoy(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
        reglas: Map<Situacion, Regla> = REGLAS_HOY,
    ): Candidata {
        if (h.diaSellado) return Respuestas.noche(h)
        val bloqueadas = topeAlcanzado(h, m) || enEspera(h, m, ctx)
        val primeraDelDia = m.mostradasHoy.none { it.situacion.cuentaTope }
        for (situacion in Prioridades.HOY) {
            if (situacion == Situacion.SALUDO) break
            if (situacion == Situacion.ELEGIR_GORDO && h.diaDuro) continue
            val regla = reglas[situacion] ?: continue
            val validas =
                regla
                    .candidatas(h, m, ctx)
                    .filterNot { proponeLimiteODejar(it, h) }
                    .filter { if (situacion.cuentaTope) !bloqueadas && !descartadaHoy(it, m, ctx) else !luegoReciente(it, m, ctx) }
            if (validas.isEmpty()) continue
            val regular = h.animoAyer == ANIMO_REGULAR && situacion.cuentaTope && primeraDelDia
            val elegida = if (regular) masCorta(validas, h) else validas.sortedWith(POR_PESO).first()
            return sinHoraSiHayDosAvisos(elegida, m, ctx)
        }
        return reglas[Situacion.SALUDO]?.candidatas(h, m, ctx)?.firstOrNull()
            ?: Candidata(Situacion.SALUDO, null, emptyMap(), emptyList())
    }

    /**
     * §5.7: la primera nota de la ficha que «No hace falta» no haya descartado en los últimos 7 días
     * (esa situación en ese hábito). Archivado o sin hábito, nada. Con 2 avisos aceptados hoy (§5.2)
     * no sale `FI_HOY_FALTA`: solo existe «con hora posible» y sin hora no tiene acciones propias.
     */
    private fun elegirFicha(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
        habitId: String?,
    ): Candidata? {
        val ocultas =
            m.eventos
                .filter { it.tipo == TipoMemoria.RESPUESTA && respuestaDe(it) == Respuesta.HOY_NO && ctx.hoy - it.dia < FICHA_OCULTA_DIAS }
                .mapNotNull { it.ref?.substringBeforeLast(':') }
                .toSet()
        val topeAvisos = dosAvisosHoy(m, ctx)
        val validas =
            ReglasFicha
                .candidatas(h, m, ctx, habitId)
                .filter { "${it.situacion.name}:${it.ref.orEmpty()}" !in ocultas }
                .filterNot { topeAvisos && it.situacion == Situacion.FI_HOY_FALTA }
        return primeraPorPrioridad(Prioridades.FICHA, validas)
    }

    /** La primera situación de [prioridades] con candidatas y, dentro de ella, la de más peso (empate: `ref`). */
    private fun primeraPorPrioridad(
        prioridades: List<Situacion>,
        candidatas: List<Candidata>,
    ): Candidata? = prioridades.firstNotNullOfOrNull { s -> candidatas.filter { it.situacion == s }.minWithOrNull(POR_PESO) }

    /** §5.2 y §5.5: 4 propuestas con botones al día; 2 en un día duro. */
    private fun topeAlcanzado(
        h: Hechos,
        m: Memoria,
    ): Boolean {
        val tope = if (h.diaDuro) Ritmo.DIA_DURO_TOPE else Ritmo.TOPE_PROPUESTAS_DIA
        return m.mostradasHoy.count { it.situacion.cuentaTope } >= tope
    }

    /**
     * §5.2: tras «Vale», «Ahora», «Luego» u «Hoy no», 90 min sin propuestas con botones, salvo que lo
     * propuesto en la última respuesta ya esté hecho desde la lista.
     */
    private fun enEspera(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): Boolean {
        val ultima =
            m.eventos
                .filter { it.tipo == TipoMemoria.RESPUESTA && respuestaDe(it) in RESPUESTAS_QUE_ESPERAN }
                .filter { ctx.ahoraMillis - it.creadoMillis < Ritmo.ESPERA_TRAS_RESPONDER_MIN * MILLIS_MIN }
                .maxByOrNull { it.creadoMillis } ?: return false
        val ref = ultima.ref?.substringAfter(':')?.substringBeforeLast(':').orEmpty()
        val hecho = ref.isNotEmpty() && (h.habitos.any { it.id == ref && it.hechoHoy } || h.tareas.any { it.id == ref && it.hecha })
        return !hecho
    }

    /** §5.2: «Hoy no» descarta esa situación con esa ref hasta mañana. */
    private fun descartadaHoy(
        c: Candidata,
        m: Memoria,
        ctx: Contexto,
    ): Boolean {
        val id = idPropuesta(c.situacion, c.ref, ctx.hoy)
        return m.eventos.any { it.tipo == TipoMemoria.RESPUESTA && it.ref == id && respuestaDe(it) == Respuesta.HOY_NO }
    }

    /**
     * §5.2: «Todavía no» (`LUEGO`) en una propuesta que no cuenta para el tope la deja volver pasada la
     * misma espera que el resto de respuestas: esa situación con esa ref, hoy.
     */
    private fun luegoReciente(
        c: Candidata,
        m: Memoria,
        ctx: Contexto,
    ): Boolean {
        val id = idPropuesta(c.situacion, c.ref, ctx.hoy)
        return m.eventos.any {
            it.tipo == TipoMemoria.RESPUESTA &&
                it.ref == id &&
                respuestaDe(it) == Respuesta.LUEGO &&
                ctx.ahoraMillis - it.creadoMillis < Ritmo.ESPERA_TRAS_RESPONDER_MIN * MILLIS_MIN
        }
    }

    private fun respuestaDe(e: EventoMemoria): Respuesta? = e.valor?.let { Respuesta.entries.getOrNull(it) }

    /** Dejar del todo y dejar con límite nunca se proponen, lo diga la regla que lo diga. */
    private fun proponeLimiteODejar(
        c: Candidata,
        h: Hechos,
    ): Boolean {
        val id = (c.destino as? Destino.Habito)?.id ?: return false
        val tipo = h.habitos.firstOrNull { it.id == id }?.tipo
        return tipo == TipoHabito.LIMITE || tipo == TipoHabito.DEJAR
    }

    /**
     * §5.5, ayer regular: la primera propuesta con botones es la de menor objetivo de su situación,
     * con el núcleo de arranque corto. Solo para hábitos: el núcleo lleva `{habito}`.
     */
    private fun masCorta(
        validas: List<Candidata>,
        h: Hechos,
    ): Candidata {
        val objetivos = h.habitos.associate { it.id to it.objetivo }
        val deHabito = validas.filter { it.destino is Destino.Habito && it.ref in objetivos }
        if (deHabito.isEmpty()) return validas.sortedWith(POR_PESO).first()
        val corta = deHabito.sortedWith(compareBy<Candidata> { objetivos.getValue(it.ref!!) }.thenBy { it.ref }).first()
        return corta.copy(nucleo = Bolsa.NU_REGULAR)
    }

    /**
     * §5.2: como mucho 2 avisos aceptados al día. La misma cuenta para Hoy, la ficha y el plan de avisos
     * ([PlanAvisos.cuentaParaElTope]): un aviso cancelado por «Otra hora» no gasta el tope.
     */
    internal fun dosAvisosHoy(
        m: Memoria,
        ctx: Contexto,
    ): Boolean = m.avisos.count { it.dia == ctx.hoy && PlanAvisos.cuentaParaElTope(it) } >= Ritmo.TOPE_ACEPTADOS_DIA

    /** §5.2: con 2 avisos aceptados hoy, una propuesta con hora sale sin ella y sin «Vale». */
    private fun sinHoraSiHayDosAvisos(
        c: Candidata,
        m: Memoria,
        ctx: Contexto,
    ): Candidata {
        if (!c.situacion.cuentaTope || c.hora == null) return c
        if (!dosAvisosHoy(m, ctx)) return c
        val acciones = c.acciones.map { if (it == Respuesta.VALE) Respuesta.AHORA else it }.distinct()
        return c.copy(acciones = acciones, hora = null, huecos = c.huecos - Hueco.HORA)
    }
}

/** El motor como dependencia: la fuente recibe [Motor.elegir] y los tests, un falso. */
fun interface ElegirCandidata {
    fun elegir(
        ambito: Ambito,
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
        habitId: String?,
    ): Candidata?
}
