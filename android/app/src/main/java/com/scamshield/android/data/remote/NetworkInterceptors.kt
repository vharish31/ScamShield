package com.scamshield.android.data.remote

import com.scamshield.android.data.remote.dto.ApiErrorEnvelopeDto
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun interface BaseUrlProvider {
    fun getBaseUrl(): String
}

interface AuthTokenProvider {
    suspend fun getIdToken(forceRefresh: Boolean = false): String?
    fun getIdTokenBlocking(forceRefresh: Boolean = false): String?
}

/**
 * Rewrites each request URL dynamically against the user-configured backend URL in Settings,
 * allowing seamless switching between emulator loopback, local LAN FastAPI servers, and HTTPS deployments.
 */
class DynamicBaseUrlInterceptor(
    private val baseUrlProvider: BaseUrlProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val configuredBase = baseUrlProvider.getBaseUrl().trim().trimEnd('/')
        val targetBaseUrl = configuredBase.toHttpUrlOrNull()

        if (targetBaseUrl == null) {
            return chain.proceed(originalRequest)
        }

        val originalUrl = originalRequest.url
        val baseSegments = targetBaseUrl.pathSegments.filter { it.isNotEmpty() }
        val requestSegments = originalUrl.pathSegments.filter { it.isNotEmpty() }

        val rebuiltBuilder = originalUrl.newBuilder()
            .scheme(targetBaseUrl.scheme)
            .host(targetBaseUrl.host)
            .port(targetBaseUrl.port)

        // Replace path segments cleanly when target BaseUrl has a sub-path (or root)
        if (baseSegments.isNotEmpty()) {
            val combined = baseSegments + requestSegments
            val currentCount = originalUrl.pathSize
            for (i in currentCount - 1 downTo 0) {
                rebuiltBuilder.removePathSegment(i)
            }
            combined.forEach { rebuiltBuilder.addPathSegment(it) }
        }

        val newRequest = originalRequest.newBuilder()
            .url(rebuiltBuilder.build())
            .build()
        return chain.proceed(newRequest)
    }
}

/**
 * Attaches the Firebase ID token as Bearer Authorization header to backend API calls.
 */
class FirebaseAuthInterceptor(
    private val tokenProvider: AuthTokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null) {
            return chain.proceed(request)
        }

        val token = tokenProvider.getIdTokenBlocking(forceRefresh = false)
        val authorizedRequest = if (!token.isNullOrBlank()) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        val response = chain.proceed(authorizedRequest)

        // Retry once with a force-refreshed token if the server rejected a stale token
        if (response.code == 401 && !token.isNullOrBlank()) {
            val refreshedToken = tokenProvider.getIdTokenBlocking(forceRefresh = true)
            if (!refreshedToken.isNullOrBlank() && refreshedToken != token) {
                response.close()
                val retried = request.newBuilder()
                    .header("Authorization", "Bearer $refreshedToken")
                    .build()
                return chain.proceed(retried)
            }
        }
        return response
    }
}

object ApiErrorParser {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    fun toUserMessage(throwable: Throwable): String {
        return when (throwable) {
            is HttpException -> parseHttpException(throwable)
            is UnknownHostException -> "Cannot reach backend server. Check the server URL and network connection in Settings."
            is SocketTimeoutException -> "The backend server took too long to respond. Please try again."
            is IOException -> "Network error communicating with backend: ${throwable.localizedMessage ?: "Connection failed"}"
            else -> throwable.localizedMessage ?: "An unexpected error occurred."
        }
    }

    private fun parseHttpException(e: HttpException): String {
        val rawBody = try {
            e.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        if (!rawBody.isNullOrBlank()) {
            try {
                val envelope = lenientJson.decodeFromString<ApiErrorEnvelopeDto>(rawBody)
                envelope.error?.message?.takeIf { it.isNotBlank() }?.let { return it }
            } catch (_: Exception) {
                // Fall through to status code message
            }
        }
        return when (e.code()) {
            401 -> "Authentication required or session expired. Please sign in again."
            403 -> "Access denied for this resource."
            404 -> "Requested item was not found on the server."
            413 -> "Audio file is too large. Maximum supported size is 25 MB."
            422 -> "Invalid input submitted to the backend."
            503 -> "Backend speech or AI service is currently unavailable."
            else -> "Backend request failed (HTTP ${e.code()})."
        }
    }
}

