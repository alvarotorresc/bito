package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.ContextoDescanso
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import kotlin.math.abs

/**
 * Lo que Habi propone en un descanso de la sesión (spec §12). Candidatos: hábitos exigibles hoy y pendientes, de tipo
 * `CHECK` o `DURACION` (o `CONTADOR` si un solo paso lo cumple), nunca `LIMITE` ni `DEJAR`, ni en pausa, ni el de
 * [ContextoDescanso.excluir], ni uno con «Hoy no» de hoy. En el descanso largo se prefiere una duración que quepa en
 * él; en el corto, un `CHECK`. Entre los preferidos (o todos, si no hay), por este orden: la hora habitual más cerca de
 * ahora, la racha a 1 o 2 periodos de un hito y el objetivo más pequeño; el id desempata.
 *
 * La candidata lleva `ref = "<sesión>:<bloque>:<hábito>"` (la propuesta vive por descanso, y la fila no guarda el
 * destino: el hábito se lee de ahí, [habitoDe]) y el hábito también en `destino`. Sin candidatos,
 * `DESCANSO_SIN_PENDIENTES` con `ref = "<sesión>:<bloque>"` y sin hábito.
 */
object ReglaDescanso {
    fun candidata(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
        d: ContextoDescanso,
    ): Candidata {
        val ref = refDe(d)
        val descartados = hoyNoDeHoy(m, ctx)
        val candidatos =
            h.habitos.filter { it.id != d.excluir && it.id !in descartados && it.puedeProponerseEnDescanso() }
        val elegido =
            preferidos(candidatos, d).sortedWith(orden(ctx)).firstOrNull()
                ?: return Candidata(
                    situacion = Situacion.DESCANSO_SIN_PENDIENTES,
                    ref = ref,
                    huecos = emptyMap(),
                    acciones = emptyList(),
                )
        return Candidata(
            situacion = if (d.largo) Situacion.DESCANSO_HABITO_LARGO else Situacion.DESCANSO_HABITO,
            ref = "$ref:${elegido.id}",
            huecos = mapOf(Hueco.HABITO to ValorHueco.Texto(elegido.nombre), Hueco.MINUTOS to ValorHueco.Numero(d.minutos)),
            acciones = emptyList<Respuesta>(),
            destino = Destino.Habito(elegido.id),
        )
    }

    /** El `ref` de la propuesta de un descanso, sin su hábito. */
    fun refDe(d: ContextoDescanso): String = "${d.sesionId}:${d.bloque}"

    /** Si [ref] es de este descanso, con o sin hábito. */
    fun esDe(
        ref: String?,
        d: ContextoDescanso,
    ): Boolean = ref != null && (ref == refDe(d) || ref.startsWith("${refDe(d)}:"))

    /** El hábito de un `ref` de descanso (`<sesión>:<bloque>:<hábito>`), o null si no lleva. */
    fun habitoDe(ref: String?): String? = ref?.split(':', limit = PARTES)?.getOrNull(PARTES - 1)?.takeIf { it.isNotEmpty() }

    private const val PARTES = 3

    private fun HabitoHoy.puedeProponerseEnDescanso(): Boolean {
        if (!pendienteHoy() || enPausa) return false
        return when (tipo) {
            TipoHabito.CHECK, TipoHabito.DURACION -> true
            TipoHabito.CONTADOR -> objetivo - progreso <= paso
            TipoHabito.LIMITE, TipoHabito.DEJAR -> false
        }
    }

    private fun preferidos(
        candidatos: List<HabitoHoy>,
        d: ContextoDescanso,
    ): List<HabitoHoy> {
        val gustan =
            if (d.largo) {
                candidatos.filter { it.tipo == TipoHabito.DURACION && it.objetivo - it.progreso <= d.minutos }
            } else {
                candidatos.filter { it.tipo == TipoHabito.CHECK }
            }
        return gustan.ifEmpty { candidatos }
    }

    private fun orden(ctx: Contexto): Comparator<HabitoHoy> =
        compareBy<HabitoHoy> { it.horaHabitual?.let { hora -> abs(hora - ctx.ahoraMin) } ?: Int.MAX_VALUE }
            .thenBy { if (it.hitoCerca != null) 0 else 1 }
            .thenBy { it.objetivo }
            .thenBy { it.id }

    /** Los hábitos con un «Hoy no» respondido hoy en cualquier propuesta (`SITUACION:hábito:día`). */
    private fun hoyNoDeHoy(
        m: Memoria,
        ctx: Contexto,
    ): Set<String> =
        m.eventos
            .filter { it.esHoyNoDe(ctx) }
            .mapNotNull { it.ref?.substringAfter(':')?.substringBeforeLast(':') }
            .toSet()

    private fun EventoMemoria.esHoyNoDe(ctx: Contexto) =
        tipo == TipoMemoria.RESPUESTA && dia == ctx.hoy && valor == Respuesta.HOY_NO.ordinal
}
