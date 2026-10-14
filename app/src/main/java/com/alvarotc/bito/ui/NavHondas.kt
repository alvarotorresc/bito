package com.alvarotc.bito.ui

/** Rutas que bajan un estrato (G21/G22). Las demás conservan su transición hasta su pieza (decisión 13). */
internal val RUTAS_HONDAS = setOf("habit?id={id}", "task?id={id}", "tasks", "archived")

internal fun esHonda(ruta: String?): Boolean = ruta in RUTAS_HONDAS

enum class Momento { ENTRA, SALE, VUELVE_ENTRA, VUELVE_SALE }

enum class TipoTransicion { GLOBAL, FUNDIDO, BAJA_ENTRA, BAJA_SALE, SUBE_ENTRA, SUBE_SALE }

/** [desde] y [hacia] son las rutas del estado inicial y final de la transición del `NavHost`. */
internal fun transicionDe(
    momento: Momento,
    desde: String?,
    hacia: String?,
    reducido: Boolean,
): TipoTransicion {
    val honda =
        when (momento) {
            Momento.ENTRA, Momento.SALE -> esHonda(hacia)
            Momento.VUELVE_ENTRA, Momento.VUELVE_SALE -> esHonda(desde)
        }
    if (!honda) return TipoTransicion.GLOBAL
    if (reducido) return TipoTransicion.FUNDIDO
    return when (momento) {
        Momento.ENTRA -> TipoTransicion.BAJA_ENTRA
        Momento.SALE -> TipoTransicion.BAJA_SALE
        Momento.VUELVE_ENTRA -> TipoTransicion.SUBE_ENTRA
        Momento.VUELVE_SALE -> TipoTransicion.SUBE_SALE
    }
}

/** true = suena `descenso` (bajar), false = `ascenso` (subir), null = nada. */
internal fun sonidoDeCambio(
    anterior: String?,
    nueva: String?,
    haciaDelante: Boolean,
): Boolean? =
    when {
        anterior == nueva -> null
        haciaDelante && esHonda(nueva) -> true
        !haciaDelante && esHonda(anterior) -> false
        else -> null
    }
