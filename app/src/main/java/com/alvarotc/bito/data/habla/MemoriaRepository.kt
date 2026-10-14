package com.alvarotc.bito.data.habla

import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Mostrada
import com.alvarotc.bito.domain.habla.UsoTrozo
import com.alvarotc.bito.domain.model.LogicalDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

private const val DIAS_DE_MEMORIA = 400
private const val DIAS_DE_USOS = 60
private const val MILLIS_POR_DIA = 86_400_000L

/** Lo que Habi recuerda: eventos, lo gordo, avisos, propuestas vigentes y usos de trozos (spec 4.6). */
class MemoriaRepository(
    private val db: BitoDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.hablaDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(hoy: Flow<LogicalDay>): Flow<Memoria> =
        hoy.distinctUntilChanged().flatMapLatest { dia ->
            combine(
                dao.eventosDesde(dia - DIAS_DE_MEMORIA),
                dao.gordoDe(listOf(dia, dia - 1)),
                dao.avisosDe(dia),
                dao.vigentes().map { filas -> filas.mapNotNull { it.aDominio(db) } },
                dao.usosDesde(now() - DIAS_DE_USOS * MILLIS_POR_DIA),
            ) { eventos, gordo, avisos, vigentes, usos ->
                val usosDominio = usos.mapNotNull { it.aDominio() }
                Memoria(
                    eventos = eventos.mapNotNull { it.aDominio() },
                    gordo =
                        gordo
                            .groupBy { it.dia }
                            .mapValues { (_, filas) -> filas.sortedBy { it.posicion }.mapNotNull { it.aDominio() } },
                    gordoElegidoMillis = gordo.filter { it.dia == dia }.maxOfOrNull { it.creadoMillis },
                    avisos = avisos.mapNotNull { it.aDominio() },
                    vigentes = vigentes,
                    mostradasHoy = mostradas(usosDominio, dia),
                    usos = usosDominio,
                )
            }
        }

    /** Una por `combinacion`: un trozo por bolsa de la misma frase cuenta como una propuesta mostrada. */
    private fun mostradas(
        usos: List<UsoTrozo>,
        hoy: LogicalDay,
    ): List<Mostrada> =
        usos
            .filter { it.dia == hoy && it.situacion != null }
            .groupBy { it.combinacion }
            .map { (_, grupo) ->
                val primero = grupo.minBy { it.usadoMillis }
                Mostrada(primero.situacion!!, null, primero.usadoMillis)
            }

    suspend fun anotar(evento: EventoMemoria) = dao.insertarEvento(evento.aEntidad())

    suspend fun anotarUsos(usos: List<UsoTrozo>) = dao.insertarUsos(usos.map { it.aEntidad() })

    /** 400 dias de memoria y 60 de usos. */
    suspend fun purgar(
        hoy: LogicalDay,
        nowMillis: Long,
    ) {
        dao.borrarEventosAntesDe(hoy - DIAS_DE_MEMORIA)
        dao.borrarUsosAntesDe(nowMillis - DIAS_DE_USOS * MILLIS_POR_DIA)
    }
}
