package com.alvarotc.bito.data.habla

import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.domain.habla.PropuestaVigente
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.propuestas.Ambito

/** La propuesta que se esta enseñando por ambito (y por habito en la ficha). */
class PropuestaVigenteStore(
    private val db: BitoDatabase,
) {
    private val dao = db.hablaDao()

    suspend fun leer(
        ambito: Ambito,
        habitId: String = "",
    ): PropuestaVigente? {
        val fila = dao.vigente(ambito.name, habitId) ?: return null
        val situacion = Situacion.entries.firstOrNull { it.name == fila.situacion }
        val esTarea =
            fila.ref != null && situacion != null && refAmbiguo(situacion) && db.taskDao().byId(fila.ref) != null
        return fila.aDominio(refEsTarea = esTarea)
    }

    suspend fun guardar(v: PropuestaVigente) = dao.guardarVigente(v.aEntidad())

    suspend fun borrar(
        ambito: Ambito,
        habitId: String = "",
    ) = dao.borrarVigente(ambito.name, habitId)

    suspend fun vaciar() = dao.vaciarVigentes()
}
