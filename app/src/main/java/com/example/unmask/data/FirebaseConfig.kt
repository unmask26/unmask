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
    fun initialize(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyBChPtWYyMwI0crZd2cVFAky3V60-uvStE")
                .setApplicationId("1:908513940709:android:ae265db40e8f7a2422700c")
                .setProjectId("unmask-app-2026")
                .setStorageBucket("unmask-app-2026.firebasestorage.app")
                .setGcmSenderId("908513940709")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
    }

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val storage: FirebaseStorage
        get() = FirebaseStorage.getInstance()
}
