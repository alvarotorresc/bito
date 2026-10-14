@file:Suppress("ktlint:standard:filename") // la Tarea 10 añade más declaraciones a este archivo

package com.alvarotc.bito.ui.premios

/** Claves de posición en el Inicio (pieza 3, §6.2): de dónde salen y adónde llegan gotas, monedas y granos. */
object ClavesAncla {
    const val SALDO = "saldo"
    const val PARCELA = "parcela"

    /** El cuerpo de Habi en la escena: lo registra `GardenScene` (Tarea 14) para el globito de «volver de crear». */
    const val HABI = "habi"

    /** El círculo de un hábito: de ahí salen la gota y su moneda. */
    fun fila(habitId: String): String = "fila:$habitId"

    /** El círculo de una tarea: de ahí caen los granos (G6) y salen sus monedas. */
    fun tarea(taskId: String): String = "tarea:$taskId"
}
