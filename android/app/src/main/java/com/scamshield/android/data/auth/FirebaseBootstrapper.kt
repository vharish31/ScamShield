package com.scamshield.android.data.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.scamshield.android.BuildConfig

object FirebaseBootstrapper {
    fun ensureInitialized(context: Context): FirebaseApp {
        val existing = FirebaseApp.getApps(context)
        if (existing.isNotEmpty()) {
            return existing.first()
        }
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
            .build()
        return FirebaseApp.initializeApp(context, options)
    }
}

