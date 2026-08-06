package com.example.unmask.core

import android.content.Context
import com.example.unmask.data.FirebaseConfig
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object FCMTokenManager {

    fun initAndSyncFCMToken(context: Context) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                val token = task.result
                saveTokenToFirestore(token, context)
            }
        }
    }

    fun saveTokenToFirestore(token: String, context: Context? = null) {
        val currentUser = FirebaseConfig.auth.currentUser ?: return
        val uid = currentUser.uid
        if (uid.isEmpty() || uid == "offline_demo_user") return

        if (context != null) {
            try {
                val prefs = context.getSharedPreferences("unmask_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("current_user_uid", uid).apply()
            } catch (_: Exception) {}
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                FirebaseConfig.firestore.collection("users").document(uid).update("fcmToken", token).await()
            } catch (e: Exception) {
                // Doküman henüz update edilemiyorsa set ile merge et
                try {
                    FirebaseConfig.firestore.collection("users").document(uid)
                        .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge()).await()
                } catch (_: Exception) {}
            }
        }
    }
}
