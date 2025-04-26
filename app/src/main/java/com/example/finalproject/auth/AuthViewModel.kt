package com.example.finalproject.auth

import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val TAG = "AuthViewModel"
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState
    
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState
    
    init {
        Log.d(TAG, "Initializing AuthViewModel")
        auth.addAuthStateListener { firebaseAuth ->
            Log.d(TAG, "Auth state changed")
            checkAuthState()
        }
        
        checkAuthState()
    }
    
    private fun checkAuthState() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User is logged in: ${currentUser.email}")
            _authState.value = AuthState.LoggedIn(currentUser)
        } else {
            Log.d(TAG, "User is logged out")
            _authState.value = AuthState.LoggedOut
        }
    }
    
    fun signIn(email: String, password: String) {
        Log.d(TAG, "Attempting to sign in with email: $email")
        
        if (!isValidEmail(email)) {
            Log.d(TAG, "Invalid email format")
            _loginState.value = LoginState.Error("Please enter a valid email address")
            return
        }
        
        if (password.length < 6) {
            Log.d(TAG, "Password too short")
            _loginState.value = LoginState.Error("Password must be at least 6 characters")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting Firebase sign in process")
                _loginState.value = LoginState.Loading
                auth.signInWithEmailAndPassword(email, password).await()
                Log.d(TAG, "Firebase sign in successful")
                _loginState.value = LoginState.Success
            } catch (e: Exception) {
                Log.e(TAG, "Sign in failed with exception: ${e.javaClass.simpleName}", e)
                Log.e(TAG, "Error message: ${e.message}")
                val errorMessage = when {
                    e.message?.contains("no user record") == true -> "No account found with this email"
                    e.message?.contains("password is invalid") == true -> "Incorrect password"
                    e.message?.contains("blocked") == true -> "Too many failed attempts. Try again later."
                    e.message?.contains("network") == true -> "Network error: Please check your internet connection"
                    else -> "Authentication failed: ${e.message}"
                }
                Log.e(TAG, "Showing error to user: $errorMessage")
                _loginState.value = LoginState.Error(errorMessage)
            }
        }
    }
    
    fun signUp(email: String, password: String) {
        Log.d(TAG, "Attempting to sign up with email: $email")
        
        if (!isValidEmail(email)) {
            Log.d(TAG, "Invalid email format")
            _loginState.value = LoginState.Error("Please enter a valid email address")
            return
        }
        
        if (password.length < 6) {
            Log.d(TAG, "Password too short")
            _loginState.value = LoginState.Error("Password must be at least 6 characters")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting Firebase sign up process")
                _loginState.value = LoginState.Loading
                auth.createUserWithEmailAndPassword(email, password).await()
                Log.d(TAG, "Firebase sign up successful")
                _loginState.value = LoginState.Success
            } catch (e: Exception) {
                Log.e(TAG, "Sign up failed with exception: ${e.javaClass.simpleName}", e)
                Log.e(TAG, "Error message: ${e.message}")
                val errorMessage = when {
                    e.message?.contains("email address is already in use") == true -> 
                        "This email is already registered"
                    e.message?.contains("password is invalid") == true -> 
                        "Please choose a stronger password"
                    e.message?.contains("network") == true -> 
                        "Network error: Please check your internet connection"
                    else -> "Registration failed: ${e.message}"
                }
                Log.e(TAG, "Showing error to user: $errorMessage")
                _loginState.value = LoginState.Error(errorMessage)
            }
        }
    }
    
    fun signOut() {
        Log.d(TAG, "Signing out user")
        auth.signOut()
        _loginState.value = LoginState.Idle
    }
    
    fun resetLoginState() {
        Log.d(TAG, "Resetting login state")
        _loginState.value = LoginState.Idle
    }
    
    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    override fun onCleared() {
        Log.d(TAG, "AuthViewModel being cleared")
        super.onCleared()
        auth.removeAuthStateListener { checkAuthState() }
    }
}

sealed class AuthState {
    object Loading : AuthState()
    data class LoggedIn(val user: FirebaseUser) : AuthState()
    object LoggedOut : AuthState()
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
} 