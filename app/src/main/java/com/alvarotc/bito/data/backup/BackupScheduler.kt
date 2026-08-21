package com.alvarotc.bito.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.data.settings.Settings
import java.util.concurrent.TimeUnit

/** Keeps the periodic auto-backup work in sync with [Settings] (driven by [BackupSync]). */
object BackupScheduler {
    fun sync(
        context: Context,
        prefs: Settings,
    ) {
        val workManager = WorkManager.getInstance(context)
        val folderUri = prefs.backupFolderUri
        if (folderUri == null) {
            // No folder means no auto backup; nothing to run against.
            workManager.cancelUniqueWork(BackupWorker.WORK_NAME)
            return
        }

        val periodDays = if (prefs.backupFrequency == BackupFrequency.DAILY) 1L else 7L
        val request =
            PeriodicWorkRequestBuilder<BackupWorker>(periodDays, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
        workManager.enqueueUniquePeriodicWork(BackupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
