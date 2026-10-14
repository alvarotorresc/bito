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
 * El modelo del cierre del día. Esqueleto de la Ola 0: cada vez que el cierre se abre monta un repaso nuevo (el de
 * siempre), como cuando era una ruta: ni las filas descartadas ni un estado viejo pasan de una apertura a otra.
 * Solo [factory] está congelado; el constructor y su estado son del carril del cierre (Tarea 26).
 */
class CierreViewModel internal constructor(
    private val crearRepaso: () -> ReviewViewModel,
) : ViewModel() {
    /** El repaso de la apertura en curso, o null con el cierre cerrado. */
    internal var repasoActual: ReviewViewModel? = null
        private set

    init {
        addCloseable { repasoActual?.viewModelScope?.cancel() }
    }

    /** Un repaso nuevo para esta apertura; el anterior, si lo hubiera, deja de escuchar. */
    internal fun abrirRepaso(): ReviewViewModel {
        repasoActual?.viewModelScope?.cancel()
        return crearRepaso().also { repasoActual = it }
    }

    /** Al cerrar: el repaso de esa apertura deja de escuchar, como la entrada de la ruta al salir. */
    internal fun soltarRepaso(repaso: ReviewViewModel) {
        repaso.viewModelScope.cancel()
        if (repasoActual === repaso) repasoActual = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CierreViewModel {
                        ReviewViewModel(
                            container.domainState,
                            container.habits,
                            container.journal,
                            container.settings,
                            container.reconciler,
                            container.rewards,
                            container.habiSounds,
                        )
                    }
                }
            }
    }
}
