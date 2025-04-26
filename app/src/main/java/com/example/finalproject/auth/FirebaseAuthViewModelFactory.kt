package com.example.finalproject.auth

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class FirebaseAuthViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FirebaseAuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FirebaseAuthViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 