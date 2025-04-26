package com.example.finalproject.auth


sealed class LocalAuthState {
    object Loading : LocalAuthState()
    data class LoggedIn(val user: User) : LocalAuthState()
    object LoggedOut : LocalAuthState()
}

sealed class LocalLoginState {
    object Idle : LocalLoginState()
    object Loading : LocalLoginState()
    object Success : LocalLoginState()
    data class Error(val message: String) : LocalLoginState()
} 