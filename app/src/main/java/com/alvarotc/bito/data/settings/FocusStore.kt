package com.alvarotc.bito.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * Focus session state: when the session ends (on two clocks) and when the device booted, to
 * distinguish between "time changed" and "device restarted". Lives in its own DataStore file
 * (`focus.preferences_pb`) to keep WidgetRefresher from repainting on every timer tick.
 */
data class FocusSession(
    val taskId: String,
    val startedAtMillis: Long,
    val endsAtMillis: Long,
    val endsAtElapsed: Long,
    val bootMillis: Long,
)

/**
 * La cuenta atras es aritmetica sobre un fin guardado, no un contador que alguien baje: cualquiera
 * que sepa la hora puede decir cuanto queda. Dos relojes porque ninguno solo vale —
 * elapsedRealtime se va a cero al reiniciar y currentTimeMillis salta si se cambia la hora.
 */
object FocusClock {
    /** Margen de la firma de arranque: el par de relojes no se lee en el mismo instante. */
    const val BOOT_TOLERANCE_MILLIS = 5_000L

    fun bootSignatureOf(
        nowMillis: Long,
        elapsedMillis: Long,
    ): Long = nowMillis - elapsedMillis

    fun remainingMillis(
        session: FocusSession,
        nowMillis: Long,
        elapsedMillis: Long,
    ): Long {
        val sameBoot = abs(bootSignatureOf(nowMillis, elapsedMillis) - session.bootMillis) <= BOOT_TOLERANCE_MILLIS
        val remaining = if (sameBoot) session.endsAtElapsed - elapsedMillis else session.endsAtMillis - nowMillis
        return remaining.coerceAtLeast(0L)
    }
}

class FocusStore(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val taskId = stringPreferencesKey("task_id")
        val startedAtMillis = longPreferencesKey("started_at_millis")
        val endsAtMillis = longPreferencesKey("ends_at_millis")
        val endsAtElapsed = longPreferencesKey("ends_at_elapsed")
        val bootMillis = longPreferencesKey("boot_millis")
    }

    val session: Flow<FocusSession?> = dataStore.data.map { it.toSession() }

    suspend fun start(session: FocusSession) {
        dataStore.edit { prefs ->
            prefs[Keys.taskId] = session.taskId
            prefs[Keys.startedAtMillis] = session.startedAtMillis
            prefs[Keys.endsAtMillis] = session.endsAtMillis
            prefs[Keys.endsAtElapsed] = session.endsAtElapsed
            prefs[Keys.bootMillis] = session.bootMillis
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun Preferences.toSession(): FocusSession? {
        val taskId = this[Keys.taskId] ?: return null
        return FocusSession(
            taskId = taskId,
            startedAtMillis = this[Keys.startedAtMillis] ?: 0L,
            endsAtMillis = this[Keys.endsAtMillis] ?: 0L,
            endsAtElapsed = this[Keys.endsAtElapsed] ?: 0L,
            bootMillis = this[Keys.bootMillis] ?: 0L,
        )
    }
}
