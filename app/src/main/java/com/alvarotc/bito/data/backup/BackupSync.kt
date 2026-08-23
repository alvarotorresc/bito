package com.alvarotc.bito.data.backup

import android.app.Application
import com.alvarotc.bito.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Mirrors [com.alvarotc.bito.ui.notifications.ReminderSync]: reprograms the periodic auto-backup
 * work only when the derived inputs (folder, frequency) actually change.
 */
object BackupSync {
    fun start(
        app: Application,
        container: AppContainer,
        scope: CoroutineScope,
    ) {
        scope.launch {
            container.settings.settings
                .map { Pair(it.backupFolderUri, it.backupFrequency) }
                .distinctUntilChanged()
                .collect {
                    // A bad emission here must not cancel this collector — same reasoning as
                    // ReminderSync/WidgetRefresher: one failure shouldn't stop syncing forever.
                    runCatching { BackupScheduler.sync(app, container.settings.settings.first()) }
                }
        }
    }
}
