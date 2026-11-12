package com.scamshield.android.data.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.scamshield.android.data.remote.AuthTokenProvider
import com.scamshield.android.data.remote.ScamShieldApiService
import com.scamshield.android.data.remote.dto.TokenVerifyRequestDto
import com.scamshield.android.domain.model.UserAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val apiServiceProvider: Provider<ScamShieldApiService>
) : AuthTokenProvider {

    val authStateFlow: Flow<UserAccount?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toDomain())
        }
        trySend(firebaseAuth.currentUser?.toDomain())
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    fun currentAccount(): UserAccount? = firebaseAuth.currentUser?.toDomain()

    override suspend fun getIdToken(forceRefresh: Boolean): String? = withContext(Dispatchers.IO) {
        val user = firebaseAuth.currentUser ?: return@withContext null
        try {
            user.getIdToken(forceRefresh).await()?.token
        } catch (_: Exception) {
            null
        }
    }

    override fun getIdTokenBlocking(forceRefresh: Boolean): String? {
        val user = firebaseAuth.currentUser ?: return null
        return try {
            Tasks.await(user.getIdToken(forceRefresh), 10, TimeUnit.SECONDS)?.token
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signIn(email: String, password: String): Result<UserAccount> = withContext(Dispatchers.IO) {
        try {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return@withContext Result.failure(
                IllegalStateException("Authentication succeeded but no user profile was returned.")
            )
            // Force refresh token so latest display name/claims are synced
            val token = try {
                user.getIdToken(false).await()?.token
            } catch (_: Exception) {
                null
            }
            // Best-effort backend session check (does not block offline app access)
            if (!token.isNullOrBlank()) {
                runCatching { apiServiceProvider.get().verifyToken(TokenVerifyRequestDto(token)) }
            }
            Result.success(user.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(fullName: String, email: String, password: String): Result<UserAccount> =
        withContext(Dispatchers.IO) {
            try {
                val trimmedName = fullName.trim()
                val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = result.user ?: return@withContext Result.failure(
                    IllegalStateException("Registration succeeded but no user record was returned.")
                )
                if (trimmedName.isNotEmpty()) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(trimmedName)
                        .build()
                    user.updateProfile(profileUpdates).await()
                    user.reload().await()
                }
                val refreshedToken = try {
                    user.getIdToken(true).await()?.token
                } catch (_: Exception) {
                    null
                }
                if (!refreshedToken.isNullOrBlank()) {
                    runCatching {
                        apiServiceProvider.get().verifyToken(TokenVerifyRequestDto(refreshedToken))
                    }
                }
                val refreshedUser = firebaseAuth.currentUser ?: user
                Result.success(
                    UserAccount(
                        uid = refreshedUser.uid,
                        email = refreshedUser.email,
                        displayName = refreshedUser.displayName ?: trimmedName.ifEmpty { null }
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun updateDisplayName(displayName: String): Result<UserAccount> = withContext(Dispatchers.IO) {
        val user = firebaseAuth.currentUser ?: return@withContext Result.failure(
            IllegalStateException("No signed-in user.")
        )
        try {
            val trimmed = displayName.trim()
            val update = UserProfileChangeRequest.Builder().setDisplayName(trimmed).build()
            user.updateProfile(update).await()
            user.reload().await()
            user.getIdToken(true).await()
            val current = firebaseAuth.currentUser ?: user
            Result.success(
                UserAccount(
                    uid = current.uid,
                    email = current.email,
                    displayName = current.displayName ?: trimmed
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    private fun FirebaseUser.toDomain(): UserAccount = UserAccount(
        uid = uid,
        email = email,
        displayName = displayName?.takeIf { it.isNotBlank() }
    )
}

