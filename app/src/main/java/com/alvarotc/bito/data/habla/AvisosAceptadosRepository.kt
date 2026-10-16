package com.alvarotc.bito.data.habla

import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.model.LogicalDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Los «Vale, a las…» aceptados (spec 6.1). */
class AvisosAceptadosRepository(
    db: BitoDatabase,
) {
    private val dao = db.hablaDao()

    suspend fun crear(a: AvisoAceptado) = dao.insertarAviso(a.aEntidad())

    suspend fun cambiarEstado(
        id: String,
        estado: EstadoAviso,
    ) {
        val fila = dao.aviso(id) ?: return
        dao.actualizarAviso(fila.copy(estado = estado.name))
    }

    /** Suma uno a `pospuesto` y cambia el minuto. */
    suspend fun posponer(
        id: String,
        minuto: Int,
    ) {
        val fila = dao.aviso(id) ?: return
        dao.actualizarAviso(fila.copy(minuto = minuto, pospuesto = fila.pospuesto + 1))
    }

    /** `PENDIENTE` de cualquier dia. */
    suspend fun pendientes(): List<AvisoAceptado> = dao.avisosPendientes().mapNotNull { it.aDominio() }

    fun observeDe(dia: LogicalDay): Flow<List<AvisoAceptado>> = dao.avisosDe(dia).map { filas -> filas.mapNotNull { it.aDominio() } }

    suspend fun vaciar() = dao.vaciarAvisos()
}
