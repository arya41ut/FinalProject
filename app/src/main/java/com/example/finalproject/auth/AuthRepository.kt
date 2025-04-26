package com.example.finalproject.auth

import android.util.Log
import com.example.finalproject.api.ApiService
import com.example.finalproject.api.ErrorResponse
import com.example.finalproject.api.LoginRequest
import com.example.finalproject.api.RegisterRequest
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val apiService: ApiService) {
    private val TAG = "AuthRepository"
    
    suspend fun login(email: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to login with email: $email")
                val loginRequest = LoginRequest(email, password)
                val response = apiService.login(loginRequest)
                
                if (response.isSuccessful) {
                    val authResponse = response.body()
                    if (authResponse != null) {
                        Log.d(TAG, "Login successful for user: ${authResponse.username}")
                        val user = User(
                            id = authResponse.id.toString(),
                            username = authResponse.username,
                            email = authResponse.email
                        )
                        Result.success(user)
                    } else {
                        Log.e(TAG, "Login response body is null")
                        Result.failure(Exception("Login failed: Empty response"))
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Login failed with status code: ${response.code()}, error: $errorBody")
                    
                    val errorMessage = if (errorBody != null) {
                        try {
                            val moshi = Moshi.Builder().build()
                            val adapter = moshi.adapter(ErrorResponse::class.java)
                            val errorResponse = adapter.fromJson(errorBody)
                            errorResponse?.error ?: "Login failed"
                        } catch (e: Exception) {
                            "Login failed"
                        }
                    } else {
                        "Login failed"
                    }
                    
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during login", e)
                Result.failure(e)
            }
        }
    }
    
    suspend fun register(username: String, email: String, password: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to register user with email: $email")
                val registerRequest = RegisterRequest(username, email, password)
                val response = apiService.register(registerRequest)
                
                if (response.isSuccessful) {
                    val authResponse = response.body()
                    if (authResponse != null) {
                        Log.d(TAG, "Registration successful for user: ${authResponse.username}")
                        val user = User(
                            id = authResponse.id.toString(),
                            username = authResponse.username,
                            email = authResponse.email
                        )
                        Result.success(user)
                    } else {
                        Log.e(TAG, "Registration response body is null")
                        Result.failure(Exception("Registration failed: Empty response"))
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Registration failed with status code: ${response.code()}, error: $errorBody")
                    
                    val errorMessage = if (errorBody != null) {
                        try {
                            val moshi = Moshi.Builder().build()
                            val adapter = moshi.adapter(ErrorResponse::class.java)
                            val errorResponse = adapter.fromJson(errorBody)
                            errorResponse?.error ?: "Registration failed"
                        } catch (e: Exception) {
                            "Registration failed"
                        }
                    } else {
                        "Registration failed"
                    }
                    
                    Result.failure(Exception(errorMessage))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during registration", e)
                Result.failure(e)
            }
        }
    }
}

data class User(
    val id: String,
    val username: String,
    val email: String
) 