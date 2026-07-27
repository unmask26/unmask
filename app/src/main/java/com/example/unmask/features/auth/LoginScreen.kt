package com.example.unmask.features.auth

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unmask.data.DataRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

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

    // Text bounce animation for "UNMASK" title
    val infiniteTransition = rememberInfiniteTransition(label = "title_bounce")
    val bounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

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
                    Toast.makeText(context, "Giriş hatası: ${e.message}", Toast.LENGTH_LONG).show()
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
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(0, bounceY.dp.roundToPx()) }
                    .padding(8.dp)
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
                                // Default Web Client ID is linked to messagingSenderId in config: 242033696510
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestIdToken("242033696510-ph3anvcea6j2f6tsaggf4oc1scuuipj0.apps.googleusercontent.com")
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
            }
        }

        // Bottom Trademark Footer
        Text(
            text = "© 2026 UNMASK PROJECT",
            color = Color.White.copy(alpha = 0.2f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}
}
