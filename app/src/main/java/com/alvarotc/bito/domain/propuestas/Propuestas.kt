package com.alvarotc.bito.domain.propuestas

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.Flow

/** Las tres respuestas de la tarjeta de Habi (pieza 3, §5.4). */
enum class Respuesta { VALE, AHORA, HOY_NO }

/** Adónde lleva «Ahora». La pieza 5 solo cambia [com.alvarotc.bito.ui.inicio.EntradaSesion]. */
sealed interface Destino {
    data class Habito(val id: String) : Destino

    data class Tarea(val id: String) : Destino

    data object Respirar : Destino
}

/** Un texto de Habi: recurso y argumentos, que se resuelven en la UI con el idioma activo. Un argumento puede ser otro [TextoHabi]. */
data class TextoHabi(
    @StringRes val res: Int,
    val args: List<Any> = emptyList(),
)

/**
 * Lo que Habi propone. [id] es estable (la pieza 4 lo usa para no repetirse). [acciones] vacío =
 * Habi solo saluda. [etiquetaVale] null = «Vale». [destino] es para «Ahora».
 */
data class Propuesta(
    val id: String,
    val texto: TextoHabi,
    val acciones: Set<Respuesta>,
    val etiquetaVale: TextoHabi? = null,
    val destino: Destino? = null,
)

enum class Ambito { HOY, FICHA }

/** El contrato que la pieza 4 implementa. La pieza 3 deja [com.alvarotc.bito.data.propuestas.PropuestasProvisionales]. */
interface FuentePropuestas {
    fun propuesta(
        ambito: Ambito,
        habitId: String? = null,
    ): Flow<Propuesta?>

    suspend fun responder(
        id: String,
        respuesta: Respuesta,
    )
}
