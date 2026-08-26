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

/**
 * Backs the single-habit widget's configure activity: the ACTIVE habits on
 * offer and which ONE of them is chosen. Unlike [WidgetConfigViewModel]'s
 * checklist, an empty choice is not a valid save — the activity keeps its
 * save button disabled until [selected] is non-null.
 */
class SingleHabitConfigViewModel(habits: HabitsRepository) : ViewModel() {
    val habits: StateFlow<List<HabitEntity>> =
        habits
            .observeHabits()
            .map { entities -> entities.filter { it.status == HabitStatus.ACTIVE } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> get() = _selected

    /** Preloads the choice from the widget's stored state (reconfigure flow). */
    fun setInitial(id: String?) {
        _selected.value = id
    }

    fun select(id: String) {
        _selected.value = id
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SingleHabitConfigViewModel(container.habits) }
            }
    }
}
