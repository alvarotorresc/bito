package com.alvarotc.bito.ui.inicio

/** Las salidas del Inicio hacia el resto de la app: las cablea la navegación y las consumen Hoy y Camino. */
data class NavegacionInicio(
    val abrirFicha: (String) -> Unit,
    val abrirAjustes: () -> Unit,
    val abrirTienda: () -> Unit,
    val abrirRepaso: () -> Unit,
    val abrirTareas: () -> Unit,
    val crearHabito: () -> Unit,
    val crearTarea: () -> Unit,
)
