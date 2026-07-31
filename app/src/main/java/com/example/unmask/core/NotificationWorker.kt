package com.example.unmask.core

import android.content.Context
import androidx.work.*
import com.example.unmask.data.DirectGameRequest
import com.example.unmask.data.FirebaseConfig
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("unmask_prefs", Context.MODE_PRIVATE)
        val savedUid = prefs.getString("current_user_uid", null) ?: return Result.success()
        val savedNickname = prefs.getString("current_user_nickname", null) ?: ""

        try {
            val firestore = FirebaseConfig.firestore
            val docs = firestore.collection("direct_game_requests")
                .whereEqualTo("status", "pending")
                .get()
                .await()

            val cleanUid = savedUid.trim()
            val cleanNick = savedNickname.removePrefix("@").trim()

            val requests = docs.documents.mapNotNull { it.toObject(DirectGameRequest::class.java) }
                .filter { req ->
                    val reqRecId = req.receiverId.removePrefix("@").trim()
                    val reqRecNick = req.receiverNickname.removePrefix("@").trim()

                    val isForMe = (cleanUid.isNotEmpty() && reqRecId.equals(cleanUid, ignoreCase = true)) ||
                            (cleanNick.isNotEmpty() && (reqRecId.equals(cleanNick, ignoreCase = true) || reqRecNick.equals(cleanNick, ignoreCase = true)))
                    isForMe
                }

            for (req in requests) {
                GameNotificationManager.showGameInviteNotification(applicationContext, req)
            }

            // 2. Online seanslarda gelen izlenmemiş videoları kontrol et (Uygulama kapalıyken güvence mekanizması)
            val s1 = firestore.collection("online_sessions").whereEqualTo("user1Id", savedUid).get().await()
            val s2 = firestore.collection("online_sessions").whereEqualTo("user2Id", savedUid).get().await()
            val allSessions = (s1.documents + s2.documents).mapNotNull { it.toObject(com.example.unmask.data.OnlineSession::class.java) }

            for (session in allSessions) {
                val videoUrl = session.videoUrl
                val senderId = session.videoSenderId
                val isWatched = session.videoWatchedByReceiver

                if (videoUrl.isNotEmpty() && senderId.isNotEmpty() && senderId != savedUid && !isWatched) {
                    val senderName = if (session.user1Id == savedUid) session.user2Name else session.user1Name
                    val uniqueKey = "${session.id}_${videoUrl.hashCode()}"
                    GameNotificationManager.showVideoReceivedNotification(
                        applicationContext,
                        senderNickname = senderName,
                        notificationIdKey = uniqueKey
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Result.success()
    }

    companion object {
        fun scheduleBackgroundWorker(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "game_invite_notification_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
        }

        fun triggerImmediateCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeWorkRequest)
        }
    }
}
