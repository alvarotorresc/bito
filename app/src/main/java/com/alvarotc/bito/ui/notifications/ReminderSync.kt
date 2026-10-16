package com.alvarotc.bito.ui.notifications

import android.content.Context
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.domain.habla.PlanAvisos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Keeps scheduled alarms in sync with live settings and habits (tech doc §6.3). A change from
 * Settings or the habit form lands here through [AppContainer.settings]/[AppContainer.habits]
 * without that code ever knowing about alarms — this collector reprograms only when the settings or
 * the derived [ReminderScheduler.slotsOf] actually changed, Habi's notices included.
 */
object ReminderSync {
    fun start(
        context: Context,
        container: AppContainer,
        scope: CoroutineScope,
    ) {
        scope.launch {
            // Presence by default: a clean install has no reminder hours, so without this the
            // GLOBAL reminder never fires until someone visits Ajustes. Seeded before the
            // collector starts so its very first slots emission already schedules them; the
            // seeder itself is one-shot and restore-safe (see SettingsRepository). A failed
            // seed (DataStore IO) must not kill the sync loop below — same guard as the
            // collect body; cancellation keeps propagating.
            runCatching { container.settings.seedDefaultReminders() }
                .onFailure { if (it is CancellationException) throw it }
            // Los ajustes enteros entran en la comparación: la hora de la noche o el silencio cambian los avisos de
            // Habi aunque los slots de siempre sigan iguales.
            combine(container.settings.settings, container.habits.observeHabits()) { prefs, entities ->
                prefs to ReminderScheduler.slotsOf(prefs, entities)
            }
                .distinctUntilChanged()
                .collect { (prefs, _) ->
                    // A TOCTOU exact-alarm revocation surfaces here as a SecurityException; letting it
                    // escape would cancel this collector and stop syncing reminders for the rest of the
                    // process's life, so one bad emission is swallowed instead of killing the loop.
                    runCatching {
                        val entities = container.habits.observeHabits().first()
                        val plan = planDeHoyDe(container).let { PlanAvisos.de(it.hechos, it.memoria, it.ctx) }
                        ReminderScheduler.scheduleAll(
                            context,
                            ReminderScheduler.slotsOf(prefs, entities, plan),
                            System.currentTimeMillis(),
                            ZoneId.systemDefault(),
                        )
                        // Lo que hoy no sale (mañana, noche, aceptados) lo quita o lo pasa a mañana.
                        container.avisosHabi.replanificar()
                    }.onFailure { if (it is CancellationException) throw it }
                }
        }
    }
}
