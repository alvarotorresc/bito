package com.alvarotc.bito.ui.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.ClockTimes
import com.alvarotc.bito.domain.habla.AvisoPlan
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Contextos
import com.alvarotc.bito.domain.habla.PlanAvisos
import com.alvarotc.bito.domain.habla.TipoAviso
import com.alvarotc.bito.domain.model.HabitStatus
import java.time.ZoneId
import java.time.ZoneOffset

/** What a scheduled reminder slot is for. El nombre viaja en el Intent; los tres de Habi salen del plan de avisos. */
enum class SlotKind { GLOBAL, HABIT, HABI_NOCHE, TASKS, BREATHING, HABI_MANANA, ACEPTADO }

/**
 * A single reminder to fire at [minutesOfDay]. [key] disambiguates slots of the same [kind]:
 * the configured minute for GLOBAL, the habit id for HABIT, the plan's key for the Habi slots.
 */
data class Slot(val kind: SlotKind, val key: String, val minutesOfDay: Int)

/**
 * Computes the reminder slots (pure, tech doc §6.3) and schedules them as exact — or best-effort,
 * gracefully degraded — [AlarmManager] alarms. Orphan slots (a deleted hour, an archived habit)
 * self-heal: nothing re-derives them from [slotsOf] on the next [scheduleAll], and the receiver
 * revalidates against the current [slotsOf] before notifying, so a stale fired alarm is a no-op.
 */
object ReminderScheduler {
    const val EXTRA_KIND = "kind"
    const val EXTRA_KEY = "key"

    /** Los avisos de tareas son a mediodia y no se configuran (D8): un solo interruptor. */
    const val TASKS_NOTICE_MINUTES = 12 * 60

    /** The stable [PendingIntent] request code for [slot] — unique per kind+key, deterministic. */
    fun requestCodeOf(slot: Slot): Int = "${slot.kind}:${slot.key}".hashCode()

    /**
     * The reminder slots implied by current settings and habits: one GLOBAL slot per configured
     * hour, one HABIT slot per `ACTIVE` habit with a reminder set, one TASKS slot at noon while
     * [Settings.taskNoticesEnabled] is on, and one BREATHING slot at
     * [Settings.breathingReminderTimeMinutes] while [Settings.breathingReminderEnabled] is on.
     * Con el silencio de Bito activo, estos se mueven al final del silencio o se pierden
     * ([PlanAvisos.ajustarConservado]); la clave no cambia, solo el minuto. Los avisos de Habi
     * (`HABI_MANANA`, `HABI_NOCHE`, `ACEPTADO`) salen solo de [plan].
     */
    fun slotsOf(
        settings: Settings,
        habits: List<HabitEntity>,
        plan: List<AvisoPlan> = emptyList(),
    ): List<Slot> {
        val global = settings.globalReminderMinutes.map { minutes -> Slot(SlotKind.GLOBAL, minutes.toString(), minutes) }
        val habit =
            habits
                .filter { it.status == HabitStatus.ACTIVE && it.reminderMinutes != null }
                .map { Slot(SlotKind.HABIT, it.id, it.reminderMinutes!!) }
        val tasks =
            if (settings.taskNoticesEnabled) listOf(Slot(SlotKind.TASKS, "", TASKS_NOTICE_MINUTES)) else emptyList()
        val breathing =
            if (settings.breathingReminderEnabled) {
                listOf(Slot(SlotKind.BREATHING, "", settings.breathingReminderTimeMinutes))
            } else {
                emptyList()
            }
        return conSilencio(global + habit + tasks + breathing, settings) + plan.map(::slotDe)
    }

    /** El slot de un aviso de Habi planificado: la clave es la del plan. */
    fun slotDe(aviso: AvisoPlan): Slot = Slot(kindDe(aviso.tipo), aviso.clave, aviso.minuto)

    fun kindDe(tipo: TipoAviso): SlotKind =
        when (tipo) {
            TipoAviso.MANANA -> SlotKind.HABI_MANANA
            TipoAviso.NOCHE -> SlotKind.HABI_NOCHE
            TipoAviso.ACEPTADO -> SlotKind.ACEPTADO
        }

    private fun conSilencio(
        slots: List<Slot>,
        settings: Settings,
    ): List<Slot> {
        if (!settings.dndEnabled) return slots
        val ctx = contextoDeAjustes(settings)
        return slots.mapNotNull { slot -> PlanAvisos.ajustarConservado(slot.minutesOfDay, ctx)?.let { slot.copy(minutesOfDay = it) } }
    }

    /** Solo el silencio y el corte del día cuentan para [PlanAvisos.ajustarConservado]; el resto es relleno. */
    private fun contextoDeAjustes(settings: Settings): Contexto =
        Contextos.de(
            ahoraMillis = 0L,
            zona = ZoneOffset.UTC,
            corteDia = settings.dayCutoffMinutes,
            horaManana = settings.habiMorningMinutes,
            horaNoche = settings.habiNightMinutes,
            dndActivo = settings.dndEnabled,
            dndDesde = settings.dndFromMinutes,
            dndHasta = settings.dndToMinutes,
            voz = settings.personality,
            nombre = null,
            permisoAvisos = true,
            alarmasExactas = true,
            diaSellado = false,
            quedanExigibles = false,
        )

    fun scheduleAll(
        context: Context,
        slots: List<Slot>,
        nowMillis: Long,
        zone: ZoneId,
    ) {
        // One bad slot (e.g. a TOCTOU SecurityException from canScheduleExactAlarms) must not
        // stop the remaining slots from being scheduled.
        slots.forEach { runCatching { scheduleSlot(context, it, nowMillis, zone) } }
    }

    /** Schedules a single [slot], exact when the OS allows it, degraded otherwise (tech doc §6.3). */
    fun scheduleSlot(
        context: Context,
        slot: Slot,
        nowMillis: Long,
        zone: ZoneId,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fireAtMillis = ClockTimes.nextOccurrence(slot.minutesOfDay, nowMillis, zone)
        val intent =
            Intent(context, ReminderReceiver::class.java)
                .putExtra(EXTRA_KIND, slot.kind.name)
                .putExtra(EXTRA_KEY, slot.key)
        val requestCode = requestCodeOf(slot)
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pendingIntent)
        }
    }

    /** Quita la alarma de [slot], si la hay: el mismo receptor y el mismo código de petición que al programarla. */
    fun cancelar(
        context: Context,
        slot: Slot,
    ) {
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCodeOf(slot),
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
