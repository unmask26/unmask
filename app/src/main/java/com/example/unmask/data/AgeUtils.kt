package com.example.unmask.data

import androidx.compose.ui.graphics.Color
import java.util.Calendar

object AgeUtils {

    fun parseAge(birthDateStr: String?, fallbackAge: Int = 23): Int {
        if (birthDateStr.isNullOrBlank()) return fallbackAge
        try {
            val str = birthDateStr.trim()
            if (str.length == 4 && str.all { it.isDigit() }) {
                val birthYear = str.toInt()
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                return maxOf(18, currentYear - birthYear)
            }
            val parts = str.split("-", ".", "/")
            if (parts.size == 3) {
                val year = parts.find { it.length == 4 }?.toIntOrNull()
                    ?: parts.lastOrNull()?.toIntOrNull()
                    ?: 2000
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                return maxOf(18, currentYear - year)
            }
        } catch (_: Exception) {}
        return fallbackAge
    }

    /**
     * Color checkmark mapping according to age:
     * 18-22: Canlı Turuncu (#FF6B00)
     * 22-26: Taze Nane Yeşili (#10B981)
     * 26-31: Ateş Kırmızısı (#EF4444)
     * 31-35: Toprak Kahvesi (#78350F)
     * 35-40: Derin Lacivert (#1E3A8A)
     * 40-50: Gri – Fildişi (#6B7280)
     * 50-55: Bordo (#881337)
     * 55-60: Gümüş Gri - Platin (#D1D5DB)
     * 60+: Altın Sarısı - Kehribar (#F59E0B)
     */
    fun getCheckmarkColor(age: Int): Color {
        return when {
            age in 18..21 -> Color(0xFFFF6B00) // 18-22: Canlı Turuncu
            age in 22..25 -> Color(0xFF10B981) // 22-26: Taze Nane Yeşili
            age in 26..30 -> Color(0xFFEF4444) // 26-31: Ateş Kırmızısı
            age in 31..34 -> Color(0xFF78350F) // 31-35: Toprak Kahvesi
            age in 35..39 -> Color(0xFF1E3A8A) // 35-40: Derin Lacivert
            age in 40..49 -> Color(0xFF6B7280) // 40-50: Gri – Fildişi
            age in 50..54 -> Color(0xFF881337) // 50-55: Bordo
            age in 55..59 -> Color(0xFFD1D5DB) // 55-60: Gümüş Gri (Platin)
            age >= 60 -> Color(0xFFF59E0B)     // 60+: Altın Sarısı (Kehribar)
            else -> Color(0xFFFF6B00)          // Default 18-22
        }
    }
}
