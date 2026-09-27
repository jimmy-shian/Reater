package com.reater.app

import android.app.Application
import com.reater.app.domain.OnDeviceClassifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ReaterApplication : Application() {

    @Inject
    lateinit var classifier: OnDeviceClassifier

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            classifier.seedInitialCategoriesIfEmpty()
        }
    }
}
