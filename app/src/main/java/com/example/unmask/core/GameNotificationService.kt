package com.example.unmask.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.unmask.data.DirectGameRequest
import com.example.unmask.data.FirebaseConfig
import com.google.firebase.firestore.ListenerRegistration

class GameNotificationService : Service() {

    private var firestoreListener: ListenerRegistration? = null
    private var isStarted = false

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceInternal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isStarted) {
            isStarted = true
            startForegroundServiceInternal()
            startListeningForRequests()
        }
        return START_STICKY
    }

    private fun startForegroundServiceInternal() {
        val channelId = "game_service_bg_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Arka Plan Oyun Dinleyicisi",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Uygulama kapalıyken davet dinleme servisi"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("UNMASK Oyun Servisi")
            .setContentText("Davet dinleyici aktif")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        try {
            startForeground(1001, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startListeningForRequests() {
        val prefs = getSharedPreferences("unmask_prefs", Context.MODE_PRIVATE)
        val savedUid = prefs.getString("current_user_uid", null) ?: return
        val savedNickname = prefs.getString("current_user_nickname", null)?.removePrefix("@")?.trim() ?: ""

        firestoreListener?.remove()

        try {
            val firestore = FirebaseConfig.firestore
            // ⚡ ANINDA MİLİSANİYE HIZINDA BİLDİRİM: Kullanıcının kendi özel incoming_invites alt dökümanını dinle
            firestoreListener = firestore.collection("users")
                .document(savedUid)
                .collection("incoming_invites")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    val requests = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(DirectGameRequest::class.java)
                    }.filter { it.status == "pending" }

                    for (req in requests) {
                        GameNotificationManager.showGameInviteNotification(applicationContext, req)
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
        isStarted = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
