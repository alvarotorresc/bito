package com.alvarotc.bito.data.habla

import com.alvarotc.bito.R
import com.alvarotc.bito.data.propuestas.PropuestasProvisionales
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.ComplianceStatus
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.Sealing
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.CatalogoHabla
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Contextos
import com.alvarotc.bito.domain.habla.ElegirCandidata
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.HechosCache
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Motor
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.idPropuesta
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.notifications.AvisosHabi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.ZoneId

/** Lo que el sistema deja hacer a los avisos de Habi: notificaciones y alarmas exactas. */
data class PermisosAvisos(
    val notificaciones: Boolean,
    val alarmasExactas: Boolean,
)

/**
 * La fuente de lo que dice Habi (spec §4.2). Esqueleto de la Ola 0: en Hoy elige con [motor] y pinta el saludo de
 * siempre; en la ficha y en Camino dice lo mismo que [PropuestasProvisionales]; nada en el cierre ni en el descanso.
 * No escribe memoria, no guarda vigente y responder no tiene efecto. Solo [guardarGordo] escribe ya. El carril m2
 * (Tarea 20) rellena el cuerpo sin cambiar el constructor.
 */
class FuentePropuestasHabi(
    private val estado: Flow<DomainState>,
    private val ajustes: Flow<Settings>,
    private val recordatorios: Flow<Map<String, Int>>,
    private val memoria: MemoriaRepository,
    private val vigentes: PropuestaVigenteStore,
    private val gordo: LoGordoRepository,
    private val aceptados: AvisosAceptadosRepository,
    private val catalogo: () -> CatalogoHabla,
    private val avisos: AvisosHabi,
    private val permisos: () -> PermisosAvisos,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // «Hecho» de la vuelta; AppContainer lo cablea.
    private val registrar: suspend (habitId: String, cantidad: Int) -> Unit = { _, _ -> },
    // Los tests de m2 inyectan un falso.
    private val motor: ElegirCandidata = ElegirCandidata(Motor::elegir),
    // Ruling P24: los tests de m2 inyectan unos hechos falsos.
    private val hechos: (DomainState, Memoria, Contexto, Map<String, Int>) -> Hechos = HechosCache()::hechos,
) : FuentePropuestas {
    private val provisional = PropuestasProvisionales(estado, ajustes, now, zone)

    override fun propuesta(
        ambito: Ambito,
        habitId: String?,
    ): Flow<Propuesta?> =
        when (ambito) {
            Ambito.HOY -> hoy()
            Ambito.FICHA, Ambito.CAMINO -> provisional.propuesta(ambito, habitId)
            Ambito.CIERRE, Ambito.DESCANSO -> flowOf(null)
        }

    private fun hoy(): Flow<Propuesta?> {
        val dia = ajustes.map { diaDe(it) }.distinctUntilChanged()
        return combine(estado, ajustes, recordatorios, memoria.observe(dia)) { state, prefs, recs, m ->
            val ctx = contexto(state, prefs)
            val c = motor.elegir(Ambito.HOY, hechos(state, m, ctx, recs), m, ctx, null) ?: return@combine null
            pintar(c, state, prefs, ctx)
        }
    }

    /** Esqueleto: solo sabe pintar el saludo, con la frase de siempre según el ánimo. */
    private fun pintar(
        c: Candidata,
        state: DomainState,
        prefs: Settings,
        ctx: Contexto,
    ): Propuesta? {
        if (c.situacion != Situacion.SALUDO) return null
        val animo = MoodEngine.moodOf(state, ctx.hoy, StatsEngine.lastActivityDay(state))
        val nombre: Any = prefs.userName.ifBlank { TextoHabi(R.string.habi_name_fallback) }
        return Propuesta(
            id = idPropuesta(c.situacion, c.ref, ctx.hoy),
            texto = TextoHabi(HabiVoice.greetingRes(animo, prefs.personality), listOf(nombre)),
            acciones = emptyList(),
        )
    }

    /** El contexto de ahora (ruling P8): sellado y exigibles salen del estado, nunca de los hechos. */
    private fun contexto(
        state: DomainState,
        prefs: Settings,
    ): Contexto {
        val ahora = now()
        val hoy = diaDe(prefs, ahora)
        val p = permisos()
        return Contextos.de(
            ahoraMillis = ahora,
            zona = zone(),
            corteDia = prefs.dayCutoffMinutes,
            horaManana = prefs.habiMorningMinutes,
            horaNoche = prefs.habiNightMinutes,
            dndActivo = prefs.dndEnabled,
            dndDesde = prefs.dndFromMinutes,
            dndHasta = prefs.dndToMinutes,
            voz = prefs.personality,
            nombre = prefs.userName,
            permisoAvisos = p.notificaciones,
            alarmasExactas = p.alarmasExactas,
            diaSellado = Sealing.isSealed(state, hoy),
            quedanExigibles = quedanExigibles(state, hoy),
        )
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

    private fun diaDe(
        prefs: Settings,
        ahora: Long = now(),
    ): Int = LogicalDays.logicalDayOf(ahora, prefs.dayCutoffMinutes, zone())

    override suspend fun responder(
        id: String,
        respuesta: Respuesta,
        hora: Int?,
    ): Efecto = Efecto.Ninguno

    override suspend fun guardarGordo(refs: List<RefGordo>) {
        gordo.guardar(diaDe(ajustes.first()), refs)
    }
}
