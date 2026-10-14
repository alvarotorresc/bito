package com.alvarotc.bito.ui.notifications

import android.content.Context
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.ComplianceStatus
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Sealing
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Contextos
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.HechosCache
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.HabitStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.ZoneId

/** Lo que hace falta saber de hoy para planificar los avisos. Los tests del carril de avisos lo sustituyen. */
data class PlanDeHoy(val hechos: Hechos, val memoria: Memoria, val ctx: Contexto)

/**
 * Los avisos de Habi sobre Android. Esqueleto: los dos métodos no hacen nada hasta que el carril de avisos
 * programe y cancele las alarmas. [plan] lee estado, memoria y ajustes del contenedor (ruling P8).
 */
@Suppress("UnusedPrivateProperty")
class AvisosHabiAndroid(
    private val context: Context,
    private val contenedor: () -> AppContainer,
    private val plan: suspend () -> PlanDeHoy = { planDeHoyDe(contenedor()) },
) : AvisosHabi {
    override suspend fun replanificar() = Unit

    override suspend fun cancelarAceptados(ids: List<String>) = Unit
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
