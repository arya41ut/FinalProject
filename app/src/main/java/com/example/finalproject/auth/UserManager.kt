package com.example.finalproject.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.finalproject.api.User
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class UserManager(context: Context) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val userAdapter = moshi.adapter(User::class.java)
    
    companion object {
        private const val KEY_CURRENT_USER = "current_user"
        private const val KEY_TOKEN = "auth_token"
        
        @Volatile
        private var INSTANCE: UserManager? = null
        
        fun getInstance(context: Context): UserManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    fun saveUser(user: User) {
        val userJson = userAdapter.toJson(user)
        sharedPreferences.edit().putString(KEY_CURRENT_USER, userJson).apply()
    }
    
    fun getUser(): User? {
        val userJson = sharedPreferences.getString(KEY_CURRENT_USER, null) ?: return null
        return try {
            userAdapter.fromJson(userJson)
        } catch (e: Exception) {
            null
        }
    }
    
    fun saveToken(token: String) {
        sharedPreferences.edit().putString(KEY_TOKEN, token).apply()
    }
    
    fun getToken(): String? {
        return sharedPreferences.getString(KEY_TOKEN, null)
    }
    
    fun clearUser() {
        sharedPreferences.edit().remove(KEY_CURRENT_USER).remove(KEY_TOKEN).apply()
    }
    
    fun isLoggedIn(): Boolean {
        return getUser() != null
    }
} 