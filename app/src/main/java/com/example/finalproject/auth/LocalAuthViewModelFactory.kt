package com.example.finalproject.auth

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.finalproject.api.ApiService

class LocalAuthViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocalAuthViewModel::class.java)) {
            val repository = AuthRepository(ApiService.create())
            return LocalAuthViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 