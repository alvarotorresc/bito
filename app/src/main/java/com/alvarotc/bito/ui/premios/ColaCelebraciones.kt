package com.alvarotc.bito.ui.premios

import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.sistema.Duraciones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TipoVeta { DIA, SEMANA, MES, RACHA }

/** Algo que celebrar en Hoy (pieza 3, §6.1). [orden] es el de G14; [lote] agrupa lo que llegó junto del libro. */
sealed interface Momento {
    val orden: Int
    val lote: Long

    data class Monedas(val origen: String, val bitos: Int, val monedas: Int, override val lote: Long) : Momento {
        override val orden get() = 1
    }

    /** [nombre] y [logroNuevo] solo en RACHA: «N días seguidos · nombre» y el emblema que se enciende la primera vez (§6.3). */
    data class Veta(
        val tipo: TipoVeta,
        val bitos: Int,
        val dia: LogicalDay? = null,
        val racha: Int? = null,
        val habitId: String? = null,
        override val lote: Long,
        val nombre: String? = null,
        val logroNuevo: Boolean = false,
    ) : Momento {
        override val orden get() =
            when (tipo) {
                TipoVeta.DIA -> 2
                TipoVeta.SEMANA -> 3
                TipoVeta.MES -> 4
                TipoVeta.RACHA -> 5
            }
    }

    data class Logro(val badgeId: String, val unlockedAtMillis: Long, override val lote: Long) : Momento {
        override val orden get() = 6
    }

    data class Especial(val especial: GardenSpecial, override val lote: Long) : Momento {
        override val orden get() = 7
    }

    data class Etapa(val parcela: Int, val parcelaNueva: Boolean, override val lote: Long) : Momento {
        override val orden get() = 8
    }
}

private const val TOPE_MONEDAS = 5

/** Las celebraciones en cadena, nunca a la vez (G14), con [respiroMs] entre una y otra. */
class ColaCelebraciones(
    private val scope: CoroutineScope,
    private val respiroMs: Long = Duraciones.RESPIRO.toLong(),
    /** Las monedas que una ráfaga funde en otra: quien la crea las cuenta como terminadas para su lote (regla 3). */
    private val alAbsorber: (List<Momento.Monedas>) -> Unit = {},
) {
    private val cola = ArrayDeque<Momento>()
    private val _actual = MutableStateFlow<Momento?>(null)
    val actual: StateFlow<Momento?> = _actual.asStateFlow()
    val pendientes: List<Momento> get() = cola.toList()
    private var pausada = false
    private var espera: Job? = null

    fun encolar(nuevos: List<Momento>) {
        if (nuevos.isEmpty()) return
        val todos = juntarMonedas((cola + nuevos).sortedBy { it.orden })
        cola.clear()
        cola.addAll(todos)
        if (_actual.value == null && espera == null) siguiente()
    }

    fun terminado() {
        _actual.value = null
        espera?.cancel()
        espera =
            scope.launch {
                delay(respiroMs)
                espera = null
                siguiente()
            }
    }

    /** Al salir de Hoy: lo que sonaba ya se dio por terminado fuera; el resto espera. */
    fun pausar() {
        pausada = true
        espera?.cancel()
        espera = null
        _actual.value = null
    }

    fun reanudar() {
        pausada = false
        if (_actual.value == null && espera == null) siguiente()
    }

    fun descartar(predicado: (Momento) -> Boolean): List<Momento> {
        val quitados = cola.filter(predicado)
        cola.removeAll(predicado)
        return quitados
    }

    private fun siguiente() {
        if (pausada) return
        _actual.value = cola.removeFirstOrNull()
    }

    private fun juntarMonedas(lista: List<Momento>): List<Momento> {
        val monedas = lista.filterIsInstance<Momento.Monedas>()
        if (monedas.sumOf { it.monedas } <= TOPE_MONEDAS) return lista
        val primera = monedas.first()
        val una = Momento.Monedas(primera.origen, monedas.sumOf { it.bitos }, TOPE_MONEDAS, primera.lote)
        alAbsorber(monedas.drop(1)) // cada una cierra su parte de su lote; la fundida cierra la de la primera
        return listOf(una) + lista.filterNot { it is Momento.Monedas }
    }
}
