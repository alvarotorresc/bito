package com.alvarotc.bito.ui.premios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.MarcaLibro
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.data.settings.marca
import com.alvarotc.bito.domain.BadgeEngine
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.PointsLedgerEntry
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal object DerivarMomentos {
    data class Resultado(val momentos: List<Momento>, val marcaLote: MarcaLibro?, val vistas: Set<String>)

    /**
     * Reglas 4, 5 y 6. [vistas] null = primera emisión (arranque) o restauración dentro de la sesión.
     * [habitos] (id → nombre) y [badges] (los de la misma emisión) solo sirven para `nombre` y `logroNuevo` de la racha.
     */
    fun delLibro(
        libro: List<PointsLedgerEntry>,
        marca: MarcaLibro?,
        vistas: Set<String>?,
        prefs: Settings,
        enHoy: Boolean,
        lote: Long,
        habitos: Map<String, String> = emptyMap(),
        badges: List<BadgeEntity> = emptyList(),
    ): Resultado {
        val ids = libro.mapTo(mutableSetOf()) { it.id }
        val nuevas =
            when {
                vistas == null && marca == null -> return Resultado(emptyList(), libro.maxOfOrNull { it.marca() }, ids)
                vistas == null -> libro.filter { it.marca() > marca!! }
                else -> libro.filter { it.id !in vistas }
            }.sortedWith(compareBy({ it.createdAtMillis }, { it.id }))
        val monedasPermitidas = enHoy && vistas != null
        val momentos =
            nuevas.mapNotNull { e ->
                val ref = e.refId.orEmpty()
                when (e.reason) {
                    PointsReason.HABIT_DONE -> {
                        val origen = ClavesAncla.fila(ref.substringBeforeLast(':'))
                        Momento.Monedas(origen, e.delta, 1, lote).takeIf { monedasPermitidas }
                    }
                    PointsReason.TASK_DONE -> {
                        val origen = ClavesAncla.tarea(ref.removePrefix("task:"))
                        Momento.Monedas(origen, e.delta, 3, lote).takeIf { monedasPermitidas }
                    }
                    PointsReason.PERFECT_DAY ->
                        when {
                            ref.startsWith("week:") -> Momento.Veta(TipoVeta.SEMANA, e.delta, lote = lote)
                            ref.startsWith("month:") -> Momento.Veta(TipoVeta.MES, e.delta, lote = lote)
                            else -> {
                                val dia = ref.removePrefix("day:").toIntOrNull() ?: e.logicalDay
                                if (prefs.perfectDayCelebratedDay >= dia) {
                                    null
                                } else {
                                    Momento.Veta(
                                        TipoVeta.DIA,
                                        e.delta,
                                        dia = dia,
                                        lote = lote,
                                    )
                                }
                            }
                        }
                    PointsReason.STREAK_MILESTONE -> {
                        val n = ref.substringAfterLast(':').toIntOrNull()
                        val habitId = ref.substringBeforeLast(':')
                        val logroId = n?.let { BadgeEngine.streakBadges[it] }
                        val yaVisto = badges.any { it.badgeId == logroId && it.unlockedAtMillis <= prefs.badgesSeenUntilMillis }
                        Momento.Veta(
                            TipoVeta.RACHA,
                            e.delta,
                            racha = n,
                            habitId = habitId,
                            lote = lote,
                            nombre = habitos[habitId],
                            logroNuevo = logroId != null && !yaVisto,
                        )
                    }
                    PointsReason.BUY_FREEZER, PointsReason.BUY_ITEM -> null
                }
            }
        return Resultado(momentos, nuevas.maxOfOrNull { it.marca() }, ids)
    }

    /** Regla 7. */
    fun logros(
        badges: List<BadgeEntity>,
        vistos: Set<String>?,
        prefs: Settings,
        lote: Long,
    ): Pair<List<Momento>, Set<String>> {
        val nuevos = badges.filter { it.unlockedAtMillis > prefs.badgesSeenUntilMillis && (vistos == null || it.badgeId !in vistos) }
        return nuevos.map { Momento.Logro(it.badgeId, it.unlockedAtMillis, lote) } to badges.mapTo(mutableSetOf()) { it.badgeId }
    }

    /** Regla 8. */
    fun delJardin(
        anterior: GardenState?,
        actual: GardenState,
        lote: Long,
    ): List<Momento> {
        if (anterior == null) return emptyList()
        val especiales = (actual.specials - anterior.specials).map { Momento.Especial(it, lote) }
        val etapa =
            when {
                actual.plots.size > anterior.plots.size -> Momento.Etapa(actual.plots.lastIndex, parcelaNueva = true, lote = lote)
                else ->
                    actual.plots.indices.firstOrNull { i -> i < anterior.plots.size && actual.plots[i].stage > anterior.plots[i].stage }
                        ?.let { Momento.Etapa(it, parcelaNueva = false, lote = lote) }
            }
        return especiales + listOfNotNull(etapa)
    }
}

/**
 * Decide qué se celebra en Hoy y cuándo se da por celebrado. Solo lee lo que otros escribieron (libro, logros, jardín)
 * y escribe tres claves de ajustes: la marca del libro, `perfectDayCelebratedDay` y `badgesSeenUntilMillis`.
 * Se usa siempre desde el hilo principal.
 */
class CelebracionesViewModel(
    domainState: DomainStateRepository,
    rewards: RewardsRepository,
    private val settings: SettingsRepository,
    colaScope: CoroutineScope? = null,
) : ViewModel() {
    /** Los momentos que faltan por terminar de un lote y la marca que se escribe al cerrarlo (null: sin marca). */
    private class Lote(var pendientes: Int, val marca: MarcaLibro?)

    private val cola = ColaCelebraciones(colaScope ?: viewModelScope, alAbsorber = { it.forEach(::cerrarEnLote) }) // regla 3
    val actual: StateFlow<Momento?> = cola.actual

    // Estado de sesión (reglas 4, 7, 8 y 9).
    private var vistas: Set<String>? = null
    private var logrosVistos: Set<String>? = null
    private var jardinAnterior: GardenState? = null
    private var hayEmision = false
    private var marcaAnterior: MarcaLibro? = null
    private var idsAnteriores: Set<String>? = null
    private var libroCambioAntes = false

    /** Tras restaurar con los ajustes por delante del libro: lo próximo que cambie en el libro es lo restaurado. */
    private var esperandoLibro = false
    private var enHoy = false
    private var siguienteLote = 0L

    /** Ordenados por número: la marca solo avanza hasta el final del lote más antiguo aún abierto (regla 9). */
    private val lotes = sortedMapOf<Long, Lote>()

    init {
        viewModelScope.launch {
            combine(domainState.observe(), rewards.observeBadges(), settings.settings) { state, badges, prefs ->
                Triple(state, badges, prefs)
            }.collect { (state, badges, prefs) ->
                val ids = state.pointsLedger.mapTo(mutableSetOf()) { it.id }
                val cambioLibro = idsAnteriores != null && ids != idsAnteriores

                // Restaurar dentro de la sesión (regla 4): la marca era no-null y ya no hay ninguna, o han desaparecido
                // entradas del libro (solo una restauración lo borra). Sea cual sea el orden en que lleguen libro y
                // ajustes, lo que traiga se trata como un arranque: no se celebra nada viejo.
                val marcaBorrada = hayEmision && marcaAnterior != null && prefs.celebratedLedgerMark == null
                val libroReemplazado = vistas?.let { !ids.containsAll(it) } == true
                var arranque = false
                if (marcaBorrada || libroReemplazado) {
                    restaurar()
                    arranque = true
                    esperandoLibro = marcaBorrada && !libroReemplazado && !libroCambioAntes
                } else if (esperandoLibro && cambioLibro) {
                    arranque = true
                    esperandoLibro = false
                }
                libroCambioAntes = cambioLibro
                idsAnteriores = ids
                val primera = !hayEmision
                hayEmision = true
                marcaAnterior = prefs.celebratedLedgerMark

                // Purga por el repaso (regla 12).
                cola.descartar { ya(it, prefs) }.forEach(::cerrarEnLote)

                val lote = ++siguienteLote
                val libro =
                    DerivarMomentos.delLibro(
                        state.pointsLedger,
                        if (arranque) null else prefs.celebratedLedgerMark,
                        if (arranque) null else vistas,
                        prefs,
                        enHoy,
                        lote,
                        habitos = state.habits.associate { it.id to it.name },
                        badges = badges,
                    )
                val (logros, logrosIds) = DerivarMomentos.logros(badges, logrosVistos, prefs, lote)
                vistas = libro.vistas
                logrosVistos = logrosIds

                val momentos = libro.momentos + logros
                // Sin marca y sin libro (instalación nueva) se fija una de partida: así restaurar siempre se nota.
                val marca = libro.marcaLote ?: MarcaLibro(0, "").takeIf { primera && prefs.celebratedLedgerMark == null }
                if (momentos.isNotEmpty() || marca != null) {
                    lotes[lote] = Lote(momentos.size, marca)
                    cola.encolar(momentos)
                    avanzarMarca()
                }
            }
        }
    }

    private fun ya(
        m: Momento,
        prefs: Settings,
    ): Boolean =
        (m is Momento.Veta && m.tipo == TipoVeta.DIA && (m.dia ?: Int.MAX_VALUE) <= prefs.perfectDayCelebratedDay) ||
            (m is Momento.Logro && m.unlockedAtMillis <= prefs.badgesSeenUntilMillis)

    /** Como un arranque: nada viejo se celebra, lo que sonaba se corta y la marca se refija a la entrada más reciente. */
    private fun restaurar() {
        vistas = null
        lotes.clear()
        cola.descartar { it is Momento.Monedas || it is Momento.Veta }
        val sonando = cola.actual.value
        if (sonando is Momento.Monedas || sonando is Momento.Veta) cola.terminado()
    }

    private fun cerrarEnLote(m: Momento) {
        val lote = lotes[m.lote] ?: return
        lote.pendientes--
        avanzarMarca()
    }

    /** Escribe la marca de los lotes cerrados desde el más antiguo, hasta el primero que siga abierto. */
    private fun avanzarMarca() {
        var marca: MarcaLibro? = null
        while (lotes.isNotEmpty() && lotes.getValue(lotes.firstKey()).pendientes <= 0) {
            val cerrado = lotes.remove(lotes.firstKey())!!
            marca = listOfNotNull(marca, cerrado.marca).maxOrNull()
        }
        marca?.let(::escribirMarca)
    }

    /** Nunca hacia atrás. */
    private fun escribirMarca(marca: MarcaLibro) {
        viewModelScope.launch {
            settings.update { it.copy(celebratedLedgerMark = maxOf(it.celebratedLedgerMark ?: marca, marca)) }
        }
    }

    /** Regla 11: salir de Hoy da por terminado lo que suena (cuenta para su lote); el resto espera. */
    fun enHoy(visible: Boolean) {
        enHoy = visible
        if (visible) {
            esperandoLibro = false // una restauración se hace fuera de Hoy: al volver, el libro ya llegó
            cola.reanudar()
        } else {
            cola.actual.value?.let {
                marcarVisto(it) // si no, el repaso volvería a celebrarlo
                cerrarEnLote(it)
            }
            cola.pausar()
        }
    }

    /** Regla 8: lo que cambia en el jardín va en un lote propio, sin marca. */
    fun jardin(garden: GardenState) {
        val momentos = DerivarMomentos.delJardin(jardinAnterior, garden, ++siguienteLote)
        jardinAnterior = garden
        cola.encolar(momentos)
    }

    /** Reglas 9 y 10. */
    fun terminado(m: Momento) {
        if (cola.actual.value != m) return
        marcarVisto(m)
        cerrarEnLote(m)
        cola.terminado()
    }

    /** Regla 10: las claves que comparte con el repaso. */
    private fun marcarVisto(m: Momento) {
        when {
            m is Momento.Veta && m.tipo == TipoVeta.DIA && m.dia != null -> {
                val dia = m.dia
                viewModelScope.launch { settings.update { it.copy(perfectDayCelebratedDay = maxOf(it.perfectDayCelebratedDay, dia)) } }
            }
            m is Momento.Logro ->
                viewModelScope.launch {
                    settings.update { it.copy(badgesSeenUntilMillis = maxOf(it.badgesSeenUntilMillis, m.unlockedAtMillis)) }
                }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory { initializer { CelebracionesViewModel(container.domainState, container.rewards, container.settings) } }
    }
}
