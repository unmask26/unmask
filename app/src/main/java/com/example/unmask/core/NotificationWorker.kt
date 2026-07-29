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
