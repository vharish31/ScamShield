package com.scamshield.android.data.remote

import com.scamshield.android.data.remote.dto.AnalysisResponseDto
import com.scamshield.android.data.remote.dto.AnalyzeTextRequestDto
import com.scamshield.android.data.remote.dto.AudioUploadResponseDto
import com.scamshield.android.data.remote.dto.DeleteAudioResponseDto
import com.scamshield.android.data.remote.dto.DeleteHistoryResponseDto
import com.scamshield.android.data.remote.dto.HealthResponseDto
import com.scamshield.android.data.remote.dto.HistoryListResponseDto
import com.scamshield.android.data.remote.dto.TokenVerifyRequestDto
import com.scamshield.android.data.remote.dto.UserProfileDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ScamShieldApiService {

    @GET("health")
    suspend fun getHealth(): HealthResponseDto

    @POST("api/auth/verify")
    suspend fun verifyToken(@Body request: TokenVerifyRequestDto): UserProfileDto

    @GET("api/auth/me")
    suspend fun getCurrentUser(): UserProfileDto

    @Multipart
    @POST("api/audio/upload")
    suspend fun uploadAudio(@Part file: MultipartBody.Part): AudioUploadResponseDto

    @DELETE("api/audio/{audioId}")
    suspend fun deleteAudio(@Path("audioId") audioId: String): DeleteAudioResponseDto

    @Multipart
    @POST("api/analyze/audio")
    suspend fun analyzeAudioById(@Part("audio_id") audioId: RequestBody): AnalysisResponseDto

    @Multipart
    @POST("api/analyze/audio")
    suspend fun analyzeAudioFile(@Part file: MultipartBody.Part): AnalysisResponseDto

    @POST("api/analyze/text")
    suspend fun analyzeText(@Body request: AnalyzeTextRequestDto): AnalysisResponseDto

    @GET("api/history")
    suspend fun getHistory(): HistoryListResponseDto

    @GET("api/history/{analysisId}")
    suspend fun getHistoryDetail(@Path("analysisId") analysisId: String): AnalysisResponseDto

    @DELETE("api/history/{analysisId}")
    suspend fun deleteHistoryItem(@Path("analysisId") analysisId: String): DeleteHistoryResponseDto
}

