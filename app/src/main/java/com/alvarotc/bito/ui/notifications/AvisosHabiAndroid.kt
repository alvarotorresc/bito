package com.alvarotc.bito.ui.notifications

import android.content.Context
import android.content.Intent
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.habla.AvisosAceptadosRepository
import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.ComplianceStatus
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Sealing
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.CatalogoHabla
import com.alvarotc.bito.domain.habla.CatalogoHablaVacio
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Contextos
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.HechosCache
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.NocheEnSilencio
import com.alvarotc.bito.domain.habla.PlanAvisos
import com.alvarotc.bito.domain.habla.Redactor
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Ritmo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoAviso
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Lo que hace falta saber de hoy para planificar los avisos. Los tests del carril de avisos lo sustituyen. */
data class PlanDeHoy(val hechos: Hechos, val memoria: Memoria, val ctx: Contexto)

/**
 * Los avisos de Habi sobre Android (spec §6). [plan] lee estado, memoria y ajustes del contenedor (ruling P8); [fuente] da
 * la vigente de Hoy y recibe las respuestas; [aceptados] guarda los «Vale, a las…».
 *
 * [replanificar] y [cancelarAceptados] no leen la fuente: la fuente los llama con su candado cogido.
 */
class AvisosHabiAndroid(
    private val context: Context,
    private val contenedor: () -> AppContainer,
    private val plan: suspend () -> PlanDeHoy = { planDeHoyDe(contenedor()) },
    private val fuente: () -> FuentePropuestas = { contenedor().propuestas },
    private val aceptados: () -> AvisosAceptadosRepository = { contenedor().avisosAceptados },
    private val catalogo: () -> CatalogoHabla = { CatalogoHablaVacio },
) : AvisosHabi {
    /**
     * Programa lo que queda de hoy y quita lo que ya no toca: la mañana y la noche que hoy no salen pasan a mañana a su
     * hora de siempre (así la cadena sigue); los aceptados que salen del plan pierden su alarma, y su recordatorio si el
     * hábito ya está hecho o el día sellado; los pendientes de otros días, también.
     */
    override suspend fun replanificar() {
        val p = plan()
        val ctx = p.ctx
        val avisos = PlanAvisos.de(p.hechos, p.memoria, ctx)
        for (tipo in listOf(TipoAviso.MANANA, TipoAviso.NOCHE)) {
            val aviso = avisos.find { it.tipo == tipo }
            if (aviso != null) {
                programar(ReminderScheduler.slotDe(aviso), ctx.ahoraMillis, ctx)
            } else {
                programar(base(tipo, ctx), inicioDeManana(ctx), ctx)
            }
        }
        if (p.hechos.diaSellado) Notifier.cancelNoche(context)

        val vivos = avisos.filter { it.tipo == TipoAviso.ACEPTADO }
        vivos.forEach { programar(ReminderScheduler.slotDe(it), ctx.ahoraMillis, ctx) }
        val claves = vivos.map { it.clave }.toSet()
        val hechos = p.hechos.habitos.filter { it.hechoHoy }.map { it.id }.toSet()
        delDia(p).forEachIndexed { i, a ->
            if (a.id !in claves) ReminderScheduler.cancelar(context, slotAceptado(a.id))
            if (p.hechos.diaSellado || a.habitId in hechos) Notifier.cancelAceptado(context, i)
        }
        aceptados().pendientes().filter { it.dia != ctx.hoy }.forEach { ReminderScheduler.cancelar(context, slotAceptado(it.id)) }
    }

    /** Antes de vaciar `aviso_aceptado` al restaurar: sus alarmas se van con ellos. */
    override suspend fun cancelarAceptados(ids: List<String>) {
        ids.forEach { ReminderScheduler.cancelar(context, slotAceptado(it)) }
    }

    /**
     * Lo que dice un aviso de Habi cuando suena su alarma. Se mira el plan del día entero, no solo lo que queda, para
     * que una alarma que llega tarde (sin alarmas exactas) aún hable; una que llega antes de su minuto se reprograma.
     */
    suspend fun evaluar(
        kind: SlotKind,
        key: String,
    ): ReminderUseCase.Outcome {
        val p = plan()
        val ctx = p.ctx
        val tipo = TipoAviso.entries.find { ReminderScheduler.kindDe(it) == kind } ?: return ReminderUseCase.Outcome.Stale
        val delDiaEntero = PlanAvisos.de(p.hechos, p.memoria, ctx.copy(ahoraMin = ctx.corteDia))
        val aviso =
            delDiaEntero.find { it.tipo == tipo && it.clave == key }
                ?: return if (tipo == TipoAviso.ACEPTADO) ReminderUseCase.Outcome.Stale else ReminderUseCase.Outcome.Silent(base(tipo, ctx))
        val slot = ReminderScheduler.slotDe(aviso)
        if (orden(aviso.minuto, ctx) > orden(ctx.ahoraMin, ctx)) return ReminderUseCase.Outcome.Silent(slot)
        return when (tipo) {
            TipoAviso.MANANA -> {
                val vigente = fuente().propuesta(Ambito.HOY).first() ?: return ReminderUseCase.Outcome.Silent(slot)
                ReminderUseCase.Outcome.Manana(slot, vigente, tituloManana(p), ctx.voz, p.hechos.animoSemana)
            }
            TipoAviso.NOCHE ->
                ReminderUseCase.Outcome.Noche(
                    slot,
                    redactar(Bolsa.AV_NOCHE_TITULO, Situacion.NOCHE, huecosDia(p), p, titulo = true),
                    redactar(Bolsa.AV_NOCHE_CUERPO, Situacion.NOCHE, huecosNoche(p), p, titulo = false),
                    ctx.voz,
                )
            TipoAviso.ACEPTADO -> aceptado(slot, p) ?: ReminderUseCase.Outcome.Stale
        }
    }

    /** Las acciones de los avisos (Vale, Hoy no o Luego, Más tarde, Mañana). Una acción desconocida no hace nada. */
    suspend fun accion(intent: Intent) {
        when (intent.action) {
            AvisoHabiReceiver.ACTION_VALE -> vale(intent)
            AvisoHabiReceiver.ACTION_HOY_NO -> hoyNo(intent)
            AvisoHabiReceiver.ACTION_MAS_TARDE -> masTarde(intent)
            AvisoHabiReceiver.ACTION_MANANA -> Notifier.cancelNoche(context)
        }
    }

    /**
     * «Vale» desde la mañana. Con [Ritmo.TOPE_ACEPTADOS_DIA] avisos ya hoy (en cualquier estado) no se acepta otro. Si
     * tras responder no hay aviso nuevo de esa propuesta (id de otro día, RF4, o día duro sin hora), solo se quita la
     * notificación; si lo hay, la mañana pasa a ser la del aceptado y se programa su alarma.
     */
    private suspend fun vale(intent: Intent) {
        val propuestaId = intent.getStringExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID) ?: return
        val hora = intent.getIntExtra(AvisoHabiReceiver.EXTRA_HORA, Notifier.SIN_HORA).takeIf { it != Notifier.SIN_HORA }
        val p = plan()
        val repo = aceptados()
        if (repo.observeDe(p.ctx.hoy).first().size >= Ritmo.TOPE_ACEPTADOS_DIA) {
            Notifier.cancelManana(context)
            return
        }
        fuente().responder(propuestaId, Respuesta.VALE, hora)
        val nuevo = repo.observeDe(p.ctx.hoy).first().find { it.propuestaId == propuestaId && it.estado == EstadoAviso.PENDIENTE }
        if (nuevo == null) {
            Notifier.cancelManana(context)
            return
        }
        val huecos = huecosAceptado(p, nuevo.minuto)
        Notifier.showAceptado(
            context,
            redactar(Bolsa.AV_ACEPTADO_TITULO, Situacion.CONFIRMA_VALE, huecos, p, titulo = true),
            redactar(Bolsa.AV_ACEPTADO_CUERPO, Situacion.CONFIRMA_VALE, huecos, p, titulo = false),
            p.ctx.voz,
        )
        programar(Slot(SlotKind.ACEPTADO, nuevo.id, nuevo.minuto), p.ctx.ahoraMillis, p.ctx)
    }

    private suspend fun hoyNo(intent: Intent) {
        val propuestaId = intent.getStringExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID) ?: return
        val respuesta =
            intent.getStringExtra(AvisoHabiReceiver.EXTRA_RESPUESTA)?.let { r -> Respuesta.entries.find { it.name == r } }
                ?: Respuesta.HOY_NO
        fuente().responder(propuestaId, respuesta, null)
        Notifier.cancelManana(context)
    }

    /** «Más tarde»: [PlanAvisos.posponer] decide si se puede; el aviso vuelve a pendiente con su minuto nuevo. */
    private suspend fun masTarde(intent: Intent) {
        val id = intent.getStringExtra(AvisoHabiReceiver.EXTRA_AVISO_ID) ?: return
        val p = plan()
        val delDia = delDia(p)
        val indice = delDia.indexOfFirst { it.id == id }
        if (indice >= 0) Notifier.cancelAceptado(context, indice)
        val aviso = delDia.getOrNull(indice) ?: return
        val minuto = PlanAvisos.posponer(aviso, p.ctx) ?: return
        val repo = aceptados()
        repo.posponer(id, minuto)
        repo.cambiarEstado(id, EstadoAviso.PENDIENTE)
        programar(Slot(SlotKind.ACEPTADO, id, minuto), p.ctx.ahoraMillis, p.ctx)
    }

    private fun aceptado(
        slot: Slot,
        p: PlanDeHoy,
    ): ReminderUseCase.Outcome.Aceptado? {
        val delDia = delDia(p)
        val indice = delDia.indexOfFirst { it.id == slot.key }
        val aviso = delDia.getOrNull(indice) ?: return null
        val habito = p.hechos.habitos.find { it.id == aviso.habitId } ?: return null
        val target =
            when (habito.tipo) {
                TipoHabito.CHECK -> QuickTarget(habito.id, habito.nombre, amount = 1, isCheck = true)
                TipoHabito.CONTADOR ->
                    QuickTarget(habito.id, habito.nombre, habito.paso, isCheck = false, hecho = habito.progreso, objetivo = habito.objetivo)
                else -> null
            }
        return ReminderUseCase.Outcome.Aceptado(
            slot = slot,
            avisoId = aviso.id,
            indice = indice,
            nombre = habito.nombre,
            cuerpo = redactar(Bolsa.AV_RECORDATORIO, Situacion.CONFIRMA_VALE, huecosDia(p), p, titulo = false),
            target = target,
            masTarde = PlanAvisos.posponer(aviso, p.ctx) != null,
            personality = p.ctx.voz,
        )
    }

    /** En ausencia, `av_ausencia`; con lo gordo o su sugerencia, `av_manana_titulo_gordo`; si no, `av_manana_titulo`. */
    private fun tituloManana(p: PlanDeHoy): TextoHabi {
        val huecos = huecosDia(p)
        val bolsa =
            when {
                p.hechos.diasSinActividad + 1 >= Ritmo.DIAS_AUSENCIA -> Bolsa.AV_AUSENCIA
                Hueco.GORDO in huecos -> Bolsa.AV_MANANA_TITULO_GORDO
                else -> Bolsa.AV_MANANA_TITULO
            }
        return redactar(bolsa, Situacion.SALUDO, huecos, p, titulo = true)
    }

    /** Los títulos de aviso siempre abren (`forzarApertura`). Sin recuerdo: los avisos no mencionan. */
    private fun redactar(
        bolsa: Bolsa,
        situacion: Situacion,
        huecos: Map<Hueco, ValorHueco>,
        p: PlanDeHoy,
        titulo: Boolean,
    ): TextoHabi {
        val c = Candidata(situacion, ref = bolsa.clave, huecos = huecos, acciones = emptyList(), nucleo = bolsa)
        val azar = Random(Redactor.semilla(p.ctx.hoy, situacion, bolsa.clave, p.ctx.voz, 0))
        return Redactor.redactar(c, p.ctx, catalogo(), p.memoria, azar, forzarApertura = titulo).frase
    }

    /** Nombre (si lo hay), cómo va el día y lo gordo de hoy o, si no hay, su sugerencia. */
    private fun huecosDia(p: PlanDeHoy): Map<Hueco, ValorHueco> {
        val h = p.hechos
        val gordo = nombres(h.gordo, h).ifEmpty { nombres(listOfNotNull(h.sugerenciaGordo), h) }
        return buildMap {
            p.ctx.nombre?.let { put(Hueco.NOMBRE, ValorHueco.Texto(it)) }
            put(Hueco.HECHOS, ValorHueco.Numero(h.tuDiaHechos))
            put(Hueco.TOTAL, ValorHueco.Numero(h.tuDiaTotal))
            if (gordo.isNotEmpty()) put(Hueco.GORDO, ValorHueco.Lista(gordo))
        }
    }

    /** La noche nombra en `{tarea}` lo gordo que quedó sin hacer. */
    private fun huecosNoche(p: PlanDeHoy): Map<Hueco, ValorHueco> {
        val sinHacer = nombres(p.hechos.gordo - p.hechos.gordoHecho.toSet(), p.hechos)
        return if (sinHacer.isEmpty()) huecosDia(p) else huecosDia(p) + (Hueco.TAREA to ValorHueco.Lista(sinHacer))
    }

    private fun huecosAceptado(
        p: PlanDeHoy,
        minuto: Int,
    ): Map<Hueco, ValorHueco> =
        huecosDia(p).filterKeys { it == Hueco.NOMBRE || it == Hueco.GORDO } + (Hueco.HORA to ValorHueco.Hora(minuto))

    private fun nombres(
        refs: List<RefGordo>,
        h: Hechos,
    ): List<String> =
        refs.mapNotNull { r ->
            when (r.tipo) {
                TipoRefGordo.HABITO -> h.habitos.find { it.id == r.id }?.nombre
                TipoRefGordo.TAREA -> h.tareas.find { it.id == r.id }?.titulo
            }
        }

    /** Los aceptados de hoy en el orden en que se aceptaron: el índice es el de su notificación. */
    private fun delDia(p: PlanDeHoy): List<AvisoAceptado> = p.memoria.avisos.filter { it.dia == p.ctx.hoy }.sortedBy { it.creadoMillis }

    /** La hora de siempre de la mañana o la noche, con el silencio, para mantener la cadena los días que no salen. */
    private fun base(
        tipo: TipoAviso,
        ctx: Contexto,
    ): Slot =
        when (tipo) {
            TipoAviso.MANANA ->
                Slot(SlotKind.HABI_MANANA, PlanAvisos.CLAVE_MANANA, PlanAvisos.ajustarConservado(ctx.horaManana, ctx) ?: ctx.horaManana)
            else ->
                Slot(
                    SlotKind.HABI_NOCHE,
                    PlanAvisos.CLAVE_NOCHE,
                    NocheEnSilencio.minuto(ctx.horaNoche, ctx.silencio, ctx.corteDia) ?: ctx.horaNoche,
                )
        }

    private fun slotAceptado(id: String) = Slot(SlotKind.ACEPTADO, id, 0)

    private fun programar(
        slot: Slot,
        desdeMillis: Long,
        ctx: Contexto,
    ) {
        // Una SecurityException por un permiso retirado a destiempo no debe tumbar el resto.
        runCatching { ReminderScheduler.scheduleSlot(context, slot, desdeMillis, ctx.zona) }
    }

    /** Un milisegundo antes de que empiece el día lógico de mañana. */
    private fun inicioDeManana(ctx: Contexto): Long =
        LocalDate
            .ofEpochDay(ctx.hoy + 1L)
            .atTime(ctx.corteDia / 60, ctx.corteDia % 60)
            .atZone(ctx.zona)
            .toInstant()
            .toEpochMilli() - 1

    private fun orden(
        minuto: Int,
        ctx: Contexto,
    ): Int = if (minuto < ctx.corteDia) minuto + MINUTOS_DIA else minuto

    private companion object {
        const val MINUTOS_DIA = 24 * 60
    }
}

/** El plan de hoy desde el contenedor: el estado, la memoria y los ajustes de ahora. */
internal suspend fun planDeHoyDe(
    contenedor: AppContainer,
    ahora: Long = System.currentTimeMillis(),
    zona: ZoneId = ZoneId.systemDefault(),
): PlanDeHoy {
    val state = contenedor.domainState.snapshot()
    val ajustes = contenedor.settings.settings.first()
    val hoy = LogicalDays.logicalDayOf(ahora, ajustes.dayCutoffMinutes, zona)
    val memoria = contenedor.memoria.observe(flowOf(hoy)).first()
    val permisos = contenedor.permisosAvisos()
    val ctx =
        Contextos.de(
            ahoraMillis = ahora,
            zona = zona,
            corteDia = ajustes.dayCutoffMinutes,
            horaManana = ajustes.habiMorningMinutes,
            horaNoche = ajustes.habiNightMinutes,
            dndActivo = ajustes.dndEnabled,
            dndDesde = ajustes.dndFromMinutes,
            dndHasta = ajustes.dndToMinutes,
            voz = ajustes.personality,
            nombre = ajustes.userName,
            permisoAvisos = permisos.notificaciones,
            alarmasExactas = permisos.alarmasExactas,
            diaSellado = Sealing.isSealed(state, hoy),
            quedanExigibles = quedanExigibles(state, hoy),
        )
    val recordatorios = contenedor.habits.observeHabits().first().mapNotNull { h -> h.reminderMinutes?.let { h.id to it } }.toMap()
    return PlanDeHoy(HechosCache().hechos(state, memoria, ctx, recordatorios), memoria, ctx)
}

private fun quedanExigibles(
    state: DomainState,
    hoy: Int,
): Boolean =
    state.habits.any { h ->
        h.status == HabitStatus.ACTIVE &&
            Compliance.isRequirableOn(state, h, hoy) &&
            Compliance.complianceOf(state, h, LogicalDays.periodKeyOf(hoy, h.period), hoy) == ComplianceStatus.PENDING
    }
