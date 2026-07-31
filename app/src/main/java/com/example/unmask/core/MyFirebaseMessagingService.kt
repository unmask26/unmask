package com.example.unmask.core

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.unmask.data.DirectGameRequest

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FCMTokenManager.saveTokenToFirestore(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val type = data["type"] ?: ""
            val senderNickname = data["senderNickname"] ?: "Bir oyuncu"
            val sessionId = data["sessionId"] ?: data["requestId"] ?: System.currentTimeMillis().toString()

            if (type == "online_game_video") {
                // Eğer oyun ekranı aktifse (uygulama açık ve oyun sekmesindeyse) bildirim olmasın
                if (GameScreenTracker.isGameScreenActive) return

                GameNotificationManager.showVideoReceivedNotification(
                    applicationContext,
                    senderNickname = senderNickname,
                    notificationIdKey = sessionId
                )
                return
            }

            val requestId = data["requestId"] ?: System.currentTimeMillis().toString()
            val senderId = data["senderId"] ?: ""
            val selectedCategory = data["selectedCategory"] ?: ""

            val request = DirectGameRequest(
                id = requestId,
                senderId = senderId,
                senderNickname = senderNickname,
                selectedCategory = selectedCategory
            )

            GameNotificationManager.showGameInviteNotification(applicationContext, request)
        } else if (remoteMessage.notification != null) {
            val title = remoteMessage.notification?.title ?: ""
            val body = remoteMessage.notification?.body ?: ""

            if (body.contains("video gönderdi", ignoreCase = true)) {
                if (GameScreenTracker.isGameScreenActive) return

                val senderNickname = if (body.startsWith("@")) {
                    body.substringAfter("@").substringBefore(" ").trim()
                } else {
                    "Rakip"
                }

                GameNotificationManager.showVideoReceivedNotification(
                    applicationContext,
                    senderNickname = senderNickname,
                    notificationIdKey = System.currentTimeMillis().toString()
                )
                return
            }

            val request = DirectGameRequest(
                id = System.currentTimeMillis().toString(),
                senderNickname = title.replace("🎮 OYUN İSTEĞİ GELDİ!", "").trim(),
                selectedCategory = body
            )

            GameNotificationManager.showGameInviteNotification(applicationContext, request)
        }
    }
}
