package com.hasiru.usiru.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hasiru.usiru.data.AppDatabase

class AlertSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            AlertFirebaseSync(AppDatabase.get(applicationContext).alertDao()).pushUnsynced()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
