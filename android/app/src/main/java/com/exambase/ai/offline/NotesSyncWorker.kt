package com.exambase.ai.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.exambase.ai.auth.appContainer
import java.util.concurrent.TimeUnit

class NotesSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer()
        val dao = ExamBaseDatabase.get(applicationContext).offlineDao()
        val prefs = EncryptedSyncPrefs(applicationContext)
        val engine = NotesSyncEngine(dao, container.noteRepository, prefs)
        val outcome = engine.syncOnce()
        return if (outcome.error != null && outcome.pushed == 0 && outcome.pulled == 0) {
            Result.retry()
        } else {
            Result.success(
                workDataOf(
                    "pushed" to outcome.pushed,
                    "pulled" to outcome.pulled,
                    "conflicts" to outcome.conflicts,
                ),
            )
        }
    }

    companion object {
        const val UNIQUE = "notes_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = OneTimeWorkRequestBuilder<NotesSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE,
                ExistingWorkPolicy.KEEP,
                req,
            )
        }
    }
}
