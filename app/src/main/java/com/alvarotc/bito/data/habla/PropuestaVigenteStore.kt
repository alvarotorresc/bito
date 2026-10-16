package com.alvarotc.bito.data.habla

import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.domain.habla.PropuestaVigente
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
        return fila.aDominio(db)
    }

    suspend fun guardar(v: PropuestaVigente) = dao.guardarVigente(v.aEntidad())

    suspend fun borrar(
        ambito: Ambito,
        habitId: String = "",
    ) = dao.borrarVigente(ambito.name, habitId)

    suspend fun vaciar() = dao.vaciarVigentes()
}
