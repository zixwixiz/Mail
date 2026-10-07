package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.MailboxCredentialStore
import com.example.data.repository.JluRepository

class MailSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(applicationContext)
        val repository = JluRepository(
            database = database,
            credentialStore = MailboxCredentialStore(applicationContext)
        )

        var hadFailure = false
        repository.availableMailboxes.value.forEach { mailbox ->
            try {
                repository.syncFolderMessages(mailbox.id, "INBOX")
            } catch (_: Throwable) {
                hadFailure = true
            }
        }

        return if (hadFailure) Result.retry() else Result.success()
    }
}
