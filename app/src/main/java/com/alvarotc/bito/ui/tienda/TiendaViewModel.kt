package com.alvarotc.bito.ui.tienda

import android.content.res.Resources
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.GardenEngine
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Movimientos
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.Sealing
import com.alvarotc.bito.domain.Streaks
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.LightPhase
import com.alvarotc.bito.domain.model.PointsLedgerEntry
import com.alvarotc.bito.domain.model.equippedSetOf
import com.alvarotc.bito.ui.habi.minuteTicks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * El estado y las acciones de la tienda. La prueba ([PruebaTienda]) es estado de pantalla: nunca toca Room.
 * Solo [comprar], [tocarPieza] sobre algo que ya es tuyo, [comprarCongelador] y [deshacerCongelador] escriben,
 * y solo por [RewardsRepository]. Lee el reloj de [reloj] y nunca `System.currentTimeMillis` suelto.
 */
class TiendaViewModel(
    private val domainState: DomainStateRepository,
    private val rewards: RewardsRepository,
    private val settings: SettingsRepository,
    private val reloj: RelojTienda,
    private val recursos: () -> Resources,
    private val saved: SavedStateHandle,
    private val economy: EconomyConfig = EconomyConfig(),
    // Un tick por minuto para que la barra de un disfraz siga a la medianoche del día lógico. Por defecto un solo
    // tick: los tests con advanceUntilIdle no pueden convivir con un bucle infinito. La fábrica pasa minuteTicks().
    clockTicks: Flow<Unit> = flowOf(Unit),
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel(), TiendaAcciones {
    private val pestana = MutableStateFlow(pestanaGuardada())
    private val pruebaElegida = MutableStateFlow(pruebaGuardada())

    // La luz de entrada necesita saber si el día está cerrado, que es dato de Room: hasta que llega no hay estado.
    private val luz = MutableStateFlow<LuzTienda?>(null)

    // Un solo cobro por toque: comprar y comprarCongelador ignoran otro toque mientras uno escribe.
    private val ocupado = AtomicBoolean(false)
    private val ultimoCongelador = AtomicReference<String?>(null)
    private val canalEventos = Channel<EventoTienda>(Channel.BUFFERED)

    /** Flujo de un solo uso: cada evento lo recibe quien esté recogiendo, o el siguiente que lo haga. */
    val eventos: Flow<EventoTienda> = canalEventos.receiveAsFlow()

    private data class Datos(
        val estado: DomainState,
        val filas: List<CustomizationItemEntity>,
        val saldo: Int,
        val ajustes: Settings,
        val recientes: List<PointsLedgerEntry>,
    )

    val uiState: StateFlow<TiendaUiState> =
        combine(
            combine(
                domainState.observe(),
                rewards.observeOwnedItems(),
                rewards.observeBalance(),
                settings.settings,
                rewards.observeRecentMovements(MOVIMIENTOS),
                ::Datos,
            ),
            pestana,
            luz.filterNotNull(),
            pruebaElegida,
            clockTicks,
        ) { datos, pestana, luz, prueba, _ -> construir(datos, pestana, luz, prueba) }
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), relleno())

    init {
        viewModelScope.launch { luz.compareAndSet(null, luzDeEntrada()) }
    }

    // ───────── Estado ─────────

    private fun construir(
        datos: Datos,
        pestana: PestanaTienda,
        luz: LuzTienda,
        elegida: PruebaTienda?,
    ): TiendaUiState {
        val dia = LogicalDays.logicalDayOf(reloj.ahora(), datos.ajustes.dayCutoffMinutes, reloj.zona())
        val hoy = LocalDate.ofEpochDay(dia.toLong())
        val poseidos = datos.filas.mapTo(mutableSetOf()) { it.itemId }
        val puestos = equippedSetOf(datos.filas.filter { it.equipped }.map { it.itemId })
        val prueba = elegida?.takeIf { vigente(it, poseidos) }
        val mejorRacha = datos.estado.habits.maxOfOrNull { Streaks.streaksOf(datos.estado, it, dia).best } ?: 0
        return TiendaUiState(
            saldo = datos.saldo,
            pestana = pestana,
            luz = luz,
            prueba = prueba,
            barra = BarrasTienda.barraDe(prueba, poseidos, datos.saldo, mejorRacha, hoy, recursos()),
            puestos = puestos,
            look = conPrueba(puestos, prueba),
            poseidos = poseidos,
            objetosJardin = datos.filas.filter { it.category == CustomizationCategory.GARDEN_OBJECT }.mapTo(mutableSetOf()) { it.itemId },
            congeladores = PointsEngine.freezersOwned(datos.estado),
            mejorRacha = mejorRacha,
            hoy = hoy,
            movimientos = Movimientos.de(datos.recientes, datos.estado, dia),
            economia = economy,
        )
    }

    /** El valor de antes del primero real. Nada se decide con él: [TiendaUiState.cargado] es falso. */
    private fun relleno(): TiendaUiState =
        TiendaUiState(
            saldo = 0,
            pestana = pestana.value,
            luz = LuzTienda.DIA,
            prueba = null,
            barra = null,
            puestos = EquippedSet(),
            look = EquippedSet(),
            poseidos = emptySet(),
            objetosJardin = emptySet(),
            congeladores = 0,
            mejorRacha = 0,
            hoy = Instant.ofEpochMilli(reloj.ahora()).atZone(reloj.zona()).toLocalDate(),
            cargado = false,
        )

    /** Una prueba restaurada que ya es tuya o que ya no existe se descarta (Review Focus 3). */
    private fun vigente(
        prueba: PruebaTienda,
        poseidos: Set<String>,
    ): Boolean =
        when (prueba) {
            is PruebaTienda.Pieza ->
                HabiCatalog.byId(prueba.itemId)?.let {
                    it.category != CustomizationCategory.GARDEN_OBJECT && !it.default && it.id !in poseidos
                } ?: false
            is PruebaTienda.Objeto ->
                HabiCatalog.byId(prueba.itemId)?.let {
                    it.category == CustomizationCategory.GARDEN_OBJECT && it.id !in poseidos
                } ?: false
            is PruebaTienda.Especial -> GardenSpecial.entries.any { it.badgeId == prueba.logroId }
        }

    /** Lo que se ve en Habi: lo puesto con la pieza probada encima; un disfraz no tapa lo que se prueba debajo. */
    private fun conPrueba(
        puestos: EquippedSet,
        prueba: PruebaTienda?,
    ): EquippedSet {
        val item = (prueba as? PruebaTienda.Pieza)?.let { HabiCatalog.byId(it.itemId) } ?: return puestos
        val id = item.id
        val conPieza =
            when (item.category) {
                CustomizationCategory.BODY_COLOR -> puestos.copy(bodyColor = id)
                CustomizationCategory.EYE_COLOR -> puestos.copy(eyeColor = id)
                CustomizationCategory.HEAD -> puestos.copy(head = id)
                CustomizationCategory.FACE -> puestos.copy(face = id)
                CustomizationCategory.NECK -> puestos.copy(neck = id)
                CustomizationCategory.FEET -> puestos.copy(feet = id)
                CustomizationCategory.COSTUME -> puestos.copy(costume = id)
                CustomizationCategory.GARDEN_OBJECT -> puestos
            }
        return if (item.category in TAPADAS_POR_DISFRAZ) conPieza.copy(costume = null) else conPieza
    }

    private suspend fun luzDeEntrada(): LuzTienda {
        val ajustes = settings.settings.first()
        val zona = reloj.zona()
        val ahora = reloj.ahora()
        val dia = LogicalDays.logicalDayOf(ahora, ajustes.dayCutoffMinutes, zona)
        if (Sealing.isSealed(domainState.snapshot(), dia)) return LuzTienda.NOCHE
        val hora = Instant.ofEpochMilli(ahora).atZone(zona).toLocalTime()
        return when (GardenEngine.lightAt(LocalTime.of(hora.hour, hora.minute)).phase) {
            LightPhase.MANANA, LightPhase.DIA -> LuzTienda.DIA
            LightPhase.TARDE -> LuzTienda.TARDE
            LightPhase.NOCHE -> LuzTienda.NOCHE
        }
    }

    private suspend fun diaLogico() = LogicalDays.logicalDayOf(reloj.ahora(), settings.settings.first().dayCutoffMinutes, reloj.zona())

    // ───────── Acciones ─────────

    override fun cambiarPestana(p: PestanaTienda) {
        pestana.value = p
        saved[CLAVE_PESTANA] = p.name
        fijarPrueba(null)
    }

    override fun cambiarLuz(l: LuzTienda) {
        luz.value = l
    }

    override fun tocarPieza(itemId: String) {
        val item = HabiCatalog.byId(itemId) ?: return
        viewModelScope.launch {
            val filas = rewards.observeOwnedItems().first()
            val tuyo = item.default || filas.any { it.itemId == itemId }
            if (item.category == CustomizationCategory.GARDEN_OBJECT) {
                // Ya tuyo no abre barra: la pestaña lo mece. Si no, se prueba.
                if (!tuyo) fijarPrueba(PruebaTienda.Objeto(itemId))
                return@launch
            }
            if (!tuyo) {
                fijarPrueba(PruebaTienda.Pieza(itemId))
                return@launch
            }
            fijarPrueba(null)
            val puestos = equippedSetOf(filas.filter { it.equipped }.map { it.itemId })
            val tapada = puestos.costume != null && item.category in TAPADAS_POR_DISFRAZ
            when {
                tapada -> rewards.equipOverCostume(itemId)
                item.default || itemId in puestos.worn -> rewards.unequip(item.category)
                else -> rewards.equip(itemId)
            }
        }
    }

    override fun tocarEspecial(logroId: String) {
        if (GardenSpecial.entries.none { it.badgeId == logroId }) return
        fijarPrueba(PruebaTienda.Especial(logroId))
    }

    override fun quitarPrueba() = fijarPrueba(null)

    override fun comprar(itemId: String) {
        val item = HabiCatalog.byId(itemId) ?: return
        if (!ocupado.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                if (rewards.purchase(item, diaLogico(), reloj.ahora())) {
                    fijarPrueba(null)
                    canalEventos.send(EventoTienda.Comprado(itemId))
                }
                // Si falla, la barra se queda: el saldo y la ventana llegan solos por el estado (H6).
            } finally {
                ocupado.set(false)
            }
        }
    }

    override fun comprarCongelador() {
        if (!ocupado.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val antes = PointsEngine.freezersOwned(domainState.snapshot())
                val refId = rewards.buyFreezer(economy.freezerPrice, diaLogico(), reloj.ahora())
                if (refId != null) {
                    ultimoCongelador.set(refId)
                    canalEventos.send(EventoTienda.CongeladorComprado(refId, antes + 1))
                }
            } finally {
                ocupado.set(false)
            }
        }
    }

    override fun deshacerCongelador() {
        // Solo la última compra, y una vez: quien llega después con el aviso ya vencido no encuentra nada.
        val refId = ultimoCongelador.getAndSet(null) ?: return
        viewModelScope.launch {
            if (rewards.undoFreezerPurchase(refId)) canalEventos.send(EventoTienda.CongeladorDeshecho)
        }
    }

    // ───────── Restaurar (D2): la pestaña y el id de la prueba; la luz no ─────────

    private fun fijarPrueba(prueba: PruebaTienda?) {
        pruebaElegida.value = prueba
        saved[CLAVE_PRUEBA] = prueba?.let(::codificar)
    }

    private fun pestanaGuardada(): PestanaTienda =
        saved.get<String>(CLAVE_PESTANA)?.let { nombre -> PestanaTienda.entries.firstOrNull { it.name == nombre } } ?: PestanaTienda.HABI

    private fun pruebaGuardada(): PruebaTienda? {
        val texto = saved.get<String>(CLAVE_PRUEBA) ?: return null
        val tipo = texto.substringBefore(':')
        val id = texto.substringAfter(':', "")
        if (id.isEmpty()) return null
        return when (tipo) {
            "pieza" -> PruebaTienda.Pieza(id)
            "objeto" -> PruebaTienda.Objeto(id)
            "especial" -> PruebaTienda.Especial(id)
            else -> null
        }
    }

    private fun codificar(prueba: PruebaTienda): String =
        when (prueba) {
            is PruebaTienda.Pieza -> "pieza:${prueba.itemId}"
            is PruebaTienda.Objeto -> "objeto:${prueba.itemId}"
            is PruebaTienda.Especial -> "especial:${prueba.logroId}"
        }

    companion object {
        const val CLAVE_PESTANA = "tienda_pestana"
        const val CLAVE_PRUEBA = "tienda_prueba"

        /** Los últimos movimientos que enseña «Tus bitos». */
        private const val MOVIMIENTOS = 5

        /** Los ejes que un disfraz tapa al pintar (los ojos no). */
        private val TAPADAS_POR_DISFRAZ =
            setOf(
                CustomizationCategory.HEAD,
                CustomizationCategory.FACE,
                CustomizationCategory.NECK,
                CustomizationCategory.FEET,
                CustomizationCategory.BODY_COLOR,
            )

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TiendaViewModel(
                        container.domainState,
                        container.rewards,
                        container.settings,
                        container.relojTienda,
                        container.recursosTienda,
                        createSavedStateHandle(),
                        clockTicks = minuteTicks(),
                    )
                }
            }
    }
}
