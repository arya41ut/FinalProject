package com.example.finalproject.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalproject.api.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.finalproject.api.ApiService
import com.example.finalproject.api.RegisterRequest

class FirebaseAuthViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "FirebaseAuthViewModel"
    
    private val _authState = MutableStateFlow<LocalAuthState>(LocalAuthState.LoggedOut)
    val authState: StateFlow<LocalAuthState> = _authState
    
    private val _loginState = MutableStateFlow<LocalLoginState>(LocalLoginState.Idle)
    val loginState: StateFlow<LocalLoginState> = _loginState
    
    private val auth = FirebaseAuth.getInstance()
    
    private val userManager = UserManager.getInstance(application)
    
    init {
        checkIfUserLoggedIn()
    }
    
    fun checkIfUserLoggedIn() {
        auth.currentUser?.let { firebaseUser ->
            val user = com.example.finalproject.auth.User(
                id = firebaseUser.uid,
                username = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "User",
                email = firebaseUser.email ?: ""
            )
            _authState.value = LocalAuthState.LoggedIn(user)
            
            val apiUser = com.example.finalproject.api.User(
                id = user.id,
                username = user.username,
                email = user.email
            )
            userManager.saveUser(apiUser)
            
            viewModelScope.launch {
                try {
                    val registerRequest = RegisterRequest(
                        username = user.username,
                        email = user.email,
                        password = "firebase_auth"
                    )
                    ApiService.create().register(registerRequest)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register user in backend", e)
                }
            }
        } ?: run {
            _authState.value = LocalAuthState.LoggedOut
        }
    }
    
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            try {
                _loginState.value = LocalLoginState.Loading
                
                withContext(Dispatchers.IO) {
                    auth.signInWithEmailAndPassword(email, password).await()
                }
                
                checkIfUserLoggedIn()
                _loginState.value = LocalLoginState.Success
            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                _loginState.value = LocalLoginState.Error(e.message ?: "Authentication failed")
            }
        }
    }
    
    fun signUp(username: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                _loginState.value = LocalLoginState.Loading
                
                withContext(Dispatchers.IO) {
                    val result = auth.createUserWithEmailAndPassword(email, password).await()
                    result.user?.updateProfile(
                        com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(username)
                            .build()
                    )?.await()
                }
                
                checkIfUserLoggedIn()
                _loginState.value = LocalLoginState.Success
            } catch (e: Exception) {
                Log.e(TAG, "Registration failed", e)
                _loginState.value = LocalLoginState.Error(e.message ?: "Registration failed")
            }
        }
    }
    
    fun signOut() {
        auth.signOut()
        userManager.clearUser()
        _authState.value = LocalAuthState.LoggedOut
    }
    
    fun resetLoginState() {
        _loginState.value = LocalLoginState.Idle
    }
} 