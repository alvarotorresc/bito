package com.alvarotc.bito.sesion

import com.alvarotc.bito.data.habla.LoGordoRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.sesion.SesionStore
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Evento
import com.alvarotc.bito.domain.sesion.FocusClock
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.PlanSesion
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.domain.sesion.ResultadoSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.domain.sesion.Tramos
import com.alvarotc.bito.ui.AppVisibility
import com.alvarotc.bito.ui.notifications.QuickActionUseCase
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID

/**
 * La sesión viva, una por proceso (spec §4.1, §7, §10, §11.1, §15). El tiempo sale de
 * [RelojSesion] sobre el registro guardado en [store]; aquí solo se cambia el registro y se manda
 * al sonido, a lo de fuera de la app y a No molestar. Toda acción va bajo un cerrojo y sin
 * cancelarse a medias: dos «He terminado» seguidos marcan y pagan una vez.
 *
 * Con la app delante, un tic cada 250 ms anuncia cada frontera una sola vez. Con la app fuera, la
 * frontera es del receptor de fuera; al volver, el motor adopta el tramo en curso sin sonar.
 */
class MotorSesionAndroid(
    private val store: SesionStore,
    private val efectos: EfectosSesion,
    private val estado: DomainStateRepository,
    private val loGordo: LoGordoRepository,
    private val acciones: QuickActionUseCase,
    private val settings: SettingsRepository,
    private val aire: Aire,
    private val fondo: Fondo,
    private val fuera: FueraDeLaApp,
    private val noMolestar: NoMolestar,
    private val sonido: MotorSonido,
    private val tacto: Tacto,
    /** En Android, `ahoraDelSistema(context)`. */
    private val reloj: () -> Ahora,
    private val alcance: CoroutineScope,
    private val zona: () -> ZoneId = ZoneId::systemDefault,
    private val delante: () -> Boolean = { AppVisibility.visible },
    private val conTicker: Boolean = true,
    private val nuevoId: () -> String = { UUID.randomUUID().toString() },
) : MotorSesion {
    private val _instante = MutableStateFlow<Instante?>(null)
    private val _registro = MutableStateFlow<RegistroSesion?>(null)
    private val _borrador = MutableStateFlow<PlanSesion?>(null)

    override val instante: StateFlow<Instante?> = _instante.asStateFlow()
    override val registro: StateFlow<RegistroSesion?> = _registro.asStateFlow()
    override val borrador: StateFlow<PlanSesion?> = _borrador.asStateFlow()

    private val cerrojo = Mutex()

    /** El último tramo anunciado, con el id de su sesión. Solo en memoria: al recuperar se adopta sin sonar. */
    private var anunciado: Pair<String, Int>? = null

    /** La app estuvo fuera: el próximo tic con la app delante adopta el tramo sin sonar. */
    private var adoptarAlVolver = false

    private var ticker: Job? = null

    /** El último guardado en Ajustes de lo que se cambió en Preparar. */
    private var guardadoBorrador: Job? = null

    override suspend fun preparar(desde: Destino?) {
        val ajustes = settings.settings.first()
        val hoy = hoyDe(reloj(), ajustes)
        val gordo = loGordo.observe(listOf(hoy)).first()[hoy].orEmpty()
        when (val p = PrepararPlan.de(desde, ajustes, estado.snapshot(), hoy, gordo, noMolestar.permitido())) {
            is Preparacion.Borrador -> _borrador.value = p.plan
            is Preparacion.SumarPaso -> {
                _borrador.value = null
                acciones.log(p.habitoId, p.paso)
            }
            Preparacion.Nada -> _borrador.value = null
        }
    }

    /** Fondo, «Para entrar» y No molestar se recuerdan en Ajustes; tiempo y bloques no (spec §21.12). */
    override fun cambiarBorrador(plan: PlanSesion) {
        val antes = _borrador.value
        _borrador.value = plan
        val fondoCambia = antes == null || antes.fondo != plan.fondo
        val entradaCambia = antes == null || antes.entrada != plan.entrada
        val dndCambia = antes == null || antes.noMolestar != plan.noMolestar
        if (!fondoCambia && !entradaCambia && !dndCambia) return
        // En cola: dos cambios seguidos se guardan en su orden y el último manda.
        val anterior = guardadoBorrador
        guardadoBorrador =
            alcance.launch {
                anterior?.join()
                settings.update { s ->
                    s.copy(
                        sessionBackground = if (fondoCambia) plan.fondo else s.sessionBackground,
                        sessionEntry = if (entradaCambia) plan.entrada else s.sessionEntry,
                        sessionDnd = if (dndCambia) plan.noMolestar else s.sessionDnd,
                    )
                }
            }
    }

    /** Espera a que lo cambiado en Preparar esté en Ajustes. */
    internal suspend fun borradorGuardado() {
        guardadoBorrador?.join()
    }

    override suspend fun empezar(): Conflicto? = enCerrojo { empezarAqui() }

    override suspend fun seguirConLaViva() =
        enCerrojo {
            val viva = vivaAqui() ?: return@enCerrojo
            _borrador.value = null
            publicar(viva, RelojSesion.en(viva, reloj()))
        }

    override suspend fun dejarlaYEmpezar(): ResultadoAccion =
        enCerrojo {
            val viva = vivaAqui()
            var aviso: AvisoSesion? = null
            if (viva != null) {
                val ahora = reloj()
                val inst = RelojSesion.en(viva, ahora)
                efectos.intento(viva.plan.objetivo, hoyDe(ahora, settings.settings.first()), ahora.paredMillis)
                aviso = AvisoSesion.ComoIntento(efectos.nombre(viva.plan.objetivo))
                cerrarAqui(viva, inst, ahora, ResultadoSesion.CAMBIADA)
            }
            _borrador.value?.let { arrancar(it) }
            ResultadoAccion(bitos = 0, celebraciones = false, aviso = aviso)
        }

    override suspend fun pausar() =
        enCerrojo {
            val (r, ahora, inst) = vivoAhora() ?: return@enCerrojo
            if (inst.actual !is Tramo.Foco || inst.pausado) return@enCerrojo
            val r2 = conEvento(r, Evento.Pausa(inst.transcurridoMillis), ahora)
            fondo.callar()
            sonar(Snd.INTERRUPTOR_OFF, Haptica.TICK)
            fuera.alCambiar(r2, sonando())
        }

    override suspend fun seguir() =
        enCerrojo {
            val (r, ahora, inst) = vivoAhora() ?: return@enCerrojo
            if (!inst.pausado) return@enCerrojo
            val r2 = conEvento(r, Evento.Seguir(inst.transcurridoMillis), ahora)
            fondo.sonar(r.plan.fondo)
            sonar(Snd.INTERRUPTOR_ON, Haptica.TICK)
            fuera.alCambiar(r2, sonando())
        }

    override suspend fun sumar(minutos: Int) =
        enCerrojo {
            val (r, ahora, inst) = vivoAhora() ?: return@enCerrojo
            if (inst.actual !is Tramo.Foco || minutos <= 0) return@enCerrojo
            val r2 = conEvento(r, Evento.Sumar(inst.transcurridoMillis, minutos), ahora)
            sonar(Snd.TIC, Haptica.SEG)
            fuera.alCambiar(r2, sonando())
        }

    override suspend fun saltar() =
        enCerrojo {
            val (r, ahora, inst) = vivoAhora() ?: return@enCerrojo
            if (inst.actual !is Tramo.Respirar && inst.actual !is Tramo.Descanso) return@enCerrojo
            val r2 = r.copy(eventos = r.eventos + Evento.Saltar(inst.transcurridoMillis))
            store.guardar(r2)
            _registro.value = r2
            val nuevo = RelojSesion.en(r2, ahora)
            _instante.value = nuevo
            anunciar(r2, inst.tramo, nuevo, gesto = Haptica.TICK)
        }

    override suspend fun empezarDescanso() =
        enCerrojo {
            val (r, ahora, inst) = vivoAhora() ?: return@enCerrojo
            if (!inst.esperandoDescanso) return@enCerrojo
            val r2 = conEvento(r, Evento.EmpezarDescanso(inst.transcurridoMillis), ahora)
            fuera.alCambiar(r2, sonando())
        }

    override suspend fun heTerminado(): ResultadoAccion = enCerrojo { terminarAqui(Haptica.FALL) }

    override suspend fun siTerminado(): ResultadoAccion = enCerrojo { terminarAqui(Haptica.CONFIRM) }

    override suspend fun loDejo(): ResultadoAccion =
        enCerrojo {
            val minutos = dejarAqui(ResultadoSesion.LO_DEJO) ?: return@enCerrojo SIN_EFECTO
            ResultadoAccion(bitos = 0, celebraciones = false, aviso = AvisoSesion.LoDejamos(minutos))
        }

    override suspend fun sigoLuego(): ResultadoAccion =
        enCerrojo {
            dejarAqui(ResultadoSesion.SIGO_LUEGO) ?: return@enCerrojo SIN_EFECTO
            ResultadoAccion(bitos = 0, celebraciones = false, aviso = AvisoSesion.QuedaParaLuego)
        }

    /**
     * Una sesión nueva de 1 bloque de 5 min, directo a Foco, con el mismo objetivo y fondo. La que
     * acaba se guarda como sin respuesta: la pregunta se quedó sin contestar.
     */
    override suspend fun otraDeCinco() =
        enCerrojo {
            val r = vivaAqui() ?: return@enCerrojo
            val ahora = reloj()
            cerrarAqui(r, RelojSesion.en(r, ahora), ahora, ResultadoSesion.SIN_RESPUESTA)
            arrancar(
                r.plan.copy(
                    minutosFoco = MINUTOS_OTRA,
                    bloques = 1,
                    entrada = Entrada.DIRECTO,
                    noMolestar = r.plan.noMolestar && noMolestar.permitido(),
                ),
            )
        }

    /**
     * Lee el store. Una sesión que acabó otro día lógico se cierra sin respuesta, con intento y sin
     * aviso (spec §7.6). Una viva se publica tal como va, sin sonar lo pasado, y se re-arma lo de fuera.
     */
    override suspend fun recuperar() =
        enCerrojo {
            val r = store.leer()
            if (r == null) {
                limpiar()
                return@enCerrojo
            }
            val ahora = reloj()
            val inst = RelojSesion.en(r, ahora)
            val ajustes = settings.settings.first()
            val hoy = hoyDe(ahora, ajustes)
            if (inst.actual == null && diaDeLlegada(r, ahora, ajustes) != hoy) {
                efectos.intento(r.plan.objetivo, hoy, ahora.paredMillis)
                cerrarAqui(r, inst, ahora, ResultadoSesion.SIN_RESPUESTA)
                return@enCerrojo
            }
            publicar(r, inst)
            when (val tramo = inst.actual) {
                is Tramo.Respirar ->
                    if (!aire.sonando.value) {
                        aire.respirar(BreathingMode.CALM, r.anclaElapsed, tramo.ciclos, settings.settings.first().breathingMusicEnabled)
                    }
                is Tramo.Foco -> if (!inst.pausado && fondo.sonando.value == null) fondo.sonar(r.plan.fondo)
                is Tramo.Descanso -> Unit
                null -> noMolestar.restaurar(r.filtroPrevio)
            }
            fuera.alCambiar(r, sonando())
        }

    /**
     * Restaurar una copia con la sesión en marcha: se cierra antes sin escribir nada en la base (la
     * restauración la vacía justo después), sin aviso, y se devuelve No molestar.
     */
    suspend fun cerrarPorRestaurar() =
        enCerrojo {
            val r = store.leer() ?: _registro.value
            if (r != null) {
                aire.parar()
                fondo.callar(seco = true)
                noMolestar.restaurar(r.filtroPrevio)
                store.borrar()
            }
            limpiar()
            if (r != null) fuera.alCambiar(null, false)
        }

    /** Un paso del tic: anuncia la frontera cruzada con la app delante; con la app fuera, solo apunta adoptar. */
    internal suspend fun tic() =
        enCerrojo {
            val r = _registro.value ?: return@enCerrojo
            if (!delante()) {
                adoptarAlVolver = true
                return@enCerrojo
            }
            val inst = RelojSesion.en(r, reloj())
            val ultimo = anunciado?.takeIf { it.first == r.id }?.second
            if (adoptarAlVolver || ultimo == null) {
                adoptarAlVolver = false
                anunciado = r.id to inst.tramo
            } else if (inst.tramo != ultimo) {
                anunciar(r, ultimo, inst)
            }
            _instante.value = inst
        }

    // Dentro del cerrojo.

    private suspend fun empezarAqui(): Conflicto? {
        val plan = _borrador.value ?: return null
        val viva = vivaAqui()
        if (viva != null) {
            val inst = RelojSesion.en(viva, reloj())
            if (viva.plan.objetivo == plan.objetivo) {
                _borrador.value = null
                publicar(viva, inst)
                return null
            }
            return Conflicto.OtraEnMarcha(viva.plan.objetivo, inst.restaMillis, viva.plan.minutosFoco)
        }
        arrancar(plan)
        return null
    }

    private suspend fun arrancar(plan: PlanSesion) {
        val ajustes = settings.settings.first()
        val ahora = reloj()
        val previo = if (plan.noMolestar) noMolestar.poner() else null
        val r =
            RegistroSesion(
                id = nuevoId(),
                plan = plan,
                eventos = emptyList(),
                inicioPared = ahora.paredMillis,
                anclaElapsed = ahora.elapsedMillis,
                arranque = FocusClock.bootSignatureOf(ahora.paredMillis, ahora.elapsedMillis),
                filtroPrevio = previo,
                inicioDia = hoyDe(ahora, ajustes),
                arranques = ahora.arranques,
            )
        store.guardar(r)
        _borrador.value = null
        val inst = RelojSesion.en(r, ahora)
        publicar(r, inst)
        when (val tramo = inst.actual) {
            is Tramo.Respirar -> aire.respirar(BreathingMode.CALM, r.anclaElapsed, tramo.ciclos, ajustes.breathingMusicEnabled)
            is Tramo.Foco -> fondo.sonar(plan.fondo)
            else -> Unit
        }
        sonar(Snd.BROTE, Haptica.CONFIRM)
        fuera.alCambiar(r, sonando())
    }

    private suspend fun terminarAqui(haptica: Haptica): ResultadoAccion {
        val r = vivaAqui() ?: return SIN_EFECTO
        val ahora = reloj()
        val inst = RelojSesion.en(r, ahora)
        val objetivo = r.plan.objetivo
        val marcado = efectos.marcar(objetivo, inst.focoMillis, hoyDe(ahora, settings.settings.first()), ahora.paredMillis)
        val aviso = if (objetivo == Objetivo.Libre) null else AvisoSesion.Hecho(efectos.nombre(objetivo))
        cerrarAqui(r, inst, ahora, ResultadoSesion.HECHA)
        sonar(Snd.ASENTAR, haptica)
        return ResultadoAccion(bitos = marcado.bitos, celebraciones = marcado.celebraciones, aviso = aviso)
    }

    /** «Lo dejo» y «Sigo luego»: intento si es tarea, se cierra y `low`. Devuelve los minutos que cuentan. */
    private suspend fun dejarAqui(resultado: ResultadoSesion): Int? {
        val r = vivaAqui() ?: return null
        val ahora = reloj()
        val inst = RelojSesion.en(r, ahora)
        efectos.intento(r.plan.objetivo, hoyDe(ahora, settings.settings.first()), ahora.paredMillis)
        cerrarAqui(r, inst, ahora, resultado)
        tacto.haptica(Haptica.LOW)
        return minutosQueCuentan(inst.focoMillis)
    }

    /** Todas las salidas: callar, devolver No molestar, la fila de `sesiones`, borrar y avisar a lo de fuera. */
    private suspend fun cerrarAqui(
        r: RegistroSesion,
        inst: Instante,
        ahora: Ahora,
        resultado: ResultadoSesion,
    ) {
        aire.parar()
        fondo.callar(seco = true)
        noMolestar.restaurar(r.filtroPrevio)
        efectos.guardarFila(r, inst, ahora.paredMillis, resultado)
        store.borrar()
        limpiar()
        fuera.alCambiar(null, false)
    }

    /** Lo que suena y vibra al cruzar una frontera (spec §7). [gesto] sustituye la háptica cuando la frontera la cruza un toque. */
    private suspend fun anunciar(
        r: RegistroSesion,
        desde: Int,
        inst: Instante,
        gesto: Haptica? = null,
    ) {
        val previo = Tramos.de(r.plan).getOrNull(desde)
        when (inst.actual) {
            is Tramo.Foco ->
                if (previo is Tramo.Respirar) {
                    aire.parar()
                    fondo.sonar(r.plan.fondo)
                    sonar(Snd.ALEJAR, gesto ?: Haptica.TICK)
                } else {
                    fondo.sonar(r.plan.fondo)
                    sonar(Snd.BROTE, gesto ?: Haptica.RISE)
                }
            is Tramo.Descanso -> {
                fondo.callar()
                sonar(Snd.CAMPANILLA, gesto ?: Haptica.CLICK)
            }
            null -> {
                if (previo is Tramo.Respirar) aire.parar()
                fondo.callar()
                noMolestar.restaurar(r.filtroPrevio)
                sonar(Snd.CAMPANILLA, gesto ?: Haptica.CONFIRM)
            }
            is Tramo.Respirar -> Unit
        }
        anunciado = r.id to inst.tramo
        fuera.alCambiar(r, sonando())
    }

    private suspend fun vivaAqui(): RegistroSesion? = _registro.value ?: store.leer()

    private suspend fun vivoAhora(): Triple<RegistroSesion, Ahora, Instante>? {
        val r = vivaAqui() ?: return null
        val ahora = reloj()
        return Triple(r, ahora, RelojSesion.en(r, ahora))
    }

    private suspend fun conEvento(
        r: RegistroSesion,
        evento: Evento,
        ahora: Ahora,
    ): RegistroSesion {
        val r2 = r.copy(eventos = r.eventos + evento)
        store.guardar(r2)
        publicar(r2, RelojSesion.en(r2, ahora))
        return r2
    }

    /** Publica la sesión tal como va y adopta su tramo sin sonar. */
    private fun publicar(
        r: RegistroSesion,
        inst: Instante,
    ) {
        _registro.value = r
        _instante.value = inst
        anunciado = r.id to inst.tramo
        adoptarAlVolver = false
        if (conTicker && ticker?.isActive != true) {
            ticker =
                alcance.launch {
                    while (isActive) {
                        delay(TIC_MILLIS)
                        tic()
                    }
                }
        }
    }

    private fun limpiar() {
        _registro.value = null
        _instante.value = null
        anunciado = null
        adoptarAlVolver = false
        ticker?.cancel()
        ticker = null
    }

    private fun sonar(
        snd: Snd,
        haptica: Haptica,
    ) {
        sonido.play(snd)
        tacto.haptica(haptica)
    }

    private fun sonando(): Boolean = aire.sonando.value || fondo.sonando.value != null

    private fun hoyDe(
        ahora: Ahora,
        ajustes: Settings,
    ): LogicalDay = LogicalDays.logicalDayOf(ahora.paredMillis, ajustes.dayCutoffMinutes, zona())

    /** El día lógico en que la sesión llegó a Terminada: Terminada espera hasta que ese día cambie. */
    private fun diaDeLlegada(
        r: RegistroSesion,
        ahora: Ahora,
        ajustes: Settings,
    ): LogicalDay {
        var bajo = 0L
        var alto = RelojSesion.transcurrido(r, ahora)
        while (bajo < alto) {
            val medio = bajo + (alto - bajo) / 2
            if (RelojSesion.enSesion(r, medio).actual == null) alto = medio else bajo = medio + 1
        }
        return LogicalDays.logicalDayOf(RelojSesion.paredDe(r, alto, ahora), ajustes.dayCutoffMinutes, zona())
    }

    private suspend fun <T> enCerrojo(bloque: suspend () -> T): T = withContext(NonCancellable) { cerrojo.withLock { bloque() } }

    private companion object {
        const val TIC_MILLIS = 250L
        const val MINUTOS_OTRA = 5
        val SIN_EFECTO = ResultadoAccion(bitos = 0, celebraciones = false, aviso = null)
    }
}
