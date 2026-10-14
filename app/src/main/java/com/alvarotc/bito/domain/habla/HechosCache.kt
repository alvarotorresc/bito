package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.DomainState

/**
 * Calcula los [Hechos] recordando por día lógico lo caro (spec §11.1). Provisional: llama a
 * [Hechos.de] sin recordar nada.
 */
class HechosCache {
    fun hechos(
        state: DomainState,
        memoria: Memoria,
        ctx: Contexto,
        recordatorios: Map<String, Int>,
    ): Hechos = Hechos.de(state, memoria, ctx, recordatorios)
}
