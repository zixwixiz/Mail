package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import java.util.concurrent.TimeUnit

class SnoozeReleaseWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val emailId = inputData.getString(KEY_EMAIL_ID) ?: return Result.failure()
        return try {
            AppDatabase.getInstance(applicationContext).mailDao().setSnoozedUntil(emailId, null)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_EMAIL_ID = "email_id"
    }
}

object SnoozeScheduler {
    fun schedule(context: Context, emailId: String, snoozedUntil: Long) {
        val delay = (snoozedUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<SnoozeReleaseWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SnoozeReleaseWorker.KEY_EMAIL_ID to emailId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "mail-snooze-$emailId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
