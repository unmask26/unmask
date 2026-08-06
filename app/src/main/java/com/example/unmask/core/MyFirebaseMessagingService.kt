package com.example.unmask.core

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.unmask.data.DirectGameRequest

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FCMTokenManager.saveTokenToFirestore(token, applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        GameNotificationManager.createNotificationChannel(applicationContext)

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val title = notification?.title ?: data["title"] ?: ""
        val body = notification?.body ?: data["body"] ?: ""
        val type = data["type"] ?: ""

        val senderNickname = data["senderNickname"] ?: if (body.startsWith("@")) {
            body.substringAfter("@").substringBefore(" ").trim()
        } else {
            "Rakip"
        }
        val sessionId = data["sessionId"] ?: data["requestId"] ?: System.currentTimeMillis().toString()

        if (type == "online_game_video" || body.contains("video gönderdi", ignoreCase = true)) {
            if (GameScreenTracker.isGameScreenActive) return

            GameNotificationManager.showVideoReceivedNotification(
                applicationContext,
                senderNickname = senderNickname,
                notificationIdKey = sessionId
            )
            return
        }

        if (type == "game_over" || body.contains("oyun tamamlandı", ignoreCase = true)) {
            GameNotificationManager.showGameOverNotification(applicationContext, body)
            return
        }

        if (type == "turn_reminder" || title.contains("SIRA SENDE", ignoreCase = true)) {
            if (GameScreenTracker.isGameScreenActive) return
            GameNotificationManager.showTurnReminderNotification(applicationContext, body)
            return
        }

        // Game Invite
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
    }
}
