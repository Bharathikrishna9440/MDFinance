package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.network.FirebaseUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.i("AppUpdateWorker", "Starting periodic background update check...")
        return withContext(Dispatchers.Main) {
            try {
                FirebaseUpdateManager.checkForCloudUpdates(applicationContext)
                Result.success()
            } catch (e: Exception) {
                Log.e("AppUpdateWorker", "Periodic background update check failed", e)
                Result.retry()
            }
        }
    }
}
