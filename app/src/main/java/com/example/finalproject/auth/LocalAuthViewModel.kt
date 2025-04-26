package com.example.finalproject.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.finalproject.api.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.finalproject.api.User as ApiUser

class LocalAuthViewModel(
    application: Application,
    private val repository: AuthRepository
) : AndroidViewModel(application) {
    private val TAG = "LocalAuthViewModel"
    
    private val _authState = MutableStateFlow<LocalAuthState>(LocalAuthState.LoggedOut)
    val authState: StateFlow<LocalAuthState> = _authState
    
    private val _loginState = MutableStateFlow<LocalLoginState>(LocalLoginState.Idle)
    val loginState: StateFlow<LocalLoginState> = _loginState
    
    private val userManager = UserManager.getInstance(application)
    
    private var currentUser: User? = null
    
    init {
        Log.d(TAG, "Initializing LocalAuthViewModel")
        checkIfUserLoggedIn()
    }
    
    fun checkIfUserLoggedIn() {
        userManager.getUser()?.let { apiUser ->
            val user = User(
                id = apiUser.id,
                username = apiUser.username,
                email = apiUser.email
            )
            currentUser = user
            _authState.value = LocalAuthState.LoggedIn(user)
        }
    }
    
    fun signIn(email: String, password: String) {
        Log.d(TAG, "Attempting to sign in with email: $email")
        
        if (!isValidEmail(email)) {
            Log.d(TAG, "Invalid email format")
            _loginState.value = LocalLoginState.Error("Please enter a valid email address")
            return
        }
        
        if (password.length < 6) {
            Log.d(TAG, "Password too short")
            _loginState.value = LocalLoginState.Error("Password must be at least 6 characters")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting login process")
                _loginState.value = LocalLoginState.Loading
                
                val result = repository.login(email, password)
                
                result.fold(
                    onSuccess = { user ->
                        Log.d(TAG, "Login successful")
                        currentUser = user
                        
                        val apiUser = ApiUser(
                            id = user.id,
                            username = user.username,
                            email = user.email
                        )
                        userManager.saveUser(apiUser)
                        
                        _authState.value = LocalAuthState.LoggedIn(user)
                        _loginState.value = LocalLoginState.Success
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Login failed", exception)
                        _loginState.value = LocalLoginState.Error(exception.message ?: "Authentication failed")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during sign in", e)
                _loginState.value = LocalLoginState.Error("Authentication failed: ${e.message}")
            }
        }
    }
    
    fun signUp(username: String, email: String, password: String) {
        Log.d(TAG, "Attempting to sign up with email: $email")
        
        if (username.isBlank()) {
            Log.d(TAG, "Username is blank")
            _loginState.value = LocalLoginState.Error("Username cannot be empty")
            return
        }
        
        if (!isValidEmail(email)) {
            Log.d(TAG, "Invalid email format")
            _loginState.value = LocalLoginState.Error("Please enter a valid email address")
            return
        }
        
        if (password.length < 6) {
            Log.d(TAG, "Password too short")
            _loginState.value = LocalLoginState.Error("Password must be at least 6 characters")
            return
        }
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting registration process")
                _loginState.value = LocalLoginState.Loading
                
                val result = repository.register(username, email, password)
                
                result.fold(
                    onSuccess = { user ->
                        Log.d(TAG, "Registration successful")
                        currentUser = user
                        _authState.value = LocalAuthState.LoggedIn(user)
                        _loginState.value = LocalLoginState.Success
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Registration failed", exception)
                        _loginState.value = LocalLoginState.Error(exception.message ?: "Registration failed")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during sign up", e)
                _loginState.value = LocalLoginState.Error("Registration failed: ${e.message}")
            }
        }
    }
    
    fun signOut() {
        userManager.clearUser()
        currentUser = null
        _authState.value = LocalAuthState.LoggedOut
    }
    
    fun resetLoginState() {
        _loginState.value = LocalLoginState.Idle
    }
    
    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
} 