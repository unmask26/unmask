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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unmask.data.DataRepository
import kotlinx.coroutines.launch
import java.util.Calendar

// Güvenlik soruları listesi
val SECURITY_QUESTIONS = listOf(
    "İlk öğretmeninin adı nedir?",
    "İlk evcil hayvanının ismi nedir?",
    "Hatırladığın okul numaran nedir?",
    "Annenin kızlık soyadı nedir?",
    "Doğduğun şehir neresidir?",
    "En sevdiğin çocukluk oyuncağı neydi?"
)

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

    // Security questions state
    var selectedQuestion1 by remember { mutableStateOf(SECURITY_QUESTIONS[0]) }
    var answer1 by remember { mutableStateOf("") }
    var selectedQuestion2 by remember { mutableStateOf(SECURITY_QUESTIONS[1]) }
    var answer2 by remember { mutableStateOf("") }
    var showQ1Dropdown by remember { mutableStateOf(false) }
    var showQ2Dropdown by remember { mutableStateOf(false) }

    // Adult password change via security question
    var showChangeAdultPasswordModal by remember { mutableStateOf(false) }
    var challengeQuestion by remember { mutableStateOf("") }
    var challengeAnswer by remember { mutableStateOf("") }
    var newAdultPasswordInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        val u = user
        if (u != null) {
            if (!hasInitialized || birthDate.isEmpty()) {
                displayName = u.displayName
                nickname = u.nickname ?: ""
                selectedGender = u.gender
                birthDate = u.birthDate
                adultPassword = u.adultPassword ?: ""
                val savedAnswers = u.securityAnswers
                if (savedAnswers.isNotEmpty()) {
                    val keys = savedAnswers.keys.toList()
                    if (keys.isNotEmpty()) {
                        selectedQuestion1 = keys[0]
                        answer1 = savedAnswers[keys[0]] ?: ""
                    }
                    if (keys.size > 1) {
                        selectedQuestion2 = keys[1]
                        answer2 = savedAnswers[keys[1]] ?: ""
                    }
                }
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
        if (answer1.trim().isEmpty() || answer2.trim().isEmpty()) {
            Toast.makeText(context, "Lütfen her iki güvenlik sorusunu da cevaplayın", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedQuestion1 == selectedQuestion2) {
            Toast.makeText(context, "Lütfen farklı iki güvenlik sorusu seçin", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            isLoading = true
            try {
                repository.updateProfile(displayName, nickname, selectedGender, birthDate, adultPassword)
                repository.saveSecurityAnswers(
                    mapOf(selectedQuestion1 to answer1, selectedQuestion2 to answer2)
                )
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

            // ─── GÜVENLİK SORULARI ───────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFF7C3AED).copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Text(text = "GÜVENLİK SORULARI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED).copy(alpha = 0.7f), letterSpacing = 1.sp)
                }
                Text(text = "Bu cevaplar Adult şifrenizi değiştirmek için kullanılır.", fontSize = 12.sp, color = Color.Black.copy(alpha = 0.5f))

                // Soru 1
                SecurityQuestionSelector(
                    index = 1,
                    selectedQuestion = selectedQuestion1,
                    answer = answer1,
                    allQuestions = SECURITY_QUESTIONS,
                    disabledQuestion = selectedQuestion2,
                    showDropdown = showQ1Dropdown,
                    onDropdownToggle = { showQ1Dropdown = !showQ1Dropdown },
                    onQuestionSelect = { selectedQuestion1 = it; showQ1Dropdown = false },
                    onAnswerChange = { answer1 = it }
                )

                // Soru 2
                SecurityQuestionSelector(
                    index = 2,
                    selectedQuestion = selectedQuestion2,
                    answer = answer2,
                    allQuestions = SECURITY_QUESTIONS,
                    disabledQuestion = selectedQuestion1,
                    showDropdown = showQ2Dropdown,
                    onDropdownToggle = { showQ2Dropdown = !showQ2Dropdown },
                    onQuestionSelect = { selectedQuestion2 = it; showQ2Dropdown = false },
                    onAnswerChange = { answer2 = it }
                )
            }

            // ─── ADULT ŞİFRESİ ────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color.Black.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    Text(text = "ADULT OYUN ŞİFRESİ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.4f), letterSpacing = 1.sp)
                }

                OutlinedTextField(
                    value = adultPassword,
                    onValueChange = { adultPassword = it },
                    placeholder = { Text("Şifre belirleyin (İsteğe bağlı)") },
                    singleLine = true,
                    enabled = false,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = Color.Black.copy(alpha = 0.05f),
                        disabledContainerColor = Color.White,
                        disabledTextColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Güvenlik sorusuyla şifre değiştirme butonu
                Button(
                    onClick = {
                        val storedQuestions = repository.getStoredSecurityQuestions().keys.toList()
                        if (storedQuestions.isEmpty()) {
                            Toast.makeText(context, "Önce güvenlik sorularınızı kaydedip tekrar deneyin.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        challengeQuestion = storedQuestions.random()
                        challengeAnswer = ""
                        newAdultPasswordInput = ""
                        showChangeAdultPasswordModal = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED), contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (adultPassword.isEmpty()) "ADULT ŞİFRESİ BELİRLE 🔐" else "ADULT ŞİFRESİNİ DEĞİŞTİR 🔐",
                        fontWeight = FontWeight.Bold, fontSize = 12.sp
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

        // ─── GÜVENLİK SORUSU DOĞRULAMA MODALI ─────────────────────────────────
        if (showChangeAdultPasswordModal) {
            AlertDialog(
                onDismissRequest = { showChangeAdultPasswordModal = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFF7C3AED))
                        Text("ADULT ŞİFRESİ DEĞİŞTİR", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.Black)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Güvenlik sorusu kutusu
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF3F0FF))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🔐 Güvenlik Sorusu", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED), letterSpacing = 0.5.sp)
                            Text(challengeQuestion, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        OutlinedTextField(
                            value = challengeAnswer,
                            onValueChange = { challengeAnswer = it },
                            label = { Text("Cevabınız") },
                            placeholder = { Text("Cevabınızı yazın") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7C3AED),
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
                                focusedBorderColor = Color(0xFF7C3AED),
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
                            if (challengeAnswer.isBlank()) {
                                Toast.makeText(context, "Lütfen güvenlik sorusunu cevaplayın", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (newAdultPasswordInput.isEmpty()) {
                                Toast.makeText(context, "Lütfen yeni Adult şifrenizi girin", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isVerifying = true
                            coroutineScope.launch {
                                try {
                                    val isValid = repository.verifySecurityAnswer(challengeQuestion, challengeAnswer)
                                    if (isValid) {
                                        adultPassword = newAdultPasswordInput
                                        repository.updateProfile(displayName, nickname, selectedGender, birthDate, newAdultPasswordInput)
                                        Toast.makeText(context, "Adult şifreniz başarıyla güncellendi! 🔒", Toast.LENGTH_SHORT).show()
                                        showChangeAdultPasswordModal = false
                                    } else {
                                        Toast.makeText(context, "⚠️ Güvenlik sorusu cevabı hatalı!", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Hata: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isVerifying = false
                                }
                            }
                        },
                        enabled = !isVerifying,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED), contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("DOĞRULA VE KAYDET 🔒", fontWeight = FontWeight.Black)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangeAdultPasswordModal = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("İPTAL", color = Color.Black.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp)
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityQuestionSelector(
    index: Int,
    selectedQuestion: String,
    answer: String,
    allQuestions: List<String>,
    disabledQuestion: String,
    showDropdown: Boolean,
    onDropdownToggle: () -> Unit,
    onQuestionSelect: (String) -> Unit,
    onAnswerChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Soru $index", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED).copy(alpha = 0.7f), letterSpacing = 0.5.sp)

        // Dropdown selector
        ExposedDropdownMenuBox(
            expanded = showDropdown,
            onExpandedChange = { onDropdownToggle() }
        ) {
            OutlinedTextField(
                value = selectedQuestion,
                onValueChange = {},
                readOnly = true,
                label = { Text("Soruyu seçin") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF7C3AED),
                    unfocusedBorderColor = Color.Black.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { onDropdownToggle() }
            ) {
                allQuestions.forEach { q ->
                    val isDisabled = q == disabledQuestion
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = q,
                                color = if (isDisabled) Color.Gray else Color.Black,
                                fontSize = 13.sp
                            )
                        },
                        enabled = !isDisabled,
                        onClick = { if (!isDisabled) onQuestionSelect(q) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = answer,
            onValueChange = onAnswerChange,
            label = { Text("Cevabınız") },
            placeholder = { Text("Cevabınızı yazın") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF7C3AED),
                unfocusedBorderColor = Color.Black.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
