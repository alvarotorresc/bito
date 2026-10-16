package com.alvarotc.bito.ui.tienda

import androidx.annotation.StringRes

/** La nota de la barra, ya como clave y argumentos: la barra la resuelve con los textos del idioma. */
sealed interface TextoBarra {
    data class Plano(
        @StringRes val res: Int,
        val args: List<Any> = emptyList(),
    ) : TextoBarra
}

/** Por qué una pieza probada no se compra. */
enum class MotivoNoSeCompra { RACHA, LOGRO, TEMPORADA }

/** Qué dice la barra. La pantalla la pinta; el estado la decide (tabla de spec §3.3.4). */
sealed interface BarraPrueba {
    val nombre: String

    data class Comprar(
        override val nombre: String,
        val precio: Int,
        val quedan: Int,
        val nota: TextoBarra?,
        val objeto: Boolean,
    ) : BarraPrueba

    data class SinSaldo(
        override val nombre: String,
        val precio: Int,
        val tienes: Int,
        val objeto: Boolean,
    ) : BarraPrueba

    data class NoSeCompra(
        override val nombre: String,
        val motivo: MotivoNoSeCompra,
        val texto: TextoBarra,
        val objeto: Boolean,
    ) : BarraPrueba
}
