package com.alvarotc.bito.data.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.data.settings.Settings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val FOLDER = "content://tree/backups"

/**
 * A bare [Application] is enough here — [BackupScheduler.sync] only enqueues/cancels work,
 * it never runs [BackupWorker] (that would need the real [com.alvarotc.bito.BitoApp]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupSchedulerTest {
    private lateinit var context: Application
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    private fun workInfos() = workManager.getWorkInfosForUniqueWork(BackupWorker.WORK_NAME).get()

    @Test
    fun `no folder cancels the unique work`() {
        BackupScheduler.sync(context, Settings(backupFolderUri = FOLDER))
        BackupScheduler.sync(context, Settings(backupFolderUri = null))

        val infos = workInfos()
        assertTrue(infos.isNotEmpty())
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `a folder enqueues the unique periodic work`() {
        BackupScheduler.sync(context, Settings(backupFolderUri = FOLDER))

        val infos = workInfos()
        assertEquals(1, infos.size)
        val info = infos.single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertNotNull(info.periodicityInfo)
        assertTrue(info.constraints.requiresBatteryNotLow())
    }

    @Test
    fun `daily and weekly produce different periods`() {
        BackupScheduler.sync(context, Settings(backupFolderUri = FOLDER, backupFrequency = BackupFrequency.DAILY))
        val dailyMillis = workInfos().single().periodicityInfo?.repeatIntervalMillis

        BackupScheduler.sync(context, Settings(backupFolderUri = FOLDER, backupFrequency = BackupFrequency.WEEKLY))
        val weeklyMillis = workInfos().single().periodicityInfo?.repeatIntervalMillis

        assertEquals(TimeUnit.DAYS.toMillis(1), dailyMillis)
        assertEquals(TimeUnit.DAYS.toMillis(7), weeklyMillis)
    }
}
