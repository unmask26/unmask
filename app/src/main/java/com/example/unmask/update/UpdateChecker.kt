package com.example.unmask.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionName: String, val downloadUrl: String)

@Composable
fun UpdateCheckDialog(repoOwner: String, repoName: String) {
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name")
                    val currentVersion = "v" + context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    
                    if (tagName != currentVersion) {
                        val assets = json.getJSONArray("assets")
                        if (assets.length() > 0) {
                            val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
                            updateInfo = UpdateInfo(tagName, downloadUrl)
                            showDialog = true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Yeni Güncelleme Mevcut") },
            text = { Text("Uygulamanın yeni bir sürümü (${updateInfo!!.versionName}) bulundu. İndirip kurmak ister misiniz?") },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    downloadAndInstallUpdate(context, updateInfo!!.downloadUrl, "app-update.apk")
                }) {
                    Text("İndir ve Kur")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Daha Sonra")
                }
            }
        )
    }
}

fun downloadAndInstallUpdate(context: Context, url: String, fileName: String) {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = Uri.parse(url)
    val request = DownloadManager.Request(uri)
        .setTitle("Uygulama Güncelleniyor")
        .setDescription("Yeni sürüm indiriliyor...")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

    val downloadId = downloadManager.enqueue(request)

    val onComplete = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == id) {
                installApk(context, fileName)
                context.unregisterReceiver(this)
            }
        }
    }
    
    // For Android 13+ you may need to specify RECEIVER_EXPORTED / RECEIVER_NOT_EXPORTED
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
    } else {
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
    
    Toast.makeText(context, "İndirme başladı...", Toast.LENGTH_SHORT).show()
}

fun installApk(context: Context, fileName: String) {
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
    if (file.exists()) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Yükleme başlatılamadı", Toast.LENGTH_SHORT).show()
        }
    }
}
