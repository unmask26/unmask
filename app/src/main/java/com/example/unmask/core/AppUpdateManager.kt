package com.example.unmask.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val version: String,
    val notes: String,
    val downloadUrl: String
)

object AppUpdateManager {

    private const val GITHUB_RELEASE_API = "https://api.github.com/repos/unmask26/unmask/releases/latest"

    // Session flags to prevent duplicate checks and duplicate dialogs
    var hasCheckedThisSession: Boolean = false
    var isUpdateFlowActive: Boolean = false

    suspend fun checkForUpdate(context: Context): AppReleaseInfo? = withContext(Dispatchers.IO) {
        if (hasCheckedThisSession && !isUpdateFlowActive) return@withContext null
        hasCheckedThisSession = true

        try {
            val url = URL(GITHUB_RELEASE_API)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "UNMASK-Android-App")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonStr)

                val tagName = jsonObj.optString("tag_name", "").removePrefix("v").trim()
                val body = jsonObj.optString("body", "")

                // Detect the device's primary ABI to pick the matching split APK
                val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                val abiLabel = when {
                    deviceAbi.contains("arm64") -> "arm64v8a"
                    deviceAbi.contains("armeabi") -> "armeabiv7a"
                    else -> "arm64v8a"
                }

                var apkUrl = ""
                val assets = jsonObj.optJSONArray("assets")
                if (assets != null) {
                    // First try: find ABI-specific APK
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true) && name.contains(abiLabel, ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                    // Fallback: grab any .apk
                    if (apkUrl.isEmpty()) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }
                }

                val packageInfo = try {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } catch (_: Exception) { null }
                val currentVersion = (packageInfo?.versionName ?: "1.0.0").removePrefix("v").trim()

                if (isNewerVersion(currentVersion, tagName) && apkUrl.isNotEmpty()) {
                    return@withContext AppReleaseInfo(
                        version = tagName,
                        notes = body,
                        downloadUrl = apkUrl
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /** Returns cached APK file if it exists and is non-empty. */
    fun getCachedApk(context: Context): File? {
        val f = File(context.cacheDir, "unmask_update.apk")
        return if (f.exists() && f.length() > 0) f else null
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "UNMASK-Android-App")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            val fileLength = connection.contentLength
            val apkFile = File(context.cacheDir, "unmask_update.apk")
            if (apkFile.exists()) apkFile.delete()

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) { onProgress(100) }
            apkFile
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "İndirme hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            null
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.provider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Yükleme başlatılamadı: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /** Returns true if the app has permission to install unknown packages (API 26+). */
    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    /** Opens system Settings page to grant "Install unknown apps" permission for this app. */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        try {
            val currParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val lateParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(currParts.size, lateParts.size)
            for (i in 0 until maxLen) {
                val c = currParts.getOrElse(i) { 0 }
                val l = lateParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (_: Exception) {}
        return false
    }
}

// ------------------------------------------------------------------
// STATES
// ------------------------------------------------------------------
private enum class UpdateStep {
    IDLE,
    NEEDS_UPDATE,
    NEEDS_PERM,
    DOWNLOADING
}

/**
 * In-App Auto Update Dialog composable.
 * Guarantees single-shot execution per app session, handling Unknown Sources permission seamlessly.
 */
@Composable
fun AutoUpdateCheckerOverlay() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var releaseInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var step by remember { mutableStateOf(UpdateStep.IDLE) }
    var downloadProgress by remember { mutableStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }

    // Check for update ONCE per launch session
    LaunchedEffect(Unit) {
        if (!AppUpdateManager.isUpdateFlowActive) {
            val info = AppUpdateManager.checkForUpdate(context)
            if (info != null) {
                releaseInfo = info
                step = UpdateStep.NEEDS_UPDATE
                showDialog = true
                AppUpdateManager.isUpdateFlowActive = true
            }
        }
    }

    // Handle return from Settings if user was asking for install permission
    LaunchedEffect(showDialog, step) {
        if (showDialog && step == UpdateStep.NEEDS_PERM) {
            if (AppUpdateManager.canInstall(context)) {
                // Permission granted! Start downloading or installing cached APK
                val cached = AppUpdateManager.getCachedApk(context)
                if (cached != null) {
                    showDialog = false
                    AppUpdateManager.isUpdateFlowActive = false
                    AppUpdateManager.installApk(context, cached)
                } else {
                    step = UpdateStep.DOWNLOADING
                    coroutineScope.launch {
                        val file = AppUpdateManager.downloadApk(
                            context = context,
                            downloadUrl = releaseInfo!!.downloadUrl,
                            onProgress = { prog -> downloadProgress = prog }
                        )
                        showDialog = false
                        AppUpdateManager.isUpdateFlowActive = false
                        if (file != null) {
                            AppUpdateManager.installApk(context, file)
                        }
                    }
                }
            }
        }
    }

    if (!showDialog || releaseInfo == null) return

    AlertDialog(
        onDismissRequest = {
            if (step != UpdateStep.DOWNLOADING) {
                showDialog = false
                AppUpdateManager.isUpdateFlowActive = false
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = "Güncelleme",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "🚀 YENİ GÜNCELLEME MEVCUT! (v${releaseInfo!!.version})",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (step) {
                    UpdateStep.NEEDS_UPDATE -> {
                        Text(
                            text = "UNMASK'in en son sürümü yayınlandı. Yeni özellikleri kullanmak için güncelleyin.",
                            fontSize = 13.sp,
                            color = Color.Black.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                    UpdateStep.NEEDS_PERM -> {
                        Text(
                            text = "⚙️ İzin Verildikten Sonra Otomatik Yüklenecek\n\nUNMASK için 'Bilinmeyen kaynaklardan yükle' iznini açıp geri dönün.",
                            fontSize = 13.sp,
                            color = Color(0xFFB45309),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    UpdateStep.DOWNLOADING -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            color = Color(0xFF10B981),
                            trackColor = Color.Black.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Text(
                            text = "İndiriliyor: %$downloadProgress",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            if (step == UpdateStep.NEEDS_UPDATE) {
                Button(
                    onClick = {
                        if (!AppUpdateManager.canInstall(context)) {
                            step = UpdateStep.NEEDS_PERM
                            AppUpdateManager.requestInstallPermission(context)
                        } else {
                            step = UpdateStep.DOWNLOADING
                            coroutineScope.launch {
                                val file = AppUpdateManager.downloadApk(
                                    context = context,
                                    downloadUrl = releaseInfo!!.downloadUrl,
                                    onProgress = { prog -> downloadProgress = prog }
                                )
                                showDialog = false
                                AppUpdateManager.isUpdateFlowActive = false
                                if (file != null) {
                                    AppUpdateManager.installApk(context, file)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ŞİMDİ GÜNCELLE", fontWeight = FontWeight.Black, color = Color.White)
                }
            }
            if (step == UpdateStep.NEEDS_PERM) {
                Button(
                    onClick = {
                        if (AppUpdateManager.canInstall(context)) {
                            val cached = AppUpdateManager.getCachedApk(context)
                            if (cached != null) {
                                showDialog = false
                                AppUpdateManager.isUpdateFlowActive = false
                                AppUpdateManager.installApk(context, cached)
                            } else {
                                step = UpdateStep.DOWNLOADING
                                coroutineScope.launch {
                                    val file = AppUpdateManager.downloadApk(
                                        context = context,
                                        downloadUrl = releaseInfo!!.downloadUrl,
                                        onProgress = { prog -> downloadProgress = prog }
                                    )
                                    showDialog = false
                                    AppUpdateManager.isUpdateFlowActive = false
                                    if (file != null) {
                                        AppUpdateManager.installApk(context, file)
                                    }
                                }
                            }
                        } else {
                            AppUpdateManager.requestInstallPermission(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("İZİN VERDİM, DEVAM ET", fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        },
        dismissButton = {
            if (step == UpdateStep.NEEDS_UPDATE || step == UpdateStep.NEEDS_PERM) {
                TextButton(onClick = {
                    showDialog = false
                    AppUpdateManager.isUpdateFlowActive = false
                }) {
                    Text("SONRA", color = Color.Black.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}
