package com.kitaplik.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ScanScheduler {

    private const val WEEKLY_SCAN_WORK_NAME = "kitaplik_weekly_book_scan"

    /**
     * Haftada bir kez arka planda kitap taraması yapacak görevi planlar.
     * Cihaz pilini korumak için yalnızca pil seviyesi düşük değilken çalışır ve
     * uygulama açılışında anında tetiklenmemesi için başlangıç gecikmesi (7 gün) kullanır.
     */
    fun scheduleWeeklyScan(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val weeklyRequest = PeriodicWorkRequestBuilder<WeeklyBookScanWorker>(
            repeatInterval = 7,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WEEKLY_SCAN_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            weeklyRequest
        )
    }

    fun cancelWeeklyScan(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WEEKLY_SCAN_WORK_NAME)
    }
}
