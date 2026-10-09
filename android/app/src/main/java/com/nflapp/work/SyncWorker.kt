package com.nflapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nflapp.NflApp
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as NflApp).container.repository
        val last = repository.lastCheckedMs()?.let(Instant::ofEpochMilli)
        if (!SyncSchedule.isDue(last, Instant.now())) return Result.success()
        return try {
            repository.refresh()
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: HttpException) {
            if (e.code() >= 500 && runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: kotlinx.serialization.SerializationException) {
            Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
