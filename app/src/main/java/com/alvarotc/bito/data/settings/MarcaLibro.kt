package com.alvarotc.bito.data.settings

import com.alvarotc.bito.domain.model.PointsLedgerEntry

/**
 * La última entrada del libro ya celebrada en Hoy (pieza 3, §6.1). Los id son UUID, así que el
 * orden lo da la hora y, a igual hora, el id. Vive en [Settings] con valor por defecto null, así que
 * restaurar una copia la borra (y con ella no se celebra nada viejo). No entra en la copia.
 */
data class MarcaLibro(val createdAtMillis: Long, val id: String) : Comparable<MarcaLibro> {
    override fun compareTo(other: MarcaLibro): Int = compareValuesBy(this, other, MarcaLibro::createdAtMillis, MarcaLibro::id)

    fun codificar(): String = "$createdAtMillis|$id"

    companion object {
        fun decodificar(s: String?): MarcaLibro? {
            if (s == null) return null
            val corte = s.indexOf('|')
            if (corte <= 0) return null
            val millis = s.substring(0, corte).toLongOrNull() ?: return null
            return MarcaLibro(millis, s.substring(corte + 1))
        }
    }
}

fun PointsLedgerEntry.marca(): MarcaLibro = MarcaLibro(createdAtMillis, id)
