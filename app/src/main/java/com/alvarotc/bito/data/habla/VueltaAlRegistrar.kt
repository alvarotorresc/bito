package com.alvarotc.bito.data.habla

import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.model.DomainState
import java.time.ZoneId

/**
 * La vuelta en la memoria (spec §5.11): el primer registro tras 3 o más días fuera la anota, venga de la lista, la
 * ficha, la tarjeta, el widget o un aviso. Lo llama el diario, que es por donde pasa todo registro. Solo cuenta un
 * registro hecho en su propio día (no rellenar un día pasado), y el id `VUELTA:<día>` lo deja en una sola fila.
 */
class VueltaAlRegistrar(
    private val estado: suspend () -> DomainState,
    private val memoria: MemoriaRepository,
    private val corteDia: suspend () -> Int,
    private val zona: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend fun registrando(
        entrada: EntryEntity,
        escribir: suspend () -> Unit,
    ) {
        val corte = corteDia()
        val z = zona()
        val dia = entrada.logicalDay
        val dias =
            Hechos.diasFueraAlVolver(
                estado(),
                dia,
                corte,
                z,
            ).takeIf { LogicalDays.logicalDayOf(entrada.createdAtMillis, corte, z) == dia }
        escribir()
        if (dias != null) {
            memoria.anotar(EventoMemoria("VUELTA:$dia", TipoMemoria.VUELTA, dia, entrada.habitId, dias, entrada.createdAtMillis))
        }
    }
}
