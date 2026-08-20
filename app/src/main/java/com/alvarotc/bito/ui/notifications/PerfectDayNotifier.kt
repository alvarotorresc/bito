package com.alvarotc.bito.ui.notifications

import android.content.Context
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.ui.AppVisibility
import com.alvarotc.bito.ui.celebration.CelebrationGate
import com.alvarotc.bito.ui.habi.HabiVoice
import kotlinx.coroutines.flow.first

/**
 * Posts the voiced perfect-day notification (T13) after an out-of-app write. The call-site
 * contract: hand it whatever [reachedNow] the write just reported ([QuickActionResult] or
 * [com.alvarotc.bito.ui.widget.WidgetLogger]'s `Boolean`) and it decides — via [CelebrationGate]
 * — whether that's actually worth announcing right now.
 */
object PerfectDayNotifier {
    /** Decides with [CelebrationGate] and posts the voiced body when it says yes. */
    suspend fun maybeNotify(
        context: Context,
        container: AppContainer,
        reachedNow: Boolean,
    ) {
        val prefs = container.settings.settings.first()
        if (!CelebrationGate.shouldNotifyPerfectDay(reachedNow, prefs.perfectDayCelebration, AppVisibility.visible)) return
        Notifier.showPerfectDay(context, bodyFor(context, prefs))
    }

    /** Testable seam: the text the notification carries for a given settings snapshot. */
    internal fun bodyFor(
        context: Context,
        prefs: Settings,
    ): String {
        val name = prefs.userName.ifBlank { context.getString(R.string.habi_name_fallback) }
        return context.getString(HabiVoice.perfectDayNotifRes(prefs.personality), name)
    }
}
