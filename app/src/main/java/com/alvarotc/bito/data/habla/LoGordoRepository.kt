package com.alvarotc.bito.data.habla

import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.model.LogicalDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val MAXIMO_GORDO = 3

/** Lo gordo de cada dia: hasta tres elementos. */
class LoGordoRepository(
    db: BitoDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.hablaDao()

    /** Reemplaza el dia entero. */
    suspend fun guardar(
        dia: LogicalDay,
        refs: List<RefGordo>,
    ) {
        val ahora = now()
        dao.reemplazarGordo(dia, refs.take(MAXIMO_GORDO).mapIndexed { i, ref -> ref.aEntidad(dia, i, ahora) })
    }

    fun observe(dias: List<LogicalDay>): Flow<Map<LogicalDay, List<RefGordo>>> =
        dao.gordoDe(dias).map { filas ->
            filas
                .groupBy { it.dia }
                .mapValues { (_, delDia) -> delDia.sortedBy { it.posicion }.mapNotNull { it.aDominio() } }
        }
}
