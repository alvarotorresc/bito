package com.alvarotc.bito.domain.propuestas

import androidx.annotation.StringRes
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import kotlinx.coroutines.flow.Flow

/** Lo que el usuario puede contestar a una propuesta de Habi. */
enum class Respuesta { VALE, AHORA, HECHO, CERRAR_DIA, ELEGIR_GORDO, OTRA_HORA, LUEGO, HOY_NO }

/** Un botón de la tarjeta o de la nota, en el orden en que se pinta. Nunca se guarda en Room. */
data class Accion(
    val respuesta: Respuesta,
    val etiqueta: TextoHabi,
    val principal: Boolean = false,
)

/** Adónde lleva «Ahora». Solo cambia [com.alvarotc.bito.ui.inicio.EntradaSesion]. */
sealed interface Destino {
    data class Habito(
        val id: String,
    ) : Destino

    data class Tarea(
        val id: String,
    ) : Destino

    data object Respirar : Destino

    data object Cierre : Destino

    data object LoGordo : Destino
}

/** Un texto de Habi: un recurso con argumentos o una frase compuesta de trozos. Se resuelve en la UI con el idioma activo. */
sealed interface TextoHabi {
    /** Un argumento puede ser otro [TextoHabi]. */
    data class Recurso(
        @StringRes val res: Int,
        val args: List<Any> = emptyList(),
    ) : TextoHabi

    data class Frase(
        val trozos: List<ClaveTrozo>,
        val huecos: Map<Hueco, ValorHueco>,
    ) : TextoHabi
}

fun TextoHabi(
    @StringRes res: Int,
    args: List<Any> = emptyList(),
): TextoHabi = TextoHabi.Recurso(res, args)

/**
 * Lo que Habi propone. [id] es estable. [acciones] vacía = Habi solo habla. [destino] es para
 * «Ahora»; [hora] es el minuto del día del «Vale, a las…».
 */
data class Propuesta(
    val id: String,
    val texto: TextoHabi,
    val acciones: List<Accion>,
    val destino: Destino? = null,
    val hora: Int? = null,
)

enum class Ambito { HOY, FICHA, CAMINO, CIERRE, DESCANSO }

/** Lo que la UI debe hacer tras una respuesta. */
sealed interface Efecto {
    data object Ninguno : Efecto

    data class Sesion(
        val destino: Destino,
    ) : Efecto

    data object AbrirCierre : Efecto

    data object AbrirLoGordo : Efecto

    data class ElegirHora(
        val desde: Int,
        val hasta: Int,
        val actual: Int,
    ) : Efecto

    data class EditarHabito(
        val habitId: String,
        val objetivoSugerido: Int,
    ) : Efecto

    data class PedirPermiso(
        val alarmas: Boolean,
    ) : Efecto
}

interface FuentePropuestas {
    fun propuesta(
        ambito: Ambito,
        habitId: String? = null,
    ): Flow<Propuesta?>

    suspend fun responder(
        id: String,
        respuesta: Respuesta,
        hora: Int? = null,
    ): Efecto

    suspend fun guardarGordo(refs: List<RefGordo>)
}
