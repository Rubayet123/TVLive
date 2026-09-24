package io.github.rubayet123.tvlive.data.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.rubayet123.tvlive.data.SourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class M3uRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = SourceRepository(applicationContext)
        val sources = repository.getSources()

        var anySuccess = false
        var anyFailure = false

        sources.filter { it.isActive && it.url.startsWith("http") && it.refreshIntervalHours != 0 }
            .forEach { source ->
                val success = repository.syncSource(source)
                if (success) {
                    anySuccess = true
                } else {
                    anyFailure = true
                }
            }

        if (anyFailure && !anySuccess) Result.retry() else Result.success()
    }
}
