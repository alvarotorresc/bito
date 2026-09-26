package com.alvarotc.bito.ui.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.ClockTimes
import com.alvarotc.bito.domain.model.HabitStatus
import java.time.ZoneId

/** What a scheduled reminder slot is for. TASKS y BREATHING al final: el nombre viaja en el Intent. */
enum class SlotKind { GLOBAL, HABIT, REVIEW, TASKS, BREATHING }

/**
 * A single reminder to fire at [minutesOfDay]. [key] disambiguates slots of the same [kind]:
 * the configured minute for GLOBAL, the habit id for HABIT, empty for the one REVIEW slot.
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
     * hour, one HABIT slot per `ACTIVE` habit with a reminder set, always one REVIEW slot, and
     * one TASKS slot at noon while [Settings.taskNoticesEnabled] is on, and one BREATHING slot at
     * [Settings.breathingReminderTimeMinutes] while [Settings.breathingReminderEnabled] is on.
     */
    fun slotsOf(
        settings: Settings,
        habits: List<HabitEntity>,
    ): List<Slot> {
        val global = settings.globalReminderMinutes.map { minutes -> Slot(SlotKind.GLOBAL, minutes.toString(), minutes) }
        val habit =
            habits
                .filter { it.status == HabitStatus.ACTIVE && it.reminderMinutes != null }
                .map { Slot(SlotKind.HABIT, it.id, it.reminderMinutes!!) }
        val review = Slot(SlotKind.REVIEW, "", settings.reviewTimeMinutes)
        val tasks =
            if (settings.taskNoticesEnabled) listOf(Slot(SlotKind.TASKS, "", TASKS_NOTICE_MINUTES)) else emptyList()
        val breathing =
            if (settings.breathingReminderEnabled) {
                listOf(Slot(SlotKind.BREATHING, "", settings.breathingReminderTimeMinutes))
            } else {
                emptyList()
            }
        return global + habit + listOf(review) + tasks + breathing
    }

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
}
