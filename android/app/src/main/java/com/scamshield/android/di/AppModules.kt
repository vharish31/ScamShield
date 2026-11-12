package com.scamshield.android.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.scamshield.android.BuildConfig
import com.scamshield.android.data.auth.FirebaseAuthRepository
import com.scamshield.android.data.auth.FirebaseBootstrapper
import com.scamshield.android.data.local.CachedAnalysisDao
import com.scamshield.android.data.local.CallRecordingDao
import com.scamshield.android.data.local.NotificationLogDao
import com.scamshield.android.data.local.ScamShieldDatabase
import com.scamshield.android.data.local.ScreeningEventDao
import com.scamshield.android.data.local.TrustedContactDao
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.data.remote.AuthTokenProvider
import com.scamshield.android.data.remote.BaseUrlProvider
import com.scamshield.android.data.remote.DynamicBaseUrlInterceptor
import com.scamshield.android.data.remote.FirebaseAuthInterceptor
import com.scamshield.android.data.remote.ScamShieldApiService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    @Singleton
    abstract fun bindBaseUrlProvider(impl: AppPreferencesRepository): BaseUrlProvider

    @Binds
    @Singleton
    abstract fun bindAuthTokenProvider(impl: FirebaseAuthRepository): AuthTokenProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(@ApplicationContext context: Context): FirebaseAuth {
        val app = FirebaseBootstrapper.ensureInitialized(context)
        return FirebaseAuth.getInstance(app)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScamShieldDatabase =
        Room.databaseBuilder(
            context,
            ScamShieldDatabase::class.java,
            "scam_shield.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()

    @Provides
    fun provideTrustedContactDao(db: ScamShieldDatabase): TrustedContactDao = db.trustedContactDao()

    @Provides
    fun provideScreeningEventDao(db: ScamShieldDatabase): ScreeningEventDao = db.screeningEventDao()

    @Provides
    fun provideCachedAnalysisDao(db: ScamShieldDatabase): CachedAnalysisDao = db.cachedAnalysisDao()

    @Provides
    fun provideNotificationLogDao(db: ScamShieldDatabase): NotificationLogDao = db.notificationLogDao()

    @Provides
    fun provideCallRecordingDao(db: ScamShieldDatabase): CallRecordingDao = db.callRecordingDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        baseUrlProvider: BaseUrlProvider,
        authTokenProvider: AuthTokenProvider
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(DynamicBaseUrlInterceptor(baseUrlProvider))
            .addInterceptor(FirebaseAuthInterceptor(authTokenProvider))
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8000/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideScamShieldApiService(retrofit: Retrofit): ScamShieldApiService =
        retrofit.create(ScamShieldApiService::class.java)
}

