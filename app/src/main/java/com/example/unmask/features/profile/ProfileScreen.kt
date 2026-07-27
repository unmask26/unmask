package com.example.unmask.features.profile

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unmask.data.DataRepository
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: DataRepository,
    onProfileSaved: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val user by repository.currentUser.collectAsState(initial = null)
    var hasInitialized by remember { mutableStateOf(false) }

    var displayName by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Erkek") }
    var birthDate by remember { mutableStateOf("") }
    var adultPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Adult Password Reset Modal & Verification State
    var showAdultResetModal by remember { mutableStateOf(false) }
    var verificationCodeInput by remember { mutableStateOf("") }
    var newAdultPasswordInput by remember { mutableStateOf("") }
    var isSendingCode by remember { mutableStateOf(false) }
    var isVerifyingCode by remember { mutableStateOf(false) }

    val userEmail = remember(user) {
        repository.currentFirebaseUser?.email ?: "kullanici@example.com"
    }

    LaunchedEffect(user) {
        if (user != null && !hasInitialized) {
            displayName = user?.displayName ?: ""
            nickname = user?.nickname ?: ""
            selectedGender = user?.gender ?: "Erkek"
            birthDate = user?.birthDate ?: ""
            adultPassword = user?.adultPassword ?: ""
            hasInitialized = true
        }
    }

    fun doSave() {
        if (displayName.trim().isEmpty()) {
            Toast.makeText(context, "Lütfen adınızı girin", Toast.LENGTH_SHORT).show()
            return
        }
        if (nickname.trim().isEmpty()) {
            Toast.makeText(context, "Lütfen takma adınızı girin", Toast.LENGTH_SHORT).show()
            return
        }
        if (birthDate.isEmpty()) {
            Toast.makeText(context, "Lütfen doğum tarihinizi seçin", Toast.LENGTH_SHORT).show()
            return
        }
        
        coroutineScope.launch {
            isLoading = true
            try {
                repository.updateProfile(displayName, nickname, selectedGender, birthDate, adultPassword)
                Toast.makeText(context, "Profil kaydedildi!", Toast.LENGTH_SHORT).show()
                onProfileSaved()
            } catch (e: Exception) {
                Toast.makeText(context, "Kayıt hatası: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                IconButton(
                    onClick = onProfileSaved,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Back",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "PROFİL DÜZENLE",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }

            // Name Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Name",
                        tint = Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "AD SOYAD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = { Text("Adınızı girin") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Black.copy(alpha = 0.2f),
                        unfocusedBorderColor = Color.Black.copy(alpha = 0.05f),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Nickname Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Nickname",
                        tint = Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "TAKMA AD (NICKNAME)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    placeholder = { Text("Takma adınızı girin") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Black.copy(alpha = 0.2f),
                        unfocusedBorderColor = Color.Black.copy(alpha = 0.05f),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Gender Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wc,
                        contentDescription = "Gender",
                        tint = Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "CİNSİYET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Erkek", "Kadın", "Diğer").forEach { gender ->
                        val isSelected = selectedGender == gender
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color.Black else Color.White)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Black else Color.Black.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedGender = gender }
                        ) {
                            Text(
                                text = gender,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color.Black
                            )
                        }
                    }
                }
            }

            // Birth Date Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "BirthDate",
                        tint = Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "DOĞUM TARİHİ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                }

                // Date Picker Dialog trigger
                val calendar = Calendar.getInstance()
                val datePickerDialog = DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val monthStr = if (month + 1 < 10) "0${month + 1}" else "${month + 1}"
                        val dayStr = if (dayOfMonth < 10) "0$dayOfMonth" else "$dayOfMonth"
                        birthDate = "$year-$monthStr-$dayStr"
                    },
                    calendar.get(Calendar.YEAR) - 20,
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .clickable { datePickerDialog.show() }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (birthDate.isEmpty()) "Doğum tarihinizi seçin" else birthDate,
                        fontWeight = FontWeight.Bold,
                        color = if (birthDate.isEmpty()) Color.Black.copy(alpha = 0.3f) else Color.Black
                    )
                }
            }

            // Adult Password Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Adult Password",
                        tint = Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ADULT OYUN ŞİFRESİ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                }
                
                OutlinedTextField(
                    value = adultPassword,
                    onValueChange = { adultPassword = it },
                    placeholder = { Text("Mevcut Adult şifresi") },
                    singleLine = true,
                    enabled = false, // Must be changed via Email verification code
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = Color.Black.copy(alpha = 0.05f),
                        disabledContainerColor = Color.White,
                        disabledTextColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 📩 MAİLE KOD GÖNDER VE ŞİFRE DEĞİŞTİR BUTONU
                Button(
                    onClick = {
                        isSendingCode = true
                        coroutineScope.launch {
                            try {
                                repository.sendAdultPasswordResetCode(userEmail)
                                Toast.makeText(context, "6 Haneli doğrulama kodu $userEmail adresinize gönderildi!", Toast.LENGTH_LONG).show()
                                showAdultResetModal = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Kod gönderilemedi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSendingCode = false
                            }
                        }
                    },
                    enabled = !isSendingCode,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isSendingCode) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(imageVector = Icons.Default.Mail, contentDescription = "Kod Gönder", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (adultPassword.isEmpty()) "ADULT ŞİFRESİ BELİRLE (MAİLE KOD İSTE) 📩" else "ADULT ŞİFRESİNİ DEĞİŞTİR (MAİLE KOD İSTE) 📩",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Button(
                    onClick = { doSave() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "KAYDET",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // 🔒 MAİLE GİDEN KOD VE YENİ ADULT ŞİFRE DEĞİŞTİRME MODALI
        if (showAdultResetModal) {
            AlertDialog(
                onDismissRequest = { showAdultResetModal = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Adult Lock", tint = Color(0xFFDC2626))
                        Text("ADULT ŞİFRESİ DEĞİŞTİR", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.Black)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "unmask adult şifre değişikliği talebiniz alındı. E-posta adresinize gönderilen 6 haneli kodu ve yeni Adult şifrenizi ilgili yere yazınız.",
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )

                        OutlinedTextField(
                            value = verificationCodeInput,
                            onValueChange = { if (it.length <= 6) verificationCodeInput = it },
                            label = { Text("6 Haneli Doğrulama Kodu") },
                            placeholder = { Text("Örn: 654321") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFDC2626),
                                unfocusedBorderColor = Color.Black.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newAdultPasswordInput,
                            onValueChange = { newAdultPasswordInput = it },
                            label = { Text("Yeni Adult Şifresi") },
                            placeholder = { Text("Yeni şifreniz") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFDC2626),
                                unfocusedBorderColor = Color.Black.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (verificationCodeInput.length != 6) {
                                Toast.makeText(context, "Lütfen 6 haneli doğrulama kodunu tam girin", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (newAdultPasswordInput.isEmpty()) {
                                Toast.makeText(context, "Lütfen yeni Adult şifrenizi girin", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isVerifyingCode = true
                            coroutineScope.launch {
                                try {
                                    val isValid = repository.verifyAdultPasswordResetCode(userEmail, verificationCodeInput)
                                    if (isValid) {
                                        adultPassword = newAdultPasswordInput
                                        repository.updateProfile(displayName, nickname, selectedGender, birthDate, newAdultPasswordInput)
                                        Toast.makeText(context, "Adult şifreniz başarıyla güncellendi! 🔒", Toast.LENGTH_SHORT).show()
                                        showAdultResetModal = false
                                        verificationCodeInput = ""
                                        newAdultPasswordInput = ""
                                    } else {
                                        Toast.makeText(context, "⚠️ Girdiğiniz doğrulama kodu hatalı!", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Doğrulama hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isVerifyingCode = false
                                }
                            }
                        },
                        enabled = !isVerifyingCode,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isVerifyingCode) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("DOĞRULA VE ŞİFREYİ KAYDET 🔒", fontWeight = FontWeight.Black)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAdultResetModal = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("İPTAL", color = Color.Black.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}
