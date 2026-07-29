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
        startServiceForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isStarted) {
            isStarted = true
            startServiceForeground()
            startListeningForRequests()
        }
        return START_STICKY
    }

    private fun startServiceForeground() {
        val channelId = "game_service_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Oyun Bildirim Servisi",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Arka planda davet dinleme servisi"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("UNMASK Oyun Servisi")
            .setContentText("Davetler arka planda dinleniyor...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        try {
            startForeground(999, notification)
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
            firestoreListener = firestore.collection("direct_game_requests")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    val requests = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(DirectGameRequest::class.java)
                    }.filter { req ->
                        val reqRecId = req.receiverId.removePrefix("@").trim()
                        val reqRecNick = req.receiverNickname.removePrefix("@").trim()

                        val isForMe = (savedUid.isNotEmpty() && reqRecId.equals(savedUid, ignoreCase = true)) ||
                                (savedNickname.isNotEmpty() && (reqRecId.equals(savedNickname, ignoreCase = true) || reqRecNick.equals(savedNickname, ignoreCase = true)))

                        isForMe && req.status == "pending"
                    }

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
