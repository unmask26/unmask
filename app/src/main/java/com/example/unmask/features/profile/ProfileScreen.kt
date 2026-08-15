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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ChevronRight
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
    var spouseList by remember { mutableStateOf(listOf("")) }
    var notifyVideoReceived by remember { mutableStateOf(true) }
    var notifyGameInvite by remember { mutableStateOf(true) }
    var notifyGameOver by remember { mutableStateOf(true) }
    var notifyTurnReminder by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var showBuyCardScreen by remember { mutableStateOf(false) }

    if (showBuyCardScreen) {
        BuyCardScreen(
            repository = repository,
            onBack = { showBuyCardScreen = false }
        )
        return
    }

    LaunchedEffect(user) {
        val u = user
        if (u != null) {
            if (!hasInitialized || birthDate.isEmpty()) {
                displayName = u.displayName
                nickname = u.nickname ?: ""
                val existingSpouse = u.spouseNickname ?: ""
                val parsedSpouseList = existingSpouse.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                spouseList = if (parsedSpouseList.isNotEmpty()) parsedSpouseList else listOf("")
                selectedGender = u.gender
                birthDate = u.birthDate
                adultPassword = u.adultPassword ?: ""
                notifyVideoReceived = u.notifyVideoReceived
                notifyGameInvite = u.notifyGameInvite
                notifyGameOver = u.notifyGameOver
                notifyTurnReminder = u.notifyTurnReminder
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
        val isFutureDate = try {
            val parts = birthDate.split("-")
            val y = parts[0].toInt()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            y > currentYear
        } catch (_: Exception) { false }

        if (isFutureDate) {
            Toast.makeText(context, "Gelecek bir tarih doğum tarihi olarak seçilemez", Toast.LENGTH_SHORT).show()
            return
        }
        val combinedSpouse = spouseList.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        coroutineScope.launch {
            isLoading = true
            try {
                repository.updateProfile(
                    displayName = displayName,
                    nickname = nickname,
                    gender = selectedGender,
                    birthDate = birthDate,
                    adultPassword = adultPassword,
                    notifyVideoReceived = notifyVideoReceived,
                    notifyGameInvite = notifyGameInvite,
                    notifyGameOver = notifyGameOver,
                    notifyTurnReminder = notifyTurnReminder,
                    spouseNickname = combinedSpouse
                )
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

            // EŞ / Flört Field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(text = "EŞ 💍", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = { spouseList = spouseList + "" },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFEC4899), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Eş Ekle",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                spouseList.forEachIndexed { index, spouseVal ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = spouseVal,
                            onValueChange = { newVal ->
                                val newList = spouseList.toMutableList()
                                newList[index] = newVal
                                spouseList = newList
                            },
                            placeholder = { Text("flört kullanıcı adını gir", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFEC4899),
                                unfocusedBorderColor = Color.Black.copy(alpha = 0.08f),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        )
                        if (index == spouseList.size - 1) {
                            IconButton(
                                onClick = { spouseList = spouseList + "" },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFEC4899).copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Eş Ekle",
                                    tint = Color(0xFFEC4899),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (spouseList.size > 1) {
                            IconButton(
                                onClick = {
                                    val newList = spouseList.toMutableList()
                                    newList.removeAt(index)
                                    spouseList = newList
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Red.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Sil",
                                    tint = Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

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
                var initYear = calendar.get(Calendar.YEAR) - 20
                var initMonth = calendar.get(Calendar.MONTH)
                var initDay = calendar.get(Calendar.DAY_OF_MONTH)

                if (birthDate.isNotEmpty()) {
                    try {
                        val parts = birthDate.split("-")
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
                        val monthStr = if (month + 1 < 10) "0${month + 1}" else "${month + 1}"
                        val dayStr = if (dayOfMonth < 10) "0$dayOfMonth" else "$dayOfMonth"
                        birthDate = "$year-$monthStr-$dayStr"
                    },
                    initYear,
                    initMonth,
                    initDay
                ).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                    datePicker.minDate = Calendar.getInstance().apply { add(Calendar.YEAR, -100) }.timeInMillis
                }
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

            // ─── BİLDİRİM AYARLARI ───────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "BİLDİRİM AYARLARI 🔔",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        NotificationToggleItem(
                            title = "Video Geldi 🎬",
                            description = "Online oyun sırasında oyundan çıkış yapılırsa rakip video gönderince bildirim al",
                            checked = notifyVideoReceived,
                            onCheckedChange = { notifyVideoReceived = it }
                        )

                        HorizontalDivider(color = Color.Black.copy(alpha = 0.06f))

                        NotificationToggleItem(
                            title = "Oyun İsteği 🎮",
                            description = "Uygulama açıkken veya kapalıyken oyun isteği gelince bildirim al",
                            checked = notifyGameInvite,
                            onCheckedChange = { notifyGameInvite = it }
                        )

                        HorizontalDivider(color = Color.Black.copy(alpha = 0.06f))

                        NotificationToggleItem(
                            title = "Oyun Bitti 🏆",
                            description = "Rakip oyunu bitirince (uygulama kapalıyken bile) bildirim al",
                            checked = notifyGameOver,
                            onCheckedChange = { notifyGameOver = it }
                        )

                        HorizontalDivider(color = Color.Black.copy(alpha = 0.06f))

                        NotificationToggleItem(
                            title = "Sıra Sende ⏰",
                            description = "Rakip video gönderdiğinde kullanıcı gecikirse her 3 dakikada bir oyunu bitirene kadar bildirim al",
                            checked = notifyTurnReminder,
                            onCheckedChange = { notifyTurnReminder = it }
                        )
                    }
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
private fun NotificationToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color.Black.copy(alpha = 0.5f),
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF8B5CF6),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.LightGray.copy(alpha = 0.5f)
            )
        )
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


