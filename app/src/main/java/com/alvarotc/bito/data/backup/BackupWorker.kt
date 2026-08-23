package com.alvarotc.bito.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alvarotc.bito.BitoApp

/** Thin WorkManager shell; all the policy lives in [AutoBackupRunner]. */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BitoApp).container
        val resolver = applicationContext.contentResolver
        val runner =
            AutoBackupRunner(
                settings = container.settings,
                backup = container.backup,
                sink = SafBackupWriter(applicationContext),
                hasPersistedPermission = { uri ->
                    resolver.persistedUriPermissions.any { it.uri.toString() == uri && it.isWritePermission }
                },
            )
        return when (runner.run()) {
            AutoBackupRunner.Outcome.DONE, AutoBackupRunner.Outcome.SKIPPED -> Result.success()
            AutoBackupRunner.Outcome.RETRY -> if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "auto-backup"
        private const val ONE_SHOT_WORK_NAME = "auto-backup-now"

        /** T8's "back up now" row. A distinct unique name keeps this from colliding with the
         * periodic work [BackupScheduler] owns. */
        fun oneShot(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    ONE_SHOT_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<BackupWorker>().build(),
                )
        }
    }
}
