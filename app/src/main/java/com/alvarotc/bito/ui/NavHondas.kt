package com.alvarotc.bito.ui

/** Rutas que bajan un estrato (G21/G22). Las demás conservan su transición hasta su pieza (decisión 13). */
internal val RUTAS_HONDAS = setOf("habit?id={id}", "task?id={id}", "tasks", "archived")

/** La ficha de un hábito; `lectura` es opcional, así que `detail/<id>` sigue valiendo. */
internal const val RUTA_FICHA = "detail/{habitId}?lectura={lectura}"

internal const val RUTA_AJUSTES = "settings"

/** Las rutas cuyo cambio suena como un descenso o un ascenso. */
internal val RUTAS_CON_SONIDO = RUTAS_HONDAS + RUTA_FICHA

internal fun esHonda(ruta: String?): Boolean = ruta in RUTAS_HONDAS

enum class Momento { ENTRA, SALE, VUELVE_ENTRA, VUELVE_SALE }

enum class TipoTransicion {
    GLOBAL,
    FUNDIDO,
    BAJA_ENTRA,
    BAJA_SALE,
    SUBE_ENTRA,
    SUBE_SALE,
    FICHA_FUNDIDO,
    CAMARA_ENTRA,
    CAMARA_SALE,
    CAMARA_VUELVE_ENTRA,
    CAMARA_VUELVE_SALE,
}

internal fun transicionAjustes(
    momento: Momento,
    reducido: Boolean,
): TipoTransicion =
    if (reducido) {
        TipoTransicion.FUNDIDO
    } else {
        when (momento) {
            Momento.ENTRA -> TipoTransicion.CAMARA_ENTRA
            Momento.SALE -> TipoTransicion.CAMARA_SALE
            Momento.VUELVE_ENTRA -> TipoTransicion.CAMARA_VUELVE_ENTRA
            Momento.VUELVE_SALE -> TipoTransicion.CAMARA_VUELVE_SALE
        }
    }

/** [desde] y [hacia] son las rutas del estado inicial y final de la transición del `NavHost`. */
internal fun transicionDe(
    momento: Momento,
    desde: String?,
    hacia: String?,
    reducido: Boolean,
): TipoTransicion {
    val haciaDelante = momento == Momento.ENTRA || momento == Momento.SALE
    val nueva = if (haciaDelante) hacia else desde // la ruta que se abre o se cierra
    val otra = if (haciaDelante) desde else hacia // de dónde se viene o adónde se vuelve
    if (nueva == RUTA_AJUSTES) return transicionAjustes(momento, reducido)
    if (nueva == RUTA_FICHA && otra != "archived") {
        return if (reducido) TipoTransicion.FUNDIDO else TipoTransicion.FICHA_FUNDIDO
    }
    val honda = nueva in RUTAS_HONDAS || nueva == RUTA_FICHA
    if (!honda) return TipoTransicion.GLOBAL
    if (reducido) return TipoTransicion.FUNDIDO
    return when (momento) {
        Momento.ENTRA -> TipoTransicion.BAJA_ENTRA
        Momento.SALE -> TipoTransicion.BAJA_SALE
        Momento.VUELVE_ENTRA -> TipoTransicion.SUBE_ENTRA
        Momento.VUELVE_SALE -> TipoTransicion.SUBE_SALE
    }
}

enum class CambioSonoro { DESCENSO, ASCENSO, ALEJAR, ACERCAR }

internal fun sonidoDeCambio(
    anterior: String?,
    nueva: String?,
    haciaDelante: Boolean,
): CambioSonoro? =
    when {
        anterior == nueva -> null
        haciaDelante && nueva == RUTA_AJUSTES -> CambioSonoro.ALEJAR
        !haciaDelante && anterior == RUTA_AJUSTES -> CambioSonoro.ACERCAR
        haciaDelante && nueva in RUTAS_CON_SONIDO -> CambioSonoro.DESCENSO
        !haciaDelante && anterior in RUTAS_CON_SONIDO -> CambioSonoro.ASCENSO
        else -> null
    }
