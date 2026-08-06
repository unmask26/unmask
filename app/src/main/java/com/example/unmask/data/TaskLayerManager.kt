package com.example.unmask.data

/**
 * Katmanlı Görev Sistemi — Yönetici (6 Katman Sistemi)
 *
 * Profil Anahtarı Formatı: "{oyuncuYaşAralığı}_{cinsiyetKombo}_{kategori}"
 * Örnek: "18-22_erkek-kadin_iliskiler"
 *
 * Katmanlar (Her Katmanda 1 Kutu / 100 Görev):
 *  1. Katman → "İlkyüz" (Yeni tanıyan, buz kırıcı)
 *  2. Katman → "İkinyüz" (Daha fazla tanıyan)
 *  3. Katman → "Üçyüz" (Derine inen)
 *  4. Katman → "Dörtyüz" (Daha derine inen)
 *  5. Katman → "Beşyüz" (Tanıştıran)
 *  6. Katman → "Nihai Yüz" (Sosyal medya, etkinlik, bağ kurma)
 */
object TaskLayerManager {

    /**
     * Katman numarasına göre isim döndürür.
     */
    fun getLayerName(layerNumber: Int): String = when (layerNumber) {
        1 -> "İlkyüz"
        2 -> "İkinyüz"
        3 -> "Üçyüz"
        4 -> "Dörtyüz"
        5 -> "Beşyüz"
        6 -> "Nihai Yüz"
        else -> "$layerNumber. Katman"
    }

    /**
     * Tek bir oyuncunun yaşına göre profil anahtarını üretir.
     */
    fun getIndividualProfileKey(
        playerAge: Int,
        playerGender: String,
        opponentGender: String,
        category: String
    ): String {
        val ageRange = getAgeRange(playerAge)
        val genderCombo = buildGenderCombo(playerGender, opponentGender)
        val cat = category.lowercase().trim()
        return "${ageRange}_${genderCombo}_${cat}"
    }

    /**
     * İki oyuncu ve kategori için benzersiz Firestore çift (pair) anahtarı üretir.
     */
    fun getPairKey(user1Id: String, user2Id: String, category: String): String {
        val sortedUsers = listOf(user1Id, user2Id).sorted()
        val cat = category.lowercase().trim()
        return "${sortedUsers[0]}_${sortedUsers[1]}_${cat}"
    }

    /**
     * Yaş değerinden bracket döndürür.
     */
    fun getAgeRange(age: Int): String = when {
        age < 22  -> "18-22"
        age < 26  -> "22-26"
        age < 31  -> "26-31"
        age < 35  -> "31-35"
        age < 40  -> "35-40"
        age < 50  -> "40-50"
        age < 55  -> "50-55"
        age < 60  -> "55-60"
        else      -> "60+"
    }

    /**
     * Cinsiyet kombinasyonunu normalize eder.
     * erkek-erkek | kadin-kadin | erkek-kadin
     */
    fun buildGenderCombo(g1: String, g2: String): String {
        val a = g1.lowercase().trim().replace("ı", "i")
        val b = g2.lowercase().trim().replace("ı", "i")
        val na = if (a.contains("kad")) "kadin" else "erkek"
        val nb = if (b.contains("kad")) "kadin" else "erkek"
        return if (na == nb) "$na-$na"
        else "erkek-kadin"
    }

    /**
     * Sıradaki oyuncunun kendi profili + katmanına göre görev döndürür.
     * @param profileKey    getIndividualProfileKey() ile elde edilen oyuncu profili
     * @param layerNumber   Aktif katman (1..6)
     * @param boxNumber     Kutu numarası (Varsayılan 1)
     * @param usedTaskTexts Oturumda kullanılmış görevler
     */
    fun getTaskForPlayerTurn(
        profileKey: String,
        layerNumber: Int,
        boxNumber: Int = 1,
        usedTaskTexts: List<String> = emptyList()
    ): String? {
        val layers = LayeredTaskDatabase.PROFILES[profileKey] ?: return null
        val layer = layers.firstOrNull { it.layerNumber == layerNumber }
            ?: layers.maxByOrNull { it.layerNumber }
            ?: return null

        val box = layer.boxes.firstOrNull { it.boxNumber == boxNumber }
            ?: layer.boxes.firstOrNull()
            ?: return null

        val available = box.tasks.filter { it !in usedTaskTexts }
        val pool = if (available.isEmpty()) box.tasks else available
        return pool.randomOrNull()
    }

    /**
     * Bir profil için maksimum katman sayısını döndürür.
     */
    fun getMaxLayer(profileKey: String): Int {
        return LayeredTaskDatabase.PROFILES[profileKey]?.maxOfOrNull { it.layerNumber } ?: 6
    }

    /**
     * Verilen profil anahtarının veritabanında mevcut olup olmadığını kontrol eder.
     */
    fun hasProfile(profileKey: String): Boolean {
        return LayeredTaskDatabase.PROFILES.containsKey(profileKey)
    }
}
