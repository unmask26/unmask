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
            val requestId = data["requestId"] ?: System.currentTimeMillis().toString()
            val senderId = data["senderId"] ?: ""
            val senderNickname = data["senderNickname"] ?: "Bir oyuncu"
            val selectedCategory = data["selectedCategory"] ?: ""

            val request = DirectGameRequest(
                id = requestId,
                senderId = senderId,
                senderNickname = senderNickname,
                selectedCategory = selectedCategory
            )

            GameNotificationManager.showGameInviteNotification(applicationContext, request)
        } else if (remoteMessage.notification != null) {
            val title = remoteMessage.notification?.title ?: "🎮 OYUN İSTEĞİ GELDİ!"
            val body = remoteMessage.notification?.body ?: "Sizinle oyun oynamak isteyen biri var!"

            val request = DirectGameRequest(
                id = System.currentTimeMillis().toString(),
                senderNickname = title.replace("🎮 OYUN İSTEĞİ GELDİ!", "").trim(),
                selectedCategory = body
            )

            GameNotificationManager.showGameInviteNotification(applicationContext, request)
        }
    }
}
