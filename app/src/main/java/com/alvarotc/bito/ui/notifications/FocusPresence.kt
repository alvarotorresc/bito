package com.alvarotc.bito.ui.notifications

import android.content.Context

/** Lo unico que la sesion necesita de la plataforma: la alarma y la bandeja. */
interface FocusPresence {
    fun show(
        taskTitle: String,
        endsAtMillis: Long,
    )

    fun clear()
}

/** Implementacion real: la alarma exacta de fin y la notificacion permanente, las dos de la Tarea 17. */
class AndroidFocusPresence(private val context: Context) : FocusPresence {
    override fun show(
        taskTitle: String,
        endsAtMillis: Long,
    ) {
        NotificationChannels.ensure(context)
        Notifier.showFocus(context, taskTitle, endsAtMillis)
        FocusAlarm.schedule(context, endsAtMillis)
    }

    override fun clear() {
        Notifier.cancelFocus(context)
        FocusAlarm.cancel(context)
    }
}
