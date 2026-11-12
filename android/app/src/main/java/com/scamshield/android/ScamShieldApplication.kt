package com.scamshield.android

import android.app.Application
import com.scamshield.android.data.auth.FirebaseBootstrapper
import com.scamshield.android.notifications.ScamNotificationManager
import com.scamshield.android.worker.RetentionCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ScamShieldApplication : Application() {

    @Inject
    lateinit var notificationManager: ScamNotificationManager

    override fun onCreate() {
        super.onCreate()
        FirebaseBootstrapper.ensureInitialized(this)
        notificationManager.ensureNotificationChannels()
        RetentionCleanupWorker.schedule(this)
    }
}
