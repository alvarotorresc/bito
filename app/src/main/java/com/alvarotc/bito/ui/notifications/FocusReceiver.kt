package com.alvarotc.bito.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alvarotc.bito.BitoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fin de una sesion de foco. Si ya no hay sesion viva, solo limpia la bandeja: no inventa nada. */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching {
                    val container = (context.applicationContext as BitoApp).container
                    val session = container.focus.session.first()
                    if (session == null) {
                        Notifier.cancelFocus(context)
                    } else {
                        NotificationChannels.ensure(context)
                        val title = container.tasks.task(session.taskId)?.title
                        if (title == null) {
                            container.focus.clear()
                            Notifier.cancelFocus(context)
                        } else {
                            Notifier.showFocusOver(context, title)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
