package com.alvarotc.bito.ui.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.domain.model.HabitStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Backs the widget configure activity: which ACTIVE habits this widget
 * instance offers and which of them are checked (spec §3.3 — empty
 * selection means "show every pending habit", enforced by the activity's
 * save flow, not here).
 */
class WidgetConfigViewModel(habits: HabitsRepository) : ViewModel() {
    val habits: StateFlow<List<HabitEntity>> =
        habits
            .observeHabits()
            .map { entities -> entities.filter { it.status == HabitStatus.ACTIVE } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> get() = _selected

    /** Preloads the checklist from the widget's stored state (reconfigure flow). */
    fun setInitial(ids: Set<String>) {
        _selected.value = ids
    }

    fun toggle(id: String) {
        _selected.update { current -> if (id in current) current - id else current + id }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { WidgetConfigViewModel(container.habits) }
            }
    }
}
