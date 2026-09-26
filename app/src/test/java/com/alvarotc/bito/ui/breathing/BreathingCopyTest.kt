package com.alvarotc.bito.ui.breathing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El copy de M11 resuelve en los dos idiomas con sus placeholders (spec §10). values/ es ingles y
 * values-es/ espanol; una clave que faltara en values-es/ compilaria igual (el id sale de values/)
 * pero el test en espanol fallaria en tiempo de ejecucion al no encontrar el recurso para ese locale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BreathingCopyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun sessions(count: Int) = context.resources.getQuantityString(R.plurals.breathing_sessions, count, count)

    @Test
    fun `english copy resolves with its placeholders`() {
        assertEquals("Breathe", context.getString(R.string.breathing_title))
        assertEquals("Cycle 3 of 6", context.getString(R.string.breathing_cycle_of, 3, 6))
        assertEquals("1 session", sessions(1))
        assertEquals("This week: 2 sessions · 4 min", context.getString(R.string.breathing_week, sessions(2), 4))
        assertEquals("Since the start: 1 session · 1 min", context.getString(R.string.breathing_all_time, sessions(1), 1))
        assertEquals("There, Álvaro.", context.getString(R.string.habi_breathing_done_neutra, "Álvaro"))
        assertEquals("Time to breathe, Álvaro.", context.getString(R.string.notif_breathing_title_neutra, "Álvaro"))
        assertEquals("A soft buzz when you log and when you breathe", context.getString(R.string.settings_log_haptic_hint))
    }

    @Test
    @Config(qualifiers = "es")
    fun `spanish copy resolves with its placeholders`() {
        assertEquals("Respirar", context.getString(R.string.breathing_title))
        assertEquals("Ciclo 3 de 6", context.getString(R.string.breathing_cycle_of, 3, 6))
        assertEquals("1 sesión", sessions(1))
        assertEquals("Esta semana: 2 sesiones · 4 min", context.getString(R.string.breathing_week, sessions(2), 4))
        assertEquals("Desde el principio: 1 sesión · 1 min", context.getString(R.string.breathing_all_time, sessions(1), 1))
        assertEquals("Ya está, Álvaro.", context.getString(R.string.habi_breathing_done_neutra, "Álvaro"))
        assertEquals("Hora de respirar, Álvaro.", context.getString(R.string.notif_breathing_title_neutra, "Álvaro"))
        assertEquals("Una vibración suave al registrar y al respirar", context.getString(R.string.settings_log_haptic_hint))
    }
}
