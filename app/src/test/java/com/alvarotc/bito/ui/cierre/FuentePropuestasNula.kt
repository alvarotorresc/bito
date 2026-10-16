package com.alvarotc.bito.ui.cierre

import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.ContextoDescanso
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Una fuente que no propone nada: el cierre solo la escucha y los tests no necesitan la cadena real. */
internal object FuentePropuestasNula : FuentePropuestas {
    override fun propuesta(
        ambito: Ambito,
        habitId: String?,
        descanso: ContextoDescanso?,
    ): Flow<Propuesta?> = flowOf(null)

    override suspend fun responder(
        id: String,
        respuesta: Respuesta,
        hora: Int?,
    ): Efecto = Efecto.Ninguno

    override suspend fun guardarGordo(refs: List<RefGordo>) = Unit
}
