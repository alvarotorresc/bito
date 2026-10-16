package com.alvarotc.bito.data.habla

import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.model.DomainState
import kotlinx.coroutines.CancellationException
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
        // La vuelta es opcional: si su cálculo o su anotación fallan, el registro del usuario se escribe igual.
        // Se mira antes de escribir porque después el registro ya contaría como día con actividad.
        val dias = tolerante { diasDeVuelta(entrada) }
        escribir()
        if (dias != null) {
            val dia = entrada.logicalDay
            tolerante {
                memoria.anotar(EventoMemoria("VUELTA:$dia", TipoMemoria.VUELTA, dia, entrada.habitId, dias, entrada.createdAtMillis))
            }
        }
    }

    private suspend fun diasDeVuelta(entrada: EntryEntity): Int? {
        val corte = corteDia()
        val z = zona()
        val dia = entrada.logicalDay
        return Hechos
            .diasFueraAlVolver(estado(), dia, corte, z)
            .takeIf { LogicalDays.logicalDayOf(entrada.createdAtMillis, corte, z) == dia }
    }

    /** Como `runCatching`, pero la cancelación de la corrutina sigue su camino. */
    private suspend fun <T> tolerante(bloque: suspend () -> T): T? =
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
