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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    LaunchedEffect(user) {
        val u = user
        if (u != null) {
            if (!hasInitialized || birthDate.isEmpty()) {
                displayName = u.displayName
                nickname = u.nickname ?: ""
                selectedGender = u.gender
                birthDate = u.birthDate
                adultPassword = u.adultPassword ?: ""
                if (u.birthDate.isNotEmpty()) {
                    hasInitialized = true
                }
            }
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
                Toast.makeText(context, "Profil başarıyla kaydedildi!", Toast.LENGTH_SHORT).show()
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
                IconButton(onClick = onProfileSaved, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.Black, modifier = Modifier.size(28.dp))
                }
                Text(text = "PROFİL DÜZENLE", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }

            // Name Field
            ProfileTextField(label = "AD SOYAD", value = displayName, onValueChange = { displayName = it }, placeholder = "Adınızı girin")

            // Nickname Field
            ProfileTextField(label = "TAKMA AD (NICKNAME)", value = nickname, onValueChange = { nickname = it }, placeholder = "Takma adınızı girin")

            // Gender Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileLabel(text = "CİNSİYET")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Erkek", "Kadın", "Diğer").forEach { gender ->
                        val isSelected = selectedGender == gender
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f).height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color.Black else Color.White)
                                .border(1.dp, if (isSelected) Color.Black else Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                .clickable { selectedGender = gender }
                        ) {
                            Text(text = gender, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color.Black)
                        }
                    }
                }
            }

            // Birth Date Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileLabel(text = "DOĞUM TARİHİ")
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
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .clickable { datePickerDialog.show() }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (birthDate.isEmpty()) "Doğum tarihinizi seçin" else birthDate,
                        fontWeight = FontWeight.Bold,
                        color = if (birthDate.isEmpty()) Color.Black.copy(alpha = 0.3f) else Color.Black
                    )
                }
            }



            // ─── ADULT ŞİFRESİ ────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color.Black.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    Text(text = "ADULT OYUN ŞİFRESİ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                }

                val isPasswordAlreadySet = remember(user) { !user?.adultPassword.isNullOrBlank() }
                OutlinedTextField(
                    value = adultPassword,
                    onValueChange = { adultPassword = it },
                    placeholder = { Text(if (isPasswordAlreadySet) "Şifre Kilitli 🔒 (Sıfırlama e-postası kullanın)" else "Şifre belirleyin (İsteğe bağlı)") },
                    singleLine = true,
                    enabled = !isPasswordAlreadySet,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        disabledTextColor = Color.Black.copy(alpha = 0.6f),
                        disabledBorderColor = Color.Black.copy(alpha = 0.1f),
                        disabledContainerColor = Color(0xFFF3F4F6),
                        focusedBorderColor = Color(0xFF7C3AED),
                        unfocusedBorderColor = Color.Black.copy(alpha = 0.15f),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // E-posta ile şifre sıfırlama bağlantısı gönderme butonu
                Button(
                    onClick = {
                        val email = repository.currentFirebaseUser?.email
                        if (email.isNullOrEmpty()) {
                            Toast.makeText(context, "E-posta adresi bulunamadı.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            try {
                                repository.sendPasswordResetEmail(email)
                                Toast.makeText(context, "📧 $email adresine şifre sıfırlama bağlantısı gönderildi!\n\nMaildeki sıfırlama bağlantısına tıklayıp yeni şifrenizi belirleyebilirsiniz. Ardından yukarıdaki alana yazıp KAYDET'e basınız.", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Sıfırlama e-postası gönderilemedi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED), contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ADULT OYUN ŞİFRESİ SIFIRLAMA E-POSTASI GÖNDER 📧",
                        fontWeight = FontWeight.Bold, fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            if (isLoading) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(
                    onClick = { doSave() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "KAYDET", fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }


    }
}

@Composable
private fun ProfileLabel(text: String) {
    Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
}

@Composable
private fun ProfileTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileLabel(text = label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
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
}


