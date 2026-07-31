package com.example.unmask.features.auth

import android.app.Activity
import android.app.DatePickerDialog
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unmask.data.DataRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@Composable
fun LoginScreen(
    repository: DataRepository,
    onLoginSuccess: () -> Unit,
    onLoginAlreadyCompleted: () -> Unit
) {
    val currentUser by repository.currentUser.collectAsState(initial = null)
    val currentFirebaseUser = remember { repository.currentFirebaseUser }
    var isCheckingSession by remember { mutableStateOf(currentFirebaseUser != null) }

    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            isCheckingSession = false
            if (user.birthDate.isNotEmpty()) {
                onLoginAlreadyCompleted()
            } else {
                onLoginSuccess()
            }
        } else {
            if (repository.currentFirebaseUser == null) {
                isCheckingSession = false
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }

    // Email registration / login states
    var showEmailDialog by remember { mutableStateOf(false) }
    var isRegisterTab by remember { mutableStateOf(true) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var birthDateInput by remember { mutableStateOf("") }

    // Google Sign-In Activity Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            coroutineScope.launch {
                isLoading = true
                try {
                    repository.loginWithCredential(credential)
                    Toast.makeText(context, "Google Girişi Başarılı!", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                } catch (e: Exception) {
                    Log.e("GoogleSignIn", "Firebase Auth login error: ${e.message}", e)
                    val msg = e.message ?: ""
                    if (msg.contains("disabled", ignoreCase = true) || msg.contains("provider", ignoreCase = true)) {
                        Toast.makeText(context, "Firebase Hatası: Lütfen Firebase Konsolunda Authentication > Sign-in method > Google sağlayıcısını Etkinleştirin (Enable).", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Giriş hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isLoading = false
                }
            }
        } catch (e: ApiException) {
            val statusCode = e.statusCode
            val errorMessage = when (statusCode) {
                10 -> "Geliştirici Hatası (Developer Error 10). Lütfen Firebase'de SHA-1 anahtarını ve Web Client ID'yi kontrol edin."
                12501 -> "Giriş kullanıcı tarafından iptal edildi (12501)."
                12500 -> "Giriş başarısız oldu (12500). Google Play Hizmetlerini veya internet bağlantısını kontrol edin."
                else -> "Google Giriş Hatası (${statusCode}): ${e.localizedMessage}"
            }
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    if (isCheckingSession) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // UNMASK Title Box with border effect
                Box(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "UNMASK",
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-4).sp
                    )
                    // Diagonal overlay box to simulate retro border
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(x = 8.dp, y = 8.dp)
                            .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))

                // Action Buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        // Google Sign In Button
                        Button(
                            onClick = {
                                isLoading = true
                                try {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestIdToken("908513940709-beipp9gvcnrrbnat1u55gh22eh7u7bfj.apps.googleusercontent.com")
                                        .requestEmail()
                                        .build()
                                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                } catch (e: Exception) {
                                    isLoading = false
                                    Toast.makeText(context, "Google Play Hizmetleri Hatası: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = "Google Login",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "GOOGLE İLE GİRİŞ YAP",
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                fontSize = 15.sp
                            )
                        }

                        // E-posta ile Kayıt Ol / Giriş Yap Butonu
                        OutlinedButton(
                            onClick = { showEmailDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email Register",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "E-POSTA İLE KAYIT OL / GİRİŞ YAP 📧",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Devam ederek gizlilik sözleşmesini\nkabul etmiş olursunuz.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "© 2026 UNMASK PROJECT",
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            // ─── E-POSTA KAYIT / GİRİŞ MODALI ─────────────────────────────────────
            if (showEmailDialog) {
                AlertDialog(
                    onDismissRequest = { showEmailDialog = false },
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { isRegisterTab = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRegisterTab) Color.Black else Color.Gray.copy(alpha = 0.2f),
                                        contentColor = if (isRegisterTab) Color.White else Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("KAYIT OL ✍️", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { isRegisterTab = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isRegisterTab) Color.Black else Color.Gray.copy(alpha = 0.2f),
                                        contentColor = if (!isRegisterTab) Color.White else Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("GİRİŞ YAP 🔐", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (isRegisterTab) {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it },
                                    label = { Text("Ad Soyad") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("E-posta Adresi") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Şifre (En az 6 karakter)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (isRegisterTab) {
                                val calendar = Calendar.getInstance()
                                var initYear = calendar.get(Calendar.YEAR) - 20
                                var initMonth = calendar.get(Calendar.MONTH)
                                var initDay = calendar.get(Calendar.DAY_OF_MONTH)

                                if (birthDateInput.isNotEmpty()) {
                                    try {
                                        val parts = birthDateInput.split("-")
                                        if (parts.size == 3) {
                                            initYear = parts[0].toInt()
                                            initMonth = parts[1].toInt() - 1
                                            initDay = parts[2].toInt()
                                        }
                                    } catch (_: Exception) {}
                                }

                                val datePickerDialog = DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        birthDateInput = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                    },
                                    initYear, initMonth, initDay
                                ).apply {
                                    datePicker.maxDate = System.currentTimeMillis()
                                    datePicker.minDate = Calendar.getInstance().apply { add(Calendar.YEAR, -100) }.timeInMillis
                                }

                                OutlinedTextField(
                                    value = birthDateInput,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Doğum Tarihi") },
                                    placeholder = { Text("YYYY-AA-GG seçiniz") },
                                    trailingIcon = {
                                        IconButton(onClick = { datePickerDialog.show() }) {
                                            Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Tarih Seç")
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { datePickerDialog.show() }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (emailInput.isBlank() || passwordInput.isBlank()) {
                                    Toast.makeText(context, "Lütfen tüm alanları doldurun.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (isRegisterTab && (nameInput.isBlank() || birthDateInput.isBlank())) {
                                    Toast.makeText(context, "Lütfen adınızı ve doğum tarihinizi girin.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isLoading = true
                                showEmailDialog = false
                                coroutineScope.launch {
                                    try {
                                        if (isRegisterTab) {
                                            repository.registerWithEmail(
                                                email = emailInput.trim(),
                                                password = passwordInput,
                                                displayName = nameInput.trim(),
                                                birthDate = birthDateInput
                                            )
                                            Toast.makeText(context, "Kayıt Başarılı!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            repository.loginWithEmail(
                                                email = emailInput.trim(),
                                                password = passwordInput
                                            )
                                            Toast.makeText(context, "Giriş Başarılı!", Toast.LENGTH_SHORT).show()
                                        }
                                        onLoginSuccess()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Hata: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(if (isRegisterTab) "KAYDOL VE BAŞLA 🚀" else "GİRİŞ YAP 🚀", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEmailDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("İPTAL", color = Color.Black.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}
