package com.alvarotc.bito.ui.tienda

import androidx.annotation.StringRes
import com.alvarotc.bito.domain.Movimiento
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.EquippedSet
import java.time.LocalDate

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

enum class PestanaTienda { HABI, JARDIN, CONGELADORES }

enum class LuzTienda(val parada: Float) { DIA(0f), TARDE(0.5f), NOCHE(1f) }

/** Lo que se prueba ahora mismo. Efímero: nunca toca Room. */
sealed interface PruebaTienda {
    /** Habi: cabeza, cara, cuello, pies, color, ojos y disfraz. */
    data class Pieza(val itemId: String) : PruebaTienda

    /** Jardín, comprable. */
    data class Objeto(val itemId: String) : PruebaTienda

    /** Jardín, no se compra: llega con un logro. */
    data class Especial(val logroId: String) : PruebaTienda
}

/**
 * Todo lo que una pestaña necesita ver de la tienda. [cargado] es falso solo en el valor de relleno que
 * hay antes del primer estado real: nada (sonido, escritura, propuesta) se decide con ese relleno.
 */
data class TiendaUiState(
    val saldo: Int,
    val pestana: PestanaTienda,
    val luz: LuzTienda,
    val prueba: PruebaTienda?,
    val barra: BarraPrueba?,
    /** Lo que lleva Habi, sin la prueba. */
    val puestos: EquippedSet,
    /** Lo que se ve en Habi: [puestos] con la prueba encima. */
    val look: EquippedSet,
    val poseidos: Set<String>,
    /** Claves de `GARDEN_OBJECT` poseídos. */
    val objetosJardin: Set<String>,
    val congeladores: Int,
    val mejorRacha: Int,
    val hoy: LocalDate,
    val cargado: Boolean = true,
    /** Los últimos movimientos y la economía, para «Tus bitos» dentro de la tienda. */
    val movimientos: List<Movimiento> = emptyList(),
    val economia: EconomyConfig = EconomyConfig(),
)

sealed interface EventoTienda {
    data class Comprado(val itemId: String) : EventoTienda

    data class CongeladorComprado(val refId: String, val cuantos: Int) : EventoTienda

    data object CongeladorDeshecho : EventoTienda
}

interface TiendaAcciones {
    fun cambiarPestana(p: PestanaTienda)

    fun cambiarLuz(l: LuzTienda)

    /** Probar, poner o quitar según el estado; en una ranura tapada quita el disfraz y pone la pieza. */
    fun tocarPieza(itemId: String)

    /** Jardín: probar un especial por logro. */
    fun tocarEspecial(logroId: String)

    fun quitarPrueba()

    fun comprar(itemId: String)

    fun comprarCongelador()

    /** Solo la última compra, dentro de los 4 s del aviso. */
    fun deshacerCongelador()
}
