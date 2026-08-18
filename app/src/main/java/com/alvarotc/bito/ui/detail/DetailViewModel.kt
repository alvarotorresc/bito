package com.alvarotc.bito.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.db.FreezerUseEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Backs the habit Detail screen: derives its state and runs every registration/lifecycle action. */
class DetailViewModel(
    private val habitId: String,
    private val domainState: DomainStateRepository,
    private val habits: HabitsRepository,
    private val journal: JournalRepository,
    private val rewards: RewardsRepository,
    private val settings: SettingsRepository,
    private val reconciler: PointsReconciler,
    private val economy: EconomyConfig = EconomyConfig(),
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    // Seeded with a cutoff-agnostic "today" — only used as the starting point for month
    // navigation before the first real settings emission; a few hours' drift around a cutoff
    // can, at worst, open the screen one calendar month early, which the user can page back from.
    private val _month =
        MutableStateFlow(YearMonth.from(LocalDate.ofEpochDay(LogicalDays.logicalDayOf(now(), 0, zone()).toLong())))
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val uiState: StateFlow<DetailUiState?> =
        combine(domainState.observe(), settings.settings, _month) { state, prefs, m ->
            buildDetailUiState(state, habitId, m, todayOf(prefs))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        write { _, _ -> } // opening the screen reconciles pending grants
    }

    private fun todayOf(prefs: Settings) = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())

    /** Every mutation recomputes grants right after, always against the CURRENT logical today — even when [day] targets the past. */
    private fun write(block: suspend (today: LogicalDay, nowMillis: Long) -> Unit) =
        viewModelScope.launch {
            val today = todayOf(settings.settings.first())
            val nowMillis = now()
            block(today, nowMillis)
            reconciler.reconcile(today, nowMillis)
        }

    fun previousMonth() {
        _month.update { it.minusMonths(1) }
    }

    /** Never advances past the calendar month containing the current logical today. */
    fun nextMonth() =
        viewModelScope.launch {
            val currentMonth = YearMonth.from(LocalDate.ofEpochDay(todayOf(settings.settings.first()).toLong()))
            _month.update { if (it < currentMonth) it.plusMonths(1) else it }
        }

    fun setDayValue(
        day: LogicalDay,
        value: Int,
    ) = write { _, nowMillis -> journal.setDayTotal(habitId, day, value, nowMillis) }

    fun markDayDone(day: LogicalDay) = write { _, nowMillis -> journal.setDayTotal(habitId, day, 1, nowMillis) }

    fun clearDay(day: LogicalDay) = write { _, nowMillis -> journal.setDayTotal(habitId, day, 0, nowMillis) }

    fun logRelapseOn(day: LogicalDay) =
        write { _, nowMillis -> journal.log(EntryEntity(UUID.randomUUID().toString(), habitId, day, 1, nowMillis)) }

    /** Batch-seal's single-day twin: clears the day's entries, then seals it — same as "todo limpio". */
    fun markCleanAndSeal(day: LogicalDay) =
        write { _, nowMillis ->
            journal.setDayTotal(habitId, day, 0, nowMillis)
            journal.sealDay(day, nowMillis)
        }

    fun buyFreezer() =
        write { today, nowMillis ->
            val ledger = domainState.snapshot().pointsLedger
            if (PointsEngine.canSpend(ledger, economy.freezerPrice)) {
                rewards.spend(-economy.freezerPrice, PointsReason.BUY_FREEZER, UUID.randomUUID().toString(), today, nowMillis)
            }
        }

    fun applyFreezer(day: LogicalDay) =
        write { _, nowMillis -> journal.useFreezer(FreezerUseEntity(UUID.randomUUID().toString(), habitId, day, nowMillis)) }

    fun pause(note: String?) = write { today, _ -> habits.pause(habitId, today, note) }

    fun resume() = write { today, _ -> habits.resume(habitId, today) }

    fun archive() = write { today, nowMillis -> habits.archive(habitId, today, nowMillis) }

    // A mutation like any other habit lifecycle write: goes through `write` so the reconcile
    // call runs afterward, consistent with pause/resume/archive above.
    fun unarchive() = write { _, _ -> habits.unarchive(habitId) }

    companion object {
        fun factory(
            container: AppContainer,
            habitId: String,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DetailViewModel(
                        habitId,
                        container.domainState,
                        container.habits,
                        container.journal,
                        container.rewards,
                        container.settings,
                        container.reconciler,
                    )
                }
            }
    }
}
