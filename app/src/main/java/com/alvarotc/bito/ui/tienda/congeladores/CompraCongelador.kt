package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lo que la pestaña enseña mientras una compra (o su vuelta atrás) está en el aire. Room cambia el saldo y los
 * congeladores casi al instante, pero el copito, la cifra y la línea no cambian hasta que la moneda llega (J14):
 * por eso la pestaña congela la cuenta y el saldo que ve en [cuentaFija] y [saldoFijo] y los suelta al llegar.
 * El saldo de la cabecera no se congela: rueda en cuanto sale la moneda.
 */
@Stable
internal class CompraCongelador {
    /** Con valor, la cuenta de congeladores que se enseña en lugar de la real. */
    var cuentaFija by mutableStateOf<Int?>(null)
        private set

    /** Con valor, el saldo que ven el botón y su línea en lugar del real. */
    var saldoFijo by mutableStateOf<Int?>(null)
        private set

    /** Hay una compra en el aire: el botón no atiende otro toque. */
    var comprando by mutableStateOf(false)
        private set

    /** El último copito (o la pastilla) acaba de aterrizar. */
    var entrando by mutableStateOf(false)
        private set

    /** El último copito (o la pastilla) se está yendo. */
    var saliendo by mutableStateOf(false)
        private set

    /** Cambia con cada compra o deshacer que empieza, para armar el tope de espera. */
    var generacion by mutableIntStateOf(0)
        private set

    private var esperaCompra = false
    private var esperaDeshacer = false
    private var llegado = false

    val ocupado: Boolean get() = comprando || esperaDeshacer

    fun empezarCompra(
        cuenta: Int,
        saldo: Int,
    ) {
        cuentaFija = cuenta
        saldoFijo = saldo
        comprando = true
        esperaCompra = true
        llegado = false
        generacion++
    }

    /** Acepta la llegada del evento solo si esta pestaña la espera: un evento viejo de otra visita no vuela. */
    fun aceptarCompra(): Boolean = esperaCompra.also { esperaCompra = false }

    /** La moneda llegó: el copito aparece, la cifra rueda, la línea hace relevo y el botón se aclara o se apaga. */
    fun llegar(
        cuantos: Int,
        precio: Int,
    ) {
        cuentaFija = cuantos
        saldoFijo = (saldoFijo ?: 0) - precio
        comprando = false
        entrando = true
        llegado = true
    }

    fun empezarDeshacer(cuenta: Int) {
        cuentaFija = cuenta
        esperaDeshacer = true
        llegado = false
        generacion++
    }

    fun aceptarDeshacer(): Boolean = esperaDeshacer.also { esperaDeshacer = false }

    fun irse() {
        saliendo = true
    }

    /** El último copito ya no está: se enseña la cuenta nueva (la real en cuanto alcance a la sabida). */
    fun yaSeFue(cuantos: Int) {
        cuentaFija = cuantos
        saliendo = false
        llegado = true
    }

    fun posado() {
        entrando = false
    }

    /** La cuenta real ya es la que se enseña: se suelta lo congelado. */
    fun soltarSiAlcanzada(real: Int) {
        if (llegado && cuentaFija == real) {
            cuentaFija = null
            saldoFijo = null
            llegado = false
        }
    }

    /** El evento no llegó (compra rechazada, deshacer rechazado): se suelta todo y se enseña lo real. */
    fun soltarSiSigueEsperando() {
        if (!esperaCompra && !esperaDeshacer) return
        esperaCompra = false
        esperaDeshacer = false
        cuentaFija = null
        saldoFijo = null
        comprando = false
        llegado = false
    }
}
