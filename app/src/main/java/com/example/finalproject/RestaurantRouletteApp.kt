package com.example.finalproject

import android.app.Application
import com.google.firebase.FirebaseApp

class RestaurantRouletteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
} 