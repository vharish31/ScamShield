package com.scamshield.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.scamshield.android.data.auth.FirebaseAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: FirebaseAuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun updateFullName(value: String) = _uiState.update { it.copy(fullName = value, errorMessage = null) }
    fun updateEmail(value: String) = _uiState.update { it.copy(email = value, errorMessage = null, infoMessage = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun updateConfirmPassword(value: String) = _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }

    fun clearMessages() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    fun signIn(onSuccess: () -> Unit) {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password
        if (!isValidEmail(email)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = authRepository.signIn(email, password)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, password = "", confirmPassword = "") }
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = mapFirebaseAuthError(err))
                }
            }
        }
    }

    fun register(onSuccess: () -> Unit) {
        val state = _uiState.value
        val name = state.fullName.trim()
        val email = state.email.trim()
        val password = state.password
        val confirm = state.confirmPassword

        if (name.length < 2) {
            _uiState.update { it.copy(errorMessage = "Please enter your full name.") }
            return
        }
        if (!isValidEmail(email)) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }
        if (password != confirm) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = authRepository.register(name, email, password)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, password = "", confirmPassword = "") }
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = mapFirebaseAuthError(err))
                }
            }
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (!isValidEmail(email)) {
            _uiState.update { it.copy(errorMessage = "Please enter your registered email address.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = authRepository.sendPasswordReset(email)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Password reset link sent to $email. Check your inbox."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = mapFirebaseAuthError(err))
                }
            }
        }
    }

    private fun isValidEmail(email: String): Boolean =
        email.contains("@") && email.contains(".") && email.length >= 5

    private fun mapFirebaseAuthError(err: Throwable): String {
        return when (err) {
            is FirebaseAuthInvalidUserException -> "No account found with that email address."
            is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
            is FirebaseAuthUserCollisionException -> "An account with this email already exists."
            is FirebaseAuthWeakPasswordException -> "Password is too weak. Choose at least 6 strong characters."
            is FirebaseNetworkException -> "Network connection error. Please check your internet connection."
            else -> err.localizedMessage ?: "Authentication failed. Please try again."
        }
    }
}

