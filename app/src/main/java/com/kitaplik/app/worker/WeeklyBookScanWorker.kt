package com.kitaplik.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kitaplik.app.data.local.AppDatabase
import com.kitaplik.app.data.local.UserPreferencesRepository
import com.kitaplik.app.domain.scanner.DocumentScanner

/**
 * Haftada bir kez arka planda pil dostu kısıtlamalarla (RequiresBatteryNotLow)
 * çalışarak Dokümanlar klasörüne yeni eklenen kitapları tarayan WorkManager işçisi.
 */
class WeeklyBookScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val database = AppDatabase.getInstance(applicationContext)
            val preferences = UserPreferencesRepository(applicationContext)
            val scanner = DocumentScanner(applicationContext, database.bookDao())

            scanner.scanDocumentsDirectory()
            preferences.updateLastScanTimestamp()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
