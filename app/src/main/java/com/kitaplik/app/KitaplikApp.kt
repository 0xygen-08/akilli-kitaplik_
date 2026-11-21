package com.kitaplik.app

import android.app.Application
import com.kitaplik.app.data.local.UserPreferencesRepository
import com.kitaplik.app.worker.ScanScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class KitaplikApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)

        // Uygulama açılışında tarama yapmaz; yalnızca kullanıcının haftalık arka plan
        // tarama tercihi açıksa WorkManager periyodik görevini (7 gün aralıklı) kayıtlı tutar.
        applicationScope.launch {
            val prefs = UserPreferencesRepository(applicationContext).preferencesFlow.first()
            if (prefs.weeklyAutoScanEnabled) {
                ScanScheduler.scheduleWeeklyScan(applicationContext)
            }
        }
    }
}
