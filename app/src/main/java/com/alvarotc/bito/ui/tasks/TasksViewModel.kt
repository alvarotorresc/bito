package com.alvarotc.bito.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.TaskEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

/** Backs the full tasks list screen: derives its state and runs every mutation. */
class TasksViewModel(
    domainState: DomainStateRepository,
    private val tasks: TasksRepository,
    private val settings: SettingsRepository,
    private val reconciler: PointsReconciler,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Overridable so tests can swap in their TestDispatcher, same reason as TodayViewModel.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<TasksUiState> =
        combine(domainState.observe(), settings.settings) { state, prefs ->
            buildTasksUiState(state, todayOf(prefs))
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    private fun todayOf(prefs: Settings) = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())

    /** Every mutation recomputes grants right after (idempotent append), like Today's. */
    private fun write(block: suspend (today: LogicalDay, nowMillis: Long) -> Unit) =
        viewModelScope.launch {
            val today = todayOf(settings.settings.first())
            val nowMillis = now()
            block(today, nowMillis)
            reconciler.reconcile(today, nowMillis)
        }

    fun bringToToday(id: String) = write { today, nowMillis -> tasks.bringToToday(id, today, nowMillis) }

    fun markDone(id: String) = write { today, nowMillis -> tasks.markDone(id, today, nowMillis) }

    fun create(
        title: String,
        firstStep: String?,
        dueKind: DueKind,
        dueDay: LogicalDay?,
    ) = write { today, nowMillis ->
        tasks.create(
            TaskEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                firstStep = firstStep,
                dueKind = dueKind,
                dueDay = resolvedDue(dueKind, dueDay, today),
                status = TaskStatus.OPEN,
                createdAtMillis = nowMillis,
                createdOnDay = today,
                doneAtMillis = null,
                doneOnDay = null,
            ),
        )
    }

    fun edit(
        id: String,
        title: String,
        firstStep: String?,
        dueKind: DueKind,
        dueDay: LogicalDay?,
    ) = write { today, _ ->
        tasks.update(id, title, firstStep, dueKind, resolvedDue(dueKind, dueDay, today))
    }

    fun delete(id: String) = write { _, _ -> tasks.delete(id) }

    /** WEEK se ancla SIEMPRE a la semana en curso: volver a decir «esta semana» es volver a comprometerse. */
    private fun resolvedDue(
        dueKind: DueKind,
        dueDay: LogicalDay?,
        today: LogicalDay,
    ): LogicalDay? =
        when (dueKind) {
            DueKind.NONE -> null
            DueKind.WEEK -> Tasks.weekDueOf(today)
            DueKind.DATE -> dueDay
        }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TasksViewModel(
                        container.domainState,
                        container.tasks,
                        container.settings,
                        container.reconciler,
                    )
                }
            }
    }
}
