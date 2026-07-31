package com.example.unmask.features.profile

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unmask.data.DataRepository
import com.example.unmask.data.FirebaseConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyCardScreen(
    repository: DataRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by repository.currentUser.collectAsState(initial = null)

    var fullName by remember(currentUser) { mutableStateOf(currentUser?.displayName ?: "") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Load local jfif image from assets
    val cardImageBitmap = remember {
        try {
            val inputStream = context.assets.open("fiziki_kart_buy.jfif")
            android.graphics.BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "FİZİKİ KART SATIN AL 🛒",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Geri",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF9F9F9)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Ürün Görseli (Gemini Generated Image)
            if (cardImageBitmap != null) {
                Image(
                    bitmap = cardImageBitmap,
                    contentDescription = "UNMASK Fiziki Kart Oyunu",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(2.dp, Color(0xFFFACC15), RoundedCornerShape(20.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎴 UNMASK FİZİKİ KART OYUNU",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
            }

            // 2. Açıklama Kutusu
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFACC15)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🔥 Sıkıcı Sorulara Son: Oyun Asla Bitmiyor!",
                        color = Color(0xFFFACC15),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "\"Klasik Doğruluk mu Cesaret mi?\" sorularından ezberlediklerinizden bıktınız mı? UNMASK Fiziki Kart ile tanışın!",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Fiziki kart kalitesini akıllı algoritmamızla birleştirdik. Kartın arkasındaki QR kodu okut, uygulamadaki sonsuz soru algoritması sayesinde her seferinde yepyeni, sürpriz ve kışkırtıcı sorularla karşılaş!",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "✨ Sonsuz İçerik: Tekrarlayan soru yok, oyun keyfi asla bitmez!",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "🌐 İki Dünya Bir Arada: Kartları masaya dizmenin heyecanı, dijitalin dinamizmiyle buluşuyor.",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "🎉 Partilerin Vazgeçilmezi: Arkadaş ortamlarını, ev partilerini ve buluşmaları unutulmaz kılmak için tasarlandı.",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🚀 Kutuyu aç, QR'ı tara, cesaretini kanıtla!",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }

            // 3. Sipariş & Kişi Bilgileri Formu
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "📦 TESLİMAT BİLGİLERİ",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color.Black
                    )

                    // Kişi Bilgisi (Ad Soyad)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Kişi Bilgisi (Ad Soyad)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.6f))
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            placeholder = { Text("Örn: Ahmet Yılmaz") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Telefon Numarası
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Telefon Numarası", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.6f))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            placeholder = { Text("Örn: 0555 123 45 67") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Adres
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Teslimat Adresi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.6f))
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            placeholder = { Text("Açık adresiniz, Mahalle, Cadde, Sokak, İlçe/İl") },
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                            minLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. En Altta Satın Al Butonu
            if (isSubmitting) {
                CircularProgressIndicator(
                    color = Color(0xFF10B981),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Button(
                    onClick = {
                        if (fullName.trim().isEmpty()) {
                            Toast.makeText(context, "Lütfen kişi bilgisini (Ad Soyad) girin", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (phone.trim().isEmpty()) {
                            Toast.makeText(context, "Lütfen telefon numaranızı girin", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (address.trim().isEmpty()) {
                            Toast.makeText(context, "Lütfen teslimat adresinizi girin", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSubmitting = true
                        coroutineScope.launch {
                            try {
                                val orderData = mapOf(
                                    "userId" to (currentUser?.uid ?: "anonymous"),
                                    "userNickname" to (currentUser?.nickname ?: ""),
                                    "fullName" to fullName.trim(),
                                    "phone" to phone.trim(),
                                    "address" to address.trim(),
                                    "productName" to "UNMASK Fiziki Kart Oyunu",
                                    "status" to "pending",
                                    "createdAt" to System.currentTimeMillis()
                                )
                                FirebaseConfig.firestore.collection("card_orders").add(orderData).await()
                                isSubmitting = false
                                showSuccessDialog = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isSubmitting = false
                                Toast.makeText(context, "Sipariş Gönderim Hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981), // Emerald Green
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Satın Al",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🛒 SATIN AL",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Success Order Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onBack()
            },
            title = {
                Text(text = "🎉 SİPARİŞ ALINDI!", fontWeight = FontWeight.Black, fontSize = 18.sp)
            },
            text = {
                Text(
                    text = "Fiziki kart siparişiniz başarıyla alındı! En kısa sürede kargoya verilecektir.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("TAMAM", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }
}
