package com.alvarotc.bito.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.LogicalDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

/** Backs the Today screen: derives its state and runs every registration action. */
class TodayViewModel(
    domainState: DomainStateRepository,
    habits: HabitsRepository,
    private val journal: JournalRepository,
    private val settings: SettingsRepository,
    private val reconciler: PointsReconciler,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    val uiState: StateFlow<TodayUiState> =
        combine(domainState.observe(), habits.observeHabits(), settings.settings) { state, entities, prefs ->
            buildTodayUiState(state, entities.associate { it.id to it.sortOrder }, todayOf(prefs))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private val loggedEntry = MutableStateFlow<String?>(null)
    val lastLogged: StateFlow<String?> = loggedEntry.asStateFlow()

    init {
        write { _, _ -> } // opening the app reconciles pending grants
    }

    private fun todayOf(prefs: Settings) = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())

    /** Every mutation recomputes grants right after (idempotent append). */
    private fun write(block: suspend (today: LogicalDay, nowMillis: Long) -> Unit) =
        viewModelScope.launch {
            val today = todayOf(settings.settings.first())
            val nowMillis = now()
            block(today, nowMillis)
            reconciler.reconcile(today, nowMillis)
        }

    private fun log(
        habitId: String,
        value: Int,
    ) = write { today, nowMillis ->
        val id = UUID.randomUUID().toString()
        journal.log(EntryEntity(id, habitId, today, value, nowMillis))
        loggedEntry.value = id
    }

    fun tapPrimary(card: HabitCardUi) {
        when (card.kind) {
            CardKind.CHECK ->
                if (card.doneToday) {
                    write { today, nowMillis ->
                        journal.setDayTotal(card.id, today, 0, nowMillis)
                        loggedEntry.value = null
                    }
                } else {
                    log(card.id, 1)
                }
            CardKind.COUNTER -> log(card.id, card.step)
            CardKind.DURATION, CardKind.ABSTINENCE -> Unit
        }
    }

    fun addAmount(
        card: HabitCardUi,
        amount: Int,
    ) = log(card.id, amount)

    fun setExactToday(
        card: HabitCardUi,
        value: Int,
    ) = write { today, nowMillis ->
        journal.setDayTotal(card.id, today, value, nowMillis)
        loggedEntry.value = null
    }

    fun logRelapse(card: HabitCardUi) = log(card.id, 1)

    fun undo() {
        val id = loggedEntry.value ?: return
        loggedEntry.value = null
        write { _, _ -> journal.remove(id) }
    }

    fun consumeLogged() {
        loggedEntry.value = null
    }

    fun sealPendingDays() = write { _, nowMillis -> uiState.value.pendingSealDays.forEach { journal.sealDay(it, nowMillis) } }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TodayViewModel(container.domainState, container.habits, container.journal, container.settings, container.reconciler)
                }
            }
    }
}
