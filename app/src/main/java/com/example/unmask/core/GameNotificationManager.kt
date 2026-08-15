package com.example.unmask.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.unmask.data.DirectGameRequest

object GameNotificationManager {

    private const val CHANNEL_ID = "game_invitations_channel"
    private const val CHANNEL_NAME = "Oyun Davetleri"
    private val notifiedRequestIds = mutableSetOf<String>()

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gelen oyun daveti sistem bildirimleri"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                enableLights(true)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showGameInviteNotification(context: Context, request: DirectGameRequest) {
        // Zaten bildirimi gösterildiyse tekrar gösterme
        if (notifiedRequestIds.contains(request.id)) return
        notifiedRequestIds.add(request.id)

        createNotificationChannel(context)

        val catName = when (request.selectedCategory.lowercase()) {
            "flort" -> "FLÖRT"
            "iliskiler" -> "İLİŞKİLER"
            "adrenalin" -> "ADRENALİN"
            "bilgi" -> "BİLGİ"
            "aktuel" -> "AKTÜEL"
            "hatiralar" -> "HATIRALAR"
            "fanteziler" -> "FANTEZİLER"
            "adult" -> "ADULT (+18)"
            "softhub" -> "SOFTHUB"
            else -> request.selectedCategory.takeIf { it.isNotBlank() }?.uppercase()
        }

        val sender = request.senderNickname.ifEmpty { "Bir oyuncu" }
        val title = "🎮 OYUN İSTEĞİ GELDİ!"
        val content = if (!catName.isNullOrBlank()) {
            "@$sender size $catName lobisinde oyun daveti gönderdi!"
        } else {
            "@$sender sizinle oyun oynamak istiyor!"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "gecmis")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            request.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 400, 200, 400))

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(request.id.hashCode(), builder.build())
    }

    fun showVideoReceivedNotification(context: Context, senderNickname: String, notificationIdKey: String = "") {
        val uniqueKey = notificationIdKey.ifEmpty { System.currentTimeMillis().toString() }
        if (notifiedRequestIds.contains(uniqueKey)) return
        notifiedRequestIds.add(uniqueKey)

        createNotificationChannel(context)

        val sender = senderNickname.ifEmpty { "Rakip" }
        val title = "📹 YENİ VİDEO!"
        val content = "@$sender size bir video gönderdi"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "oyun")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            uniqueKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 400, 200, 400))

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(uniqueKey.hashCode(), builder.build())
    }

    fun showGameOverNotification(context: Context, body: String) {
        createNotificationChannel(context)
        val title = "🏆 OYUN BİTTİ!"
        val content = body.ifEmpty { "Online oyun tamamlandı!" }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "gecmis")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            content.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(content.hashCode(), builder.build())
    }

    fun showTurnReminderNotification(context: Context, body: String) {
        createNotificationChannel(context)
        val title = "⏰ SIRA SENDE!"
        val content = body.ifEmpty { "Rakibiniz sizden hamle bekliyor! Oyuna dönün." }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "oyun")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            content.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(content.hashCode(), builder.build())
    }
}

