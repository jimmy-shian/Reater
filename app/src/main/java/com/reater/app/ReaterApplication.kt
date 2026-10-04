package com.reater.app

import android.app.Application
import com.reater.app.data.repository.SettingsRepository
import com.reater.app.domain.OnDeviceClassifier
import com.reater.app.data.billing.PlayBillingManager
import com.reater.app.notify.NotifyCenter
import com.reater.app.widget.WidgetUpdateWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ReaterApplication : Application() {

    @Inject
    lateinit var classifier: OnDeviceClassifier

    @Inject
    lateinit var billingManager: PlayBillingManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        NotifyCenter.ensureChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            classifier.seedInitialCategoriesIfEmpty()
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                // 舊版 pro_unlocked=true 的啟用碼用戶：補寫新旗標，之後 Play 對帳不再覆蓋
                settingsRepository.migrateLegacyProIfNeeded()
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                NotifyCenter.rescheduleReviewDigest(
                    this@ReaterApplication,
                    settingsRepository.reviewDigestEnabled.first(),
                    settingsRepository.reviewDigestHour.first()
                )
            }
        }
        // Schedule Glance Widget 4-hour background update
        WidgetUpdateWorker.schedulePeriodicUpdate(this)
        billingManager.connectAndRefresh()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                billingManager.refreshPurchases()
            }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }
}
