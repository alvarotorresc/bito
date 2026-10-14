package com.alvarotc.bito.ui.cierre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.ui.review.ReviewViewModel
import kotlinx.coroutines.cancel

/**
 * El modelo del cierre del día. Esqueleto de la Ola 0: envuelve el repaso de siempre, que el cierre monta tal cual.
 * Solo [factory] está congelado; el constructor y su estado son del carril del cierre (Tarea 26).
 */
class CierreViewModel internal constructor(
    internal val repaso: ReviewViewModel,
) : ViewModel() {
    init {
        // El repaso no cuelga de ningún almacén: su ámbito muere con el cierre.
        addCloseable { repaso.viewModelScope.cancel() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CierreViewModel(
                        ReviewViewModel(
                            container.domainState,
                            container.habits,
                            container.journal,
                            container.settings,
                            container.reconciler,
                            container.rewards,
                            container.habiSounds,
                        ),
                    )
                }
            }
    }
}
