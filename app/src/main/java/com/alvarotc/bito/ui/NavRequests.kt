package com.alvarotc.bito.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one bridge from an [android.content.Intent] to the NavHost (T11). [MainActivity] decodes a
 * notification tap's route extra and calls [open]; [BitoNavHost] observes [pending] and navigates
 * to [destino] of it, then [consume]s it so the request only ever fires once.
 *
 * Las peticiones son nombres, no rutas: `review` sigue siendo el nombre que manda el aviso de la noche, pero ya no
 * hay ruta del repaso y abre el cierre dentro del Inicio; `gordo` (el aviso de la mañana) abre Hoy con la hoja de
 * lo gordo; `focus` abre Hoy en modo sesión (la sesión en curso, o lo gordo si no hay); `today` vuelve al Hoy que ya está abierto.
 */
object NavRequests {
    /**
     * Routes an [android.content.Intent] extra is allowed to open. `MainActivity` is exported
     * (it's the LAUNCHER activity), so its extras are untrusted input from any app on the
     * device: without this allowlist, an arbitrary string would reach
     * `NavController.navigate(String)` in [BitoNavHost] and crash Bito with
     * `IllegalArgumentException` for any route that isn't a real destination. All of them are
     * exact strings, and none carries arguments.
     */
    private val deepLinkable = setOf("review", "tasks", "focus", "breathing", "gordo", "today")

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** No-ops for anything outside [deepLinkable] instead of forwarding it to the NavHost. */
    fun open(route: String) {
        if (route in deepLinkable) _pending.value = route
    }

    /** Adónde navega cada petición: `review` y `gordo` abren Hoy con su bandera; el resto, tal cual. */
    fun destino(peticion: String): String =
        when (peticion) {
            "review" -> "today?cierre=true"
            "gordo" -> "today?gordo=true"
            // La sesión vive en Hoy: la petición abre Hoy y el Inicio la atiende (EntradaSesion.pendiente).
            "focus" -> "today"
            else -> peticion
        }

    /** Las peticiones que acaban en Hoy y no apilan otro Hoy encima del que hay. */
    internal fun vaAHoy(peticion: String): Boolean =
        peticion == "review" || peticion == "gordo" || peticion == "today" || peticion == "focus"

    fun consume() {
        _pending.value = null
    }
}
