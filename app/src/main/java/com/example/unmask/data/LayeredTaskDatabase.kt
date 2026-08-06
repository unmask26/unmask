package com.example.unmask.data

// ─────────────────────────────────────────────────────────────
//  Katmanlı Görev Sistemi — Veri Yapıları
// ─────────────────────────────────────────────────────────────

/** Bir katmanın (layer) içindeki tek bir kutunun görevleri */
data class TaskBox(
    val boxNumber: Int,       // 1..5
    val tasks: List<String>   // Bu kutudan random seçilecek görevler
)

/** Bir katman (layer) = 5 kutu */
data class TaskLayer(
    val layerNumber: Int,     // 1, 2, 3...
    val boxes: List<TaskBox>  // Her zaman 5 eleman: box 1..5
)

// ─────────────────────────────────────────────────────────────
//  Profil Anahtarı Formatı:
//  "{yaşAralığı}_{cinsiyetKombo}_{kategori}"
//
//  Yaş Aralıkları : 18-22 | 22-26 | 26-31 | 31-35 | 35-40 | 40-50 | 50-55 | 55-60 | 60+
//  Cinsiyet Kombo : erkek-erkek | kadin-kadin | erkek-kadin
//  Kategoriler    : iliskiler | adrenalin | bilgi | aktuel | hatiralar | fanteziler | adult | softhub
//
//  Örnek: "18-22_erkek-erkek_iliskiler"
// ─────────────────────────────────────────────────────────────

object LayeredTaskDatabase {

    // ================================================================
    // 📋 6 KATMANLI ŞABLON (Her Katmanda Tek Kutu / 100 Görev)
    // ================================================================
    //
    //  "YAS-ARALIK_CINSIYET-KOMBO_KATEGORI" to listOf(
    //      // ── 1. KATMAN: İlkyüz (Yeni Tanıyan / Buz Kırıcı) ──
    //      TaskLayer(layerNumber = 1, boxes = listOf(
    //          TaskBox(boxNumber = 1, tasks = listOf( /* 100 Görev */ ))
    //      )),
    //      // ── 2. KATMAN: İkinyüz (Daha Fazla Tanıyan) ──
    //      TaskLayer(layerNumber = 2, boxes = listOf(
    //          TaskBox(boxNumber = 1, tasks = listOf( /* 100 Görev */ ))
    //      )),
    //      // ── 3. KATMAN: Üçyüz (Derine İnen) ──
    //      TaskLayer(layerNumber = 3, boxes = listOf(
    //          TaskBox(boxNumber = 1, tasks = listOf( /* 100 Görev */ ))
    //      )),
    //      // ── 4. KATMAN: Dörtyüz (Daha Derine İnen) ──
    //      TaskLayer(layerNumber = 4, boxes = listOf(
    //          TaskBox(boxNumber = 1, tasks = listOf( /* 100 Görev */ ))
    //      )),
    //      // ── 5. KATMAN: Beşyüz (Tanıştıran) ──
    //      TaskLayer(layerNumber = 5, boxes = listOf(
    //          TaskBox(boxNumber = 1, tasks = listOf( /* 100 Görev */ ))
    //      )),
    //      // ── 6. KATMAN: Nihai Yüz (Sosyal Medya & Etkinlik Bağ Kurma) ──
    //      TaskLayer(layerNumber = 6, boxes = listOf(
    //          TaskBox(boxNumber = 1, tasks = listOf( /* 100 Görev */ ))
    //      ))
    //  ),
    // ================================================================

    val PROFILES: Map<String, List<TaskLayer>> = mapOf(

        // ════════════════════════════════════════════════════════════
        // ÖRNEK PROFİL: 18-22 yaş | Erkek-Erkek | İlişkiler
        // ════════════════════════════════════════════════════════════
        "18-22_erkek-erkek_iliskiler" to listOf(

            TaskLayer(layerNumber = 1, boxes = listOf(
                TaskBox(boxNumber = 1, tasks = listOf(
                    "Sence gerçek arkadaşlık nedir? 15 saniyede anlat.",
                    "En iyi arkadaşına bugüne kadar söyleyemediğin bir şeyi söyle.",
                    "Arkadaşlıkta en önemli özellik nedir? Birini seç ve savun."
                )),
                TaskBox(boxNumber = 2, tasks = listOf(
                    "Hayatında en çok kimi etkilemek istiyorsun? Anlat.",
                    "En büyük hayalin ne? 30 saniye anlatma zamanı.",
                    "Başarı mı yoksa mutluluk mu? Hangisini seçersin, neden?"
                )),
                TaskBox(boxNumber = 3, tasks = listOf(
                    "Kendini bir film karakteriyle karşılaştır. Hangi karakter ve neden?",
                    "Şimdiye kadar yaptığın en cesur şey neydi? Anlat.",
                    "Hayatını değiştiren bir an var mıydı? Paylaş."
                )),
                TaskBox(boxNumber = 4, tasks = listOf(
                    "10 yıl sonra kendini nerede görüyorsun? Anlat.",
                    "Bir şeyi baştan yaşayabilseydin ne olurdu? Neden?",
                    "Bugüne kadar aldığın en iyi karar neydi?"
                )),
                TaskBox(boxNumber = 5, tasks = listOf(
                    "Karşındaki kişi hakkında en çok neyi merak ediyorsun? Sor.",
                    "Bu oyunda öğrendiğin en ilginç şey neydi? Paylaş.",
                    "Karşındaki kişiye bugüne kadar söylemediğin bir iltifat yap."
                ))
            )),

            TaskLayer(layerNumber = 2, boxes = listOf(
                TaskBox(boxNumber = 1, tasks = listOf(
                    "Hiç kimseye söylemediğin bir sırrın var mı? Söylemeye hazır mısın?",
                    "En büyük korkun nedir? Korkuyla nasıl baş ediyorsun?",
                    "Kendini 3 kelimeyle tanımla ve nedenini açıkla."
                )),
                TaskBox(boxNumber = 2, tasks = listOf(
                    "Seni en çok ne motive eder? Anlat.",
                    "Hiç pişman olduğun bir karar var mı? Ne öğrendin?",
                    "En çok değer verdiğin şey nedir? Hayatta en önemli şey."
                )),
                TaskBox(boxNumber = 3, tasks = listOf(
                    "Güç mü, zeka mı, kalp mi? Hangisini seçersin?",
                    "Karşındaki kişi hakkında ilk izlenimin neydi?",
                    "Bu sohbette seni en çok etkileyen şey neydi?"
                )),
                TaskBox(boxNumber = 4, tasks = listOf(
                    "Dünyayı değiştirme şansın olsaydı ne değiştirirdin?",
                    "En büyük başarın nedir ve bunu nasıl hissettin?",
                    "Bir gün daha yaşayabilseydin nasıl geçirirdin?"
                )),
                TaskBox(boxNumber = 5, tasks = listOf(
                    "Bu ikinci katmanda öğrendiğin en önemli şey neydi?",
                    "Karşındaki kişiyle bir sonraki buluşmada ne yapmak istersin?",
                    "Bu oyunu oynamana değdi mi? Neden?"
                ))
            )),

            TaskLayer(layerNumber = 3, boxes = listOf(
                TaskBox(boxNumber = 1, tasks = listOf(
                    "Hayatında en çok kime güveniyorsun? Neden?",
                    "Şu ana kadar kimseye anlatmadığın bir deneyim var mı?",
                    "Seni gerçekten anlayan biri var mı? Anlat."
                )),
                TaskBox(boxNumber = 2, tasks = listOf(
                    "İdeal bir gün nasıl geçer? Sabahtan akşama anlat.",
                    "En büyük hayalini gerçek yaparken en zor an ne olur?",
                    "Hayatında en çok neyi özlemek istersin?"
                )),
                TaskBox(boxNumber = 3, tasks = listOf(
                    "Şu an en çok ne hissediyorsun ve neden?",
                    "Bu oyun seninle karşındaki kişi arasında bir şeyleri değiştirdi mi?",
                    "Karşındaki kişiyle paylaşmak istediğin ama söyleyemediğin bir şey var mı?"
                )),
                TaskBox(boxNumber = 4, tasks = listOf(
                    "3. katmana kadar geldiniz. En değerli anınız hangisiydi?",
                    "Karşındaki kişiyle bir ortak hedefiniz olsaydı ne olurdu?",
                    "Bu deneyimi başkalarına anlatırken ne söylersin?"
                )),
                TaskBox(boxNumber = 5, tasks = listOf(
                    "Son: Karşındaki kişiye en içten teşekkürünü söyle.",
                    "Bu oyunun sana kattığı en önemli şey neydi?",
                    "Bir sonraki buluşmada yapmak istediğin bir aktivite öner."
                ))
            ))
        ),

        // ════════════════════════════════════════════════════════════
        // ÖRNEK PROFİL: 18-22 yaş | Erkek-Kadın | İlişkiler
        // ════════════════════════════════════════════════════════════
        "18-22_erkek-kadin_iliskiler" to listOf(
            // ── 1. KATMAN: Güven İnşa Etme (Hafif, Eğlenceli, Buz Kırıcı) ─────────────────
            TaskLayer(layerNumber = 1, boxes = listOf(
                TaskBox(boxNumber = 1, tasks = listOf(
                    "Güne başlarken seni en çok ne moduna sokar? Kahve mi, müzik mi?",
                    "Profil resmime baktığında aklına gelen ilk kelime ne oldu?",
                    "Şu an hayatının bir soundtrack'i olsa hangi şarkı çalardı?",
                    "10 üzerinden bir enerjini puanla! Şu an nasılsın?",
                    "En son neye tek başına kahkaha attın?"
                )),
                TaskBox(boxNumber = 2, tasks = listOf(
                    "Boş bir hafta sonunu nasıl geçirmek senin için hayal gibi bir gündür?",
                    "Gece insanı mısın yoksa sabah erken kalkanlardan mı?",
                    "En çok hangi yemeği yemekten asla bıkmazsın?",
                    "Sosyal medyada en çok ne tür içeriklerde kayboluyorsun?",
                    "Şu an nerede olmak isterdin? Deniz kenarı mı, dağ evi mi?"
                )),
                TaskBox(boxNumber = 3, tasks = listOf(
                    "İzlediğin en iyi dizi veya film hangisiydi? Bana önerebilir misin?",
                    "Küçüklükten kalma komik bir alışkanlığın var mı?",
                    "Şu ana kadar gittiğin en güzel şehir veya mekân neresiydi?",
                    "En çok dinlediğin müzik tarzı nedir? Bir sanatçı söyle.",
                    "Gizli bir yeteneğin var mı? (Gözlerini kırpmadan durmak bile sayılır!)"
                )),
                TaskBox(boxNumber = 4, tasks = listOf(
                    "Birisi seni etkilemek istese ne yapmalı? İpucu ver!",
                    "Tatlı mı, tuzlu mu? Hızlıca cevap ver!",
                    "Arkadaş grubunda genellikle hangi roldesin? (Plan yapan, neşeli, sakin...)",
                    "Şu an masanın üzerinde veya yanında sana ait 1 şey söyle.",
                    "Yağmurlu bir günde evde ne yapmayı seversin?"
                )),
                TaskBox(boxNumber = 5, tasks = listOf(
                    "İlk katmanın sonundayız! Benimle ilgili şu ana kadar fark ettiğin olumlu 1 şey söyle.",
                    "Karşındaki kişiyle ilk buluşmada nereye gitmek isterdin?",
                    "Bana içten bir iltifat yap ama komik veya eğlenceli olsun!",
                    "1. katmanı bitirdik. Sence ikimizin enerjisi uydu mu?",
                    "Bu oyunu oynamadan önceki ruh halinle şu anki ruh halin arasında fark var mı?"
                ))
            )),

            // ── 2. KATMAN: Yüzeyin Altına İnme (Anılar, Flört Deneyimleri) ────────────────
            TaskLayer(layerNumber = 2, boxes = listOf(
                TaskBox(boxNumber = 1, tasks = listOf(
                    "Çocukken büyüyünce ne olmak isterdin? Şu an ne olmak istiyorsun?",
                    "Okul yıllarına dair asla unutamadığın komik bir anını anlat.",
                    "Çocukluğunda seni en çok ne mutlu ederdi?",
                    "Geçmişte yaptığın ve şu an hatılayınca güldüğün bir sakarlık var mı?",
                    "Küçükken en sevdiğin çizgi film veya oyuncak neydi?"
                )),
                TaskBox(boxNumber = 2, tasks = listOf(
                    "İlk aşkını veya ilk platonik hissettiğin anı hatırla. Neler hissetmiştin?",
                    "Sence bir ilişkide yapılan en büyük buz kırıcı jest nedir?",
                    "Flört ederken en çok dikkat ettiğin ilk 3 şey nedir?",
                    "Bir ilişkide asla tahammül edemeyeceğin 1 davranış söyle.",
                    "Romantik biri misin yoksa daha mantık insanı mı?"
                )),
                TaskBox(boxNumber = 3, tasks = listOf(
                    "En yakın arkadaşında en çok neye değer verirsin?",
                    "Bir dostundan aldığın ve seni çok etkileyen bir tavsiye var mı?",
                    "Arkadaş ortamında en çok neye sinirlenirsin?",
                    "Zor zamanlarında ilk aradığın kişi kimdir?",
                    "İnsanlarda güvenini kolayca kazandıran şey nedir?"
                )),
                TaskBox(boxNumber = 4, tasks = listOf(
                    "İlk buluşmada 'Bu kesinlikle yürümez' dedirten bir detay nedir?",
                    "Sevgi dili senin için nedir? (İlgi, hediye, kaliteli zaman, fiziksel temas...)",
                    "Bir ilişkide iletişimsizlik mi daha kötü yoksa kıskançlık mı?",
                    "Karşı cinste seni ilk anda büyüleyen şey ne olur?",
                    "Sence ideal bir ilişki ne kadar özgürlük içermeli?"
                )),
                TaskBox(boxNumber = 5, tasks = listOf(
                    "Şu an karşındaki kişi hakkında merak ettiğin tek bir soru sor!",
                    "2. katmanı tamamladık! Benim hakkımdaki düşüncen nasıl değişti?",
                    "Birlikte bir gün geçirecek olsak akşam ne yapmak isterdin?",
                    "Benimle sohbet etmek sana nasıl hissettiriyor?",
                    "İlişkilerde 'Keşke daha önce bilseydim' dediğin 1 şey var mı?"
                ))
            )),

            // ── 3. KATMAN: Duygusal Bağ Kurma (Kişisel Deneyimler, Duygular) ──────────────
            TaskLayer(layerNumber = 3, boxes = listOf(
                TaskBox(boxNumber = 1, tasks = listOf(
                    "Hayatında seni duygusal olarak en çok olgunlaştıran olay neydi?",
                    "Hiç birisi için beklenmedik büyük bir fedakarlık yaptın mı?",
                    "En çok hangi anında yalnız hissettin ve bununla nasıl baş ettin?",
                    "Kendi içinde aşmaya çalıştığın en büyük kişisel zorluk nedir?",
                    "Biri sana haksızlık yaptığında tepkin ne olur?"
                )),
                TaskBox(boxNumber = 2, tasks = listOf(
                    "Geleceğe dair en büyük içsel korkun nedir?",
                    "Kendinde en çok sevdiğin ve en çok değiştirmek istediğin 1 özellik nedir?",
                    "Hayatta reddedilme korkusu yaşadın mı? Nasıl etkiledi?",
                    "Dışarıdan güçlü görünürken içte en kırılgan olduğun konu nedir?",
                    "Hata yaptığında kolayca özür dileyebilir misin?"
                )),
                TaskBox(boxNumber = 3, tasks = listOf(
                    "Aşık olduğunda davranışların ve duyguların nasıl değişir?",
                    "Sevilmek mi seni daha çok besler yoksa birini derinden sevmek mi?",
                    "Bir ilişkide kendini güvende hissetmek senin için ne anlama gelir?",
                    "Duygularını açıkça ifade etmekte zorlanır mısın?",
                    "Hiç birine kırıldığını söyleyemeyip içine attığın oldu mu?"
                )),
                TaskBox(boxNumber = 4, tasks = listOf(
                    "Seni ağlatacak kadar duygulandıran en son şey neydi?",
                    "Hayatında kırıldığın birini gerçekten tamamen affedebildin mi?",
                    "Sence kırılan bir güven yeniden inşa edilebilir mi?",
                    "Stresli veya üzgün olduğunda rahatlamak için ne yaparsın?",
                    "Seni bu hayatta en çok anladığını düşündüğün kişi kim?"
                )),
                TaskBox(boxNumber = 5, tasks = listOf(
                    "3. katmandayız. Şu an aramızdaki bağ ve enerji sence nasıl ilerliyor?",
                    "Karşındaki kişiye içinden gelen derin ve samimi bir cümle söyle.",
                    "Benim hakkımda tahmin ettiğin ama emin olamadığın bir şey sor.",
                    "Bu sohbette öğrendiğin ve seni en çok şaşırtan detay neydi?",
                    "Karşılıklı olarak birbirimize güven duyduğumuzu hissediyor musun?"
                ))
            )),

            // ── 4. KATMAN: Gerçek Benliği Görme (Savunmasızlık, Hayaller, Değerler) ────────
            TaskLayer(layerNumber = 4, boxes = listOf(
                TaskBox(boxNumber = 1, tasks = listOf(
                    "Zamanı geri alabilseydin hayatındaki hangi anı veya kararı değiştirirdin?",
                    "Geçmişte 'Keşke o cümleyi söyleseydim' dediğin bir insan var mı?",
                    "Erken yaşta öğrendiğin en acı ama en değerli hayat dersi neydi?",
                    "Asla taviz vermeyeceğin 1 hayat prensibin nedir?",
                    "Kendine dair kabul etmekte zorlandığın bir gerçek var mı?"
                )),
                TaskBox(boxNumber = 2, tasks = listOf(
                    "10 yıl sonra kendini nerede, nasıl bir hayat yaşarken hayal ediyorsun?",
                    "Gerçekleştirmek için sabırsızlandığın en büyük hayalin nedir?",
                    "Hayatta başardığında 'Ben başardım' diyeceğin nihai hedefin ne?",
                    "Kendi ailen veya kurmak istediğin aile hakkında ne düşünüyorsun?",
                    "Maddi zenginlik mi, ruhsal huzur mu? Senin için hangisi öncelikli?"
                )),
                TaskBox(boxNumber = 3, tasks = listOf(
                    "Sence gerçek mutluluk nedir? Kısa süreli sevinçlerden farkı ne?",
                    "Yaşamın anlamı senin için 3 kelimeyle ne ifade ediyor?",
                    "Aşkın bir sonu olduğuna inanıyor musun yoksa ömür boyu sürebilir mi?",
                    "Bir insanda karakteri en net gösteren davranış sence nedir?",
                    "İnsanların senin hakkında en çok yanlış anladığı şey nedir?"
                )),
                TaskBox(boxNumber = 4, tasks = listOf(
                    "Maskelerini çıkardığında, hiç kimsenin bilmediği gerçek seni nasıl tanımlarsın?",
                    "En çok hangi anlarda kendini tamamen özgür hissediyorsun?",
                    "Toplumun beklentileri ile kendi isteklerin çakıştığında ne yaparsın?",
                    "İçindeki çocuk hala yaşıyor mu? Onu en çok ne sevindirir?",
                    "Kendinle baş başa kaldığında kafanı en çok kurcalayan düşünce ne?"
                )),
                TaskBox(boxNumber = 5, tasks = listOf(
                    "4. katmanı geçtik! Aramızda oluşan bu derin bağı nasıl tanımlarsın?",
                    "Şu an bana karşı kendini ne kadar rahat ve maskesiz hissediyorsun?",
                    "Bu oyunda benimle paylaştığın için mutlu olduğun bir anını söyle.",
                    "Benim yanımda kendin gibi olabildiğini hissediyor musun?",
                    "Karşındaki insanla gelecekte ortak bir hayal kuracak olsan bu ne olurdu?"
                ))
            )),

            // ── 5. KATMAN: Tam Bağlantı (Derin Yakınlık, Ruh Bağı, Gelecek) ──────────────
            TaskLayer(layerNumber = 5, boxes = listOf(
                TaskBox(boxNumber = 1, tasks = listOf(
                    "Ölmeden önce mutlaka yaşamak istediğin en derin deneyim nedir?",
                    "Ruhunu tamamen teslim edebileceğin birine dair vizyonun nedir?",
                    "Hayatta sana en çok ilham veren duygu veya insan kimdir?",
                    "Kendi hikayenin yazarı olarak şu an hayatının hangi bölümündesin?",
                    "Kırılmaktan korkmadan birine kalbini açmak senin için ne kadar mümkün?"
                )),
                TaskBox(boxNumber = 2, tasks = listOf(
                    "Sence iki insan arasındaki 'Ruh Eşi' bağı gerçek mi yoksa yaratılır mı?",
                    "Gerçek aşk seni bulduğunda onu korumak için neleri göze alırsın?",
                    "Bir insanda gördüğünde 'İşte bu benim aradığım kişi' dedirten duygu ne?",
                    "Tutku mu daha kalıcıdır yoksa birbirini derinden anlamak mı?",
                    "Sevildiğini en derin şekilde ne zaman ve nasıl hissedersin?"
                )),
                TaskBox(boxNumber = 3, tasks = listOf(
                    "Bu sohbetin başında benim hakkımda düşündüğünle şu an düşündüğün arasındaki en büyük fark ne?",
                    "Eğer bu oyun şu an biterse, benden aklında kalacak en belirgin iz ne olur?",
                    "Aramızdaki bu derin sohbetin sana hissettirdiği 3 duygu söyle.",
                    "İleride geri dönüp baktığında bu buluşmayı nasıl hatırlayacaksın?",
                    "Karşındaki insan olarak beni tek bir özel kelimeyle tanımla."
                )),
                TaskBox(boxNumber = 4, tasks = listOf(
                    "Bu 5 katmanlık yolculuk bittiğinde benimle yapmak istediğin ilk şey ne olurdu?",
                    "Gerçek hayatta yüz yüze kahve içsek ilk ne konuşmak isterdin?",
                    "Benimle ortak bir maceraya atılacak olsan nereye gitmek isterdin?",
                    "Şu an bana sormak istediğin en son ve en derin soruyu sor!",
                    "Birlikte vakit geçirmeye devam etme isteğin ne kadar güçlü?"
                )),
                TaskBox(boxNumber = 5, tasks = listOf(
                    "🏆 FİNAL: 5. Katmanı tamamladık! Bana en samimi, en içten teşekkürünü söyle.",
                    "🏆 FİNAL: İkimiz için ortak bir temenni veya dilek söyle.",
                    "🏆 FİNAL: Bu oyunda yaşadığımız en özel an sence hangi sorudaydı?",
                    "🏆 FİNAL: Şu an bu ekran kapanmadan önce bana söylemek istediğin son cümle ne?",
                    "🏆 FİNAL: Karşılıklı gözlerimizin içine bakıp (kameraya bakıp) içtenlikle gülümseyelim!"
                ))
            ))
        ),

        // ════════════════════════════════════════════════════════════
        // ÖRNEK PROFİL: 35-40 yaş | Erkek-Erkek | İlişkiler
        // ════════════════════════════════════════════════════════════
        "35-40_erkek-erkek_iliskiler" to listOf(
            TaskLayer(layerNumber = 1, boxes = listOf(
                TaskBox(boxNumber = 1, tasks = listOf(
                    "30'lu yaşların sonlarında dostluk sence nasıl tanımlanır?",
                    "Hayatta edindiğin en tecrübeli yaşam tavsiyesini ver."
                )),
                TaskBox(boxNumber = 2, tasks = listOf(
                    "Kariyer ve özel hayat dengesini kurmak sence ne kadar zor?",
                    "Gençliğinde yapmadığın ama şimdi 'keşke yapsaydım' dediğin bir şey var mı?"
                )),
                TaskBox(boxNumber = 3, tasks = listOf(
                    "Hayatta en gurur duyduğun başarın nedir?",
                    "Stresle baş etmenin sana özel en etkili yolu nedir?"
                )),
                TaskBox(boxNumber = 4, tasks = listOf(
                    "Gelecek 10 yıl için en büyük hedefin nedir?",
                    "İlişkilerde olgunluğun getirdiği en büyük avantaj nedir?"
                )),
                TaskBox(boxNumber = 5, tasks = listOf(
                    "Karşındaki genç arkadaşa vereceğin en değerli hayat tavsiyesi nedir?",
                    "Bu sohbet sana hayatın hangi evresini hatırlattı?"
                ))
            )),
            TaskLayer(layerNumber = 2, boxes = listOf(
                TaskBox(boxNumber = 1, tasks = listOf(
                    "Katman 2 / Kutu 1: Hayatta aldığın en riskli karar neydi ve sonuç ne oldu?",
                    "Katman 2 / Kutu 1: Yıllar geçtikçe değerlerin nasıl değişti?"
                )),
                TaskBox(boxNumber = 2, tasks = listOf(
                    "Katman 2 / Kutu 2: Asla vazgeçmeyeceğin bir prensibini anlat."
                )),
                TaskBox(boxNumber = 3, tasks = listOf(
                    "Katman 2 / Kutu 3: Gerçek başarı ve iç huzur senin için ne ifade ediyor?"
                )),
                TaskBox(boxNumber = 4, tasks = listOf(
                    "Katman 2 / Kutu 4: Kendine dair kabul ettiğin en zor gerçek neydi?"
                )),
                TaskBox(boxNumber = 5, tasks = listOf(
                    "Katman 2 / Kutu 5: Oyunun 2. katmanını tamamladınız, hislerin neler?"
                ))
            ))
        )

        // ════════════════════════════════════════════════════════════
        // 🟡 BURAYA YENİ PROFİLLER EKLEYEBİLİRSİNİZ
        // Şablon için dosyanın başındaki açıklamayı okuyun
        // ════════════════════════════════════════════════════════════
    )
}
