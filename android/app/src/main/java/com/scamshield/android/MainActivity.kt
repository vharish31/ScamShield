package com.scamshield.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamshield.android.notifications.ScamNotificationManager
import com.scamshield.android.service.LiveAudioAnalysisService
import com.scamshield.android.ui.navigation.AppRootViewModel
import com.scamshield.android.ui.navigation.ScamShieldRootNav
import com.scamshield.android.ui.theme.ScamShieldTheme
import com.scamshield.android.util.RoleAndPermissionHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var deepLinkDestination by mutableStateOf<String?>(null)
    private var deepLinkAnalysisId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            val rootViewModel: AppRootViewModel = hiltViewModel()
            val rootState by rootViewModel.rootState.collectAsStateWithLifecycle()
            val darkTheme = rootState.settings.darkModeOverride ?: isSystemInDarkTheme()

            ScamShieldTheme(darkTheme = darkTheme) {
                ScamShieldRootNav(
                    rootViewModel = rootViewModel,
                    initialNotificationDestination = deepLinkDestination,
                    initialAnalysisId = deepLinkAnalysisId,
                    onDeepLinkConsumed = {
                        deepLinkDestination = null
                        deepLinkAnalysisId = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        deepLinkDestination = intent?.getStringExtra(ScamNotificationManager.EXTRA_NAV_DESTINATION)
        deepLinkAnalysisId = intent?.getStringExtra(ScamNotificationManager.EXTRA_ANALYSIS_ID)

        val autoStart = intent?.getBooleanExtra(ScamNotificationManager.EXTRA_AUTO_START_LIVE, false) == true
        if (autoStart && RoleAndPermissionHelper.isMicrophoneGranted(this)) {
            val phone = intent?.getStringExtra(ScamNotificationManager.EXTRA_PHONE_NUMBER)
            runCatching {
                ContextCompat.startForegroundService(
                    this,
                    LiveAudioAnalysisService.startIntent(this, phoneNumber = phone)
                )
            }
        }
    }
}
