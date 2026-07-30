package com.example.unmask.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            try {
                NotificationWorker.scheduleBackgroundWorker(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
