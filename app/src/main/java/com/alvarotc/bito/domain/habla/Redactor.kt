package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.TextoHabi
import kotlin.random.Random

/** Una frase redactada, los trozos que gastó y el recuerdo que mencionó (id del evento), si alguno. */
data class Redaccion(
    val frase: TextoHabi.Frase,
    val usos: List<UsoTrozo>,
    val mencion: String?,
)

/**
 * Convierte una [Candidata] en una frase de trozos (spec §4.4, §5.12, §5.13). Provisional: solo el
 * núcleo, índice 0, sin usos ni mención.
 */
object Redactor {
    /**
     * [forzarApertura]: los títulos de aviso siempre abren. [recuerdo]: el que la fuente eligió con
     * `Recuerdos.elegible`; los avisos pasan null.
     */
    fun redactar(
        c: Candidata,
        ctx: Contexto,
        catalogo: CatalogoHabla,
        m: Memoria,
        azar: Random,
        forzarApertura: Boolean = false,
        recuerdo: Recuerdo? = null,
    ): Redaccion {
        val nucleo = c.nucleo
        val trozos = if (nucleo == null) emptyList() else listOf(ClaveTrozo(nucleo, ctx.voz, 0))
        return Redaccion(TextoHabi.Frase(trozos, c.huecos), emptyList(), null)
    }

    /** La semilla del azar de una redacción: la misma propuesta del mismo día dice lo mismo. */
    fun semilla(
        dia: LogicalDay,
        situacion: Situacion,
        ref: String?,
        voz: Personality,
        respuestasHoy: Int,
    ): Long {
        var s = dia.toLong()
        s = s * PRIMO + situacion.name.hashCode()
        s = s * PRIMO + ref.orEmpty().hashCode()
        s = s * PRIMO + voz.name.hashCode()
        s = s * PRIMO + respuestasHoy
        return s
    }

    private const val PRIMO = 31L
}
