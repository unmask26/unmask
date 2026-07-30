package com.example.unmask.core

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.unmask.core.theme.UNMASKTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    GameNotificationManager.createNotificationChannel(this)
    FCMTokenManager.initAndSyncFCMToken(this)
    NotificationWorker.scheduleBackgroundWorker(this)

    try {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(1001)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel("game_service_bg_channel")
        }
        val stopIntent = Intent().apply {
            component = android.content.ComponentName(packageName, "com.example.unmask.core.GameNotificationService")
        }
        stopService(stopIntent)
    } catch (_: Exception) {}

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    enableEdgeToEdge()
    setContent {
      UNMASKTheme { 
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
            MainNavigation() 
            com.example.unmask.update.UpdateCheckDialog(repoOwner = "unmask26", repoName = "unmask")
        } 
      }
    }
  }

  override fun onStop() {
      super.onStop()
      NotificationWorker.triggerImmediateCheck(this)
  }
}
