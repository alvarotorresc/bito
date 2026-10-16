package com.alvarotc.bito.data.sesion

import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.SesionEntity
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.ResultadoSesion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Una sesion de foco terminada, tal como se guarda. */
data class SesionCerrada(
    val id: String,
    val objetivo: Objetivo,
    val dia: LogicalDay,
    val inicioMillis: Long,
    val finMillis: Long,
    val minutosFoco: Int,
    val bloquesHechos: Int,
    val bloquesPlan: Int,
    val resultado: ResultadoSesion,
)

class SesionesRepository(db: BitoDatabase) {
    private val dao = db.sesionDao()

    /** No guarda las que no llegaron a un minuto de foco. */
    suspend fun guardar(s: SesionCerrada) {
        if (s.minutosFoco < 1) return
        dao.insert(s.toEntity())
    }

    fun deDia(dia: LogicalDay): Flow<List<SesionCerrada>> = dao.observeDeDia(dia).map { filas -> filas.map { it.toCerrada() } }

    suspend fun minutosDe(
        objetivoId: String,
        dia: LogicalDay,
    ): Int = dao.minutosDe(objetivoId, dia)

    /** Lo llaman el borrado de una tarea y el de un habito. */
    suspend fun borrarDe(objetivoId: String) = dao.borrarDe(objetivoId)
}

private const val TIPO_TAREA = "TAREA"
private const val TIPO_HABITO = "HABITO"
private const val TIPO_LIBRE = "LIBRE"

private fun SesionCerrada.toEntity(): SesionEntity {
    val (tipo, objetivoId) =
        when (objetivo) {
            is Objetivo.Tarea -> TIPO_TAREA to objetivo.id
            is Objetivo.Habito -> TIPO_HABITO to objetivo.id
            Objetivo.Libre -> TIPO_LIBRE to null
        }
    return SesionEntity(id, tipo, objetivoId, dia, inicioMillis, finMillis, minutosFoco, bloquesHechos, bloquesPlan, resultado.name)
}

private fun SesionEntity.toCerrada() =
    SesionCerrada(
        id = id,
        objetivo =
            when (objetivoTipo) {
                TIPO_TAREA -> Objetivo.Tarea(objetivoId.orEmpty())
                TIPO_HABITO -> Objetivo.Habito(objetivoId.orEmpty())
                else -> Objetivo.Libre
            },
        dia = dia,
        inicioMillis = inicioMillis,
        finMillis = finMillis,
        minutosFoco = minutosFoco,
        bloquesHechos = bloquesHechos,
        bloquesPlan = bloquesPlan,
        resultado = runCatching { ResultadoSesion.valueOf(resultado) }.getOrDefault(ResultadoSesion.SIN_RESPUESTA),
    )
