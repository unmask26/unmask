package com.example.unmask.data

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

object FirebaseConfig {
    private const val DATABASE_ID = "ai-studio-e030f33d-59c1-49c4-9657-c57ca2d9ad00"

    fun initialize(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyBFz1rdaP5cUmukIwnmzRDaUwOSRQRTNzc")
                // Programmatic Application ID format for Firebase on Android
                .setApplicationId("1:242033696510:android:138ade331aa3ab221d4faf")
                .setProjectId("hidate-621c5")
                .setStorageBucket("hidate-621c5.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
    }

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    val firestore: FirebaseFirestore
        get() {
            val app = FirebaseApp.getInstance()
            return FirebaseFirestore.getInstance(app, DATABASE_ID)
        }

    val storage: FirebaseStorage
        get() = FirebaseStorage.getInstance()
}
