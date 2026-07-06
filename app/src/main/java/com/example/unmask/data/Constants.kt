package com.example.unmask.data

import androidx.compose.ui.graphics.Color

data class CategoryInfo(
    val name: Category,
    val color: Color,
    val label: String
)

object Constants {
    const val CLOUDFLARE_WORKER_URL = "https://unmask-api.kullaniciadi.workers.dev/upload"

    // Cloudflare R2 Credentials for Yöntem B (Direct S3 client upload)
    const val CLOUDFLARE_R2_ACCESS_KEY_ID = "3567cb11308d9c134783bb446586c0f2"
    const val CLOUDFLARE_R2_SECRET_ACCESS_KEY = "7b8451a4088c2b8aa126a31681a50e6c9873ce644cb4b568fcf2ba24c29f9a23"
    const val CLOUDFLARE_R2_ENDPOINT = "https://78541d32b9d0136bfdd5e8d704962c7b.r2.cloudflarestorage.com"
    const val CLOUDFLARE_R2_BUCKET_NAME = "unmask"
    const val CLOUDFLARE_R2_PUBLIC_URL_PREFIX = "https://pub-82ef6a814f5a4f3890ae3a9019537321.r2.dev"

    val CATEGORIES = listOf(
        CategoryInfo("SPOR", Color(0xFF10B981), "SPOR"), // bg-emerald-500
        CategoryInfo("EĞLENCE", Color(0xFFF97316), "EĞLENCE"), // bg-orange-500
        CategoryInfo("BİLGİ", Color(0xFFFEF3C7), "BİLGİ"), // bg-amber-100 (Black text)
        CategoryInfo("GEZİ", Color(0xFF22D3EE), "GEZİ"), // bg-cyan-400
        CategoryInfo("ADULT", Color(0xFF000000), "ADULT"), // bg-black
        CategoryInfo("KENDİ OYUNLARIM", Color(0xFF8B5CF6), "KENDİ OYUNLARIM") // bg-violet-500
    )

    val GAMES = listOf(
        Game("buz-kirma", "Buz Kırma", "EĞLENCE", 0, true),
        Game("tabu", "Tabu", "EĞLENCE", 30, false),
        Game("kelime-oyunu", "Kelime Oyunu", "EĞLENCE", 30, false),
        Game("kim-milyoner", "Kim Milyoner", "BİLGİ", 30, false),
        Game("sarki-bilmece", "Şarkı Bilmece", "EĞLENCE", 30, false),
        Game("hizli-sut", "Hızlı Şut", "SPOR", 0, true),
        Game("basketbol", "Basketbol", "SPOR", 0, true),
        Game("kart-gorevleri", "Kart Görevleri", "EĞLENCE", 0, true),
        Game("dans-yarismasi", "Dans Yarışması", "EĞLENCE", 0, true),
        Game("yaratici-hikaye", "Yaratıcı Hikaye", "EĞLENCE", 0, true)
    )

    private val tabuWords = listOf(
        "Gözlük", "Telefon", "Kitap", "Bilgisayar", "Araba", "Uçak", "Tren", "Deniz", "Güneş", "Bulut",
        "Yağmur", "Kar", "Rüzgar", "Ağaç", "Çiçek", "Kedi", "Köpek", "Kuş", "Balık", "Ev",
        "Okul", "Öğretmen", "Öğrenci", "Sınıf", "Defter", "Kalem", "Silgi", "Masa", "Sandalye", "Kapı",
        "Pencere", "Mutfak", "Yemek", "Ekmek", "Su", "Çay", "Kahve", "Süt", "Meyve", "Sebze",
        "Elma", "Armut", "Muz", "Çilek", "Portakal", "Limon", "Domates", "Patates", "Soğan", "Biber",
        "Tuz", "Şeker"
    )

    private val icebreakerQuestions = listOf(
        "En büyük korkun nedir?",
        "En sevdiğin yemek hangisidir?",
        "Görünmez olsan ilk ne yapardın?",
        "Piyangodan büyük ikramiye çıksa ne alırdın?",
        "En son ne zaman ve neden ağladın?",
        "Çocukken büyüyünce ne olmak istiyordun?",
        "Süper gücün olsa ne olmasını isterdin?",
        "En çok gitmek istediğin ülke neresidir?",
        "En sevdiğin film veya dizi hangisidir?",
        "Hayatında aldığın en garip hediye neydi?",
        "En sevdiğin müzik tarzı nedir?",
        "Issız bir adaya düşsen yanına alacağın 3 şey ne olurdu?",
        "Tarihten biriyle akşam yemeği yiyecek olsan bu kim olurdu?",
        "En büyük pişmanlığın nedir?",
        "Şu anki mesleğin dışında ne yapmak isterdin?",
        "En sevdiğin renk hangisidir?",
        "Günde en çok neye vakit harcıyorsun?",
        "En çok sevdiğin özelliğin nedir?",
        "Değiştirmek istediğin bir alışkanlığın veya özelliğin var mı?",
        "Hayat felsefen tek bir cümleyle ne olurdu?",
        "En son okuduğun kitap hangisidir?",
        "Kedi insanı mısın yoksa köpek insanı mı?",
        "En sevmediğin yemek hangisidir?",
        "Sabah insanı mısın yoksa gece insanı mı?",
        "En sevdiğin mevsim hangisidir?",
        "Bir günlüğüne bir başkasının yerine geçsen bu kim olurdu?",
        "Hayatında yaptığın en çılgınca şey neydi?",
        "En çok değer verdiğin insan kimdir?",
        "Geleceğe mi gitmek isterdin geçmişe mi?",
        "En sevdiğin spor dalı hangisidir?",
        "En çok güldüğün anı hangisidir?",
        "İlk aşkın kimdi?",
        "En sevdiğin oyun hangisidir?",
        "Kendi hakkında anlatmak istediğin ilginç bir bilgi var mı?",
        "Şanslı sayın kaçtır?",
        "En sevdiğin hayvan hangisidir?",
        "Dünyada değiştirmek istediğin ilk şey ne olurdu?",
        "En sevdiğin şarkı hangisidir?",
        "Kendini üç kelimeyle nasıl tanımlarsın?",
        "En son yaptığın iyilik neydi?",
        "En çok neye sinirlenirsin?",
        "En büyük başarın nedir?",
        "Kendine verdiğin en iyi tavsiye neydi?",
        "Bir hayvan olsaydın hangisi olurdun?",
        "En sevdiğin tatil yeri neresidir?",
        "En çok dinlediğin podcast veya YouTube kanalı hangisidir?",
        "En sevdiğin aktör/aktris kimdir?",
        "Tarihte en çok hayran olduğun olay hangisidir?",
        "En sevdiğin meyve hangisidir?",
        "En çok özlediğin şey nedir?",
        "Şu an nerede olmak isterdin?",
        "Hayatındaki en büyük motivasyon kaynağın nedir?"
    )

    private val storyStarters = listOf(
        "Karanlık bir gecede ormanda yürürken birden...",
        "Sabah uyandığımda ellerimin mavi olduğunu gördüm...",
        "Yolda yürürken konuşan bir kedi bana dedi ki...",
        "Zaman makinesini çalıştırdım ve 100 yıl sonraya gittim...",
        "Uçaktan paraşütle atladığımda altımda dev bir...",
        "Bir sabah kapım çalındı ve gelen kişi bana gizemli bir kutu verdi...",
        "Görünmezlik iksirini içtikten sonra ilk işim...",
        "Denizin derinliklerinde parlayan bir batık gemi gördüm...",
        "Süpermarket arabam birden hızlanıp uçmaya başladı...",
        "Uzay gemimiz bilinmeyen bir gezegene acil iniş yaptı...",
        "Rüyamda gördüğüm o gizemli şato tam karşımdaydı...",
        "Cebimde bulduğum eski bir anahtar hangi kapıyı açıyordu?",
        "Fotoğraf albümündeki eski bir resim birden hareket etmeye başladı...",
        "Müzedeki o heykel bana göz kırptı ve...",
        "Bir günlüğüne dünyanın başkanı seçilseydim...",
        "Evcil hayvanım birden Türkçe konuşmaya başladı ve...",
        "Yağmur damlaları havada asılı kalmıştı, zaman durmuştu...",
        "Çölde yürürken karşımda dev bir kardan adam belirdi...",
        "Eski bir sahaf dükkanından aldığım kitap geleceği yazıyordu...",
        "O gece gökyüzünde iki tane ay vardı ve...",
        "Bir ressamın tablosunun içine çekildiğimde...",
        "Tünelin sonundaki ışığa ulaştığımda karşımda duran şey...",
        "Cüzdanımda hiç bitmeyen bir 100 TL olduğunu fark ettim...",
        "Bahçemizde kazı yaparken bulduğumuz o eski sandık...",
        "Telsizden gelen o garip ses sadece benim adımı söylüyordu...",
        "Bir sabah uyandığımda herkesin sadece şarkı söyleyerek konuştuğunu fark ettim...",
        "Göl kenarında yürürken suyun üstünde yürüyen birini gördüm...",
        "Eski bir radyodan gelen melodi beni geçmişe götürdü...",
        "Lunaparkta bindiğim hız treni raylardan çıkıp bulutlara yükseldi...",
        "Yere düşen göktaşının içinden çıkan küçük yaratık...",
        "Evimizin bodrum katında gizli bir geçit bulduk...",
        "Aynadaki yansımam benden bağımsız hareket edip bana güldü...",
        "Bir sabah tüm teknolojinin bir anda yok olduğunu hayal et...",
        "En sevdiğim bilgisayar oyununun içine ışınlandığımda...",
        "Sokak lambasının altında bekleyen o gölge birden kayboldu...",
        "Çantamdan çıkardığım pusula kuzeyi değil, en mutlu anımı gösteriyordu...",
        "O gün herkesin aklından geçenleri okuyabildiğimi fark ettim...",
        "Deniz kabuğunu kulağıma dayadığımda fısıldayan ses...",
        "Bir büyücü bana üç dilek hakkı verdi ama bir şartı vardı...",
        "Şehrin tam ortasında dev bir labirent belirdi ve...",
        "Kütüphanede gizli bölmedeki tozlu kitabı açtığımda...",
        "Yemek yediğim çatal kaşıklar birden havada uçuşmaya başladı...",
        "Bahçedeki ağacın meyveleri altın rengindeydi ve...",
        "O sabah yerçekimi yarı yarıya azalmıştı...",
        "Posta kutumdan çıkan imzasız mektupta yazan tek cümle...",
        "Yolda bulduğum o taşın içinden parlayan ışık...",
        "Çatımızda mahsur kalan o yaralı ejderha yavrusu...",
        "Herkesin renk körü olduğu bir dünyada tek renkli gören bendim...",
        "O eski tren istasyonuna hiç gelmeyen bir tren yanaştı...",
        "Ayakkabılarımı giydiğimde beni istediğim yere uçuruyorlardı...",
        "Bir günlüğüne hayvanlarla konuşabilme yeteneği kazandım...",
        "O karanlık mağaranın duvarlarında geleceğimin resimleri çiziliydi..."
    )

    private val cardDares = listOf(
        "Sağındaki kişiye iltifat et.",
        "Solundaki kişinin taklidini yap.",
        "En sevdiğin şarkıyı mırıldan.",
        "Komik bir yüz ifadesi yap ve 10 saniye bekle.",
        "Tek ayak üzerinde 15 saniye dur.",
        "Bir tekerlemeyi şaşırmadan 3 kez söyle.",
        "Odada bulunan bir nesne hakkında 1 dakika konuş.",
        "En sevdiğin dizi karakteri gibi konuş.",
        "5 şınav veya 10 squat çek.",
        "Bir opera sanatçısı gibi kendi adını söyle.",
        "Gözlerini kapatıp burnuna dokunmaya çalış.",
        "En son attığın mesajı yüksek sesle oku.",
        "Karşındaki kişiye bir bilmece sor.",
        "Robot dansı yap.",
        "En sevdiğin yemeğin tarifini hızlıca anlat.",
        "Bir dakika boyunca hiç göz kırpmamaya çalış.",
        "Sağındaki kişinin saç şeklini yorumla.",
        "Sanki televizyon sunucusuymuşsun gibi konuş.",
        "Sessiz sinema tarzında bir mesleği anlat.",
        "Kendi etrafında 5 kez dön ve düz yürümeye çalış.",
        "En sevdiğin takımı destekleyen bir tezahürat yap.",
        "Sanki çok soğuk bir yerdeymişsin gibi davran.",
        "En sevdiğin hayvanın sesini çıkar.",
        "Ağzında hayali bir sakız varmış gibi çiğne.",
        "Alfabeyi tersten söylemeye çalış (en az 5 harf).",
        "Karşındaki kişiyle 10 saniye göz teması kur.",
        "Komik bir fıkra anlat.",
        "En son izlediğin filmi 3 kelimeyle özetle.",
        "Sanki bir bebekmişsin gibi konuş.",
        "Cebindeki veya çantandaki 3 eşyayı göster ve tanıt.",
        "Hayali bir gitar çalıyormuş gibi yap.",
        "Bir dakika boyunca sadece evet veya hayır demeden konuş.",
        "En garip alışkanlığını itiraf et.",
        "Bir dakika boyunca sadece fısıldayarak konuş.",
        "Hayali bir halter kaldırıyormuş gibi davran.",
        "Solundaki kişinin en sevdiği rengi tahmin et.",
        "Sanki piyango kazanmışsın gibi sevinç çığlığı at.",
        "Bir dakika boyunca gözlerini kapatıp konuş.",
        "En sevdiğin kelimeyi ve nedenini söyle.",
        "Sağındaki kişiyle el sıkış ve komik bir selamlaşma uydur.",
        "Sanki çok ağır bir şey taşıyormuş gibi yürü.",
        "Hayali bir flüt çal.",
        "En sevmediğin ev işini anlat.",
        "Solundaki kişiye bir takma ad bul.",
        "Sanki çok lezzetli bir şey yiyormuş gibi sesler çıkar.",
        "En son gittiğin konseri veya etkinliği anlat.",
        "Bir dakika boyunca kollarını hiç kıpırdatmadan konuş.",
        "Sağındaki kişinin göz rengini tahmin et.",
        "En komik gülüşünü sergile.",
        "Sanki uykun varmış gibi esne ve konuş.",
        "Bir fıkra veya espri yapmaya çalış.",
        "En sevdiğin kahramanın taklidini yap."
    )

    private val dansTasks = listOf(
        "Erik dalı oyna",
        "Breakdance figürü sergile",
        "Robot dansı yap",
        "Vals adımları at",
        "Moonwalk yapmayı dene",
        "Tango adımları sergile",
        "Salsa figürleri yap",
        "Halay çek (mendil varmış gibi)",
        "Zeybek oyna (kolları açarak)",
        "Kolbastı figürleri yap",
        "Çiftetelli oyna",
        "Flamenko dansı yap",
        "Bale figürü (piruet) dene",
        "Hip-hop dans adımları sergile",
        "Disko dansı yap",
        "Sirtaki oyna",
        "Twist dansı yap",
        "Macarena dansı yap",
        "Cha Cha adımları at",
        "Samba dansı yap",
        "K-Pop dans koreografisi dene",
        "Modern dans figürü sergile",
        "Halk oyunu adımları sergile",
        "Komik bir dans uydur",
        "Sadece ellerini kullanarak dans et",
        "Sadece kafanı oynatarak ritim tut",
        "Gözlerin kapalı olarak dans et",
        "Tek ayak üzerinde zıplayarak dans et",
        "Ağır çekimde (slow motion) dans et",
        "Hızlı çekimde (fast forward) dans et",
        "Sanki suyun altındaymış gibi dans et",
        "Sanki uzaydaymış gibi (yer çekimsiz) dans et",
        "Oturduğun yerde omuzlarını oynatarak dans et",
        "Karşındaki kişinin dans figürlerini taklit et",
        "Sağındaki kişiyle el ele tutuşup dans et",
        "Sanki bir kuklaymışsın gibi dans et",
        "En sevdiğin şarkının nakaratında dans et",
        "Sessizce, müzik olmadan dans et",
        "Vücudunu dalgalandırarak (wave) dans et",
        "Bebek adımlarıyla dans et",
        "Sanki bir askermiş gibi ritmik dans et",
        "Kendi etrafında dönerek dans et",
        "Parmak uçlarında yükselerek dans et",
        "Sanki heyecanlı bir haber almış gibi dans et",
        "Sanki çok yorgunmuşsun gibi dans et",
        "Şemsiyeyle dans ediyormuş gibi yap",
        "Sanki rüzgara karşı yürüyormuş gibi dans et",
        "Sanki bir rock yıldızıymışsın gibi kafa salla",
        "Sanki bir kovboymuşsun gibi dans et",
        "Mısır patlağı gibi zıplayarak dans et",
        "Sanki çamura batmışsın gibi dans et",
        "En komik figürlerini sergileyerek çılgınca dans et"
    )

    private val hizliSutTasks = listOf(
        "Sağ ayağınla kaleye sert bir şut çek!",
        "Sol ayağınla köşeye plase bir şut çek!",
        "Rövaşata çekmeyi dene!",
        "Kafayla topu kaleye yolla!",
        "Topu sektirip yere indirmeden vole vur!",
        "Trivela (ayak dışı) vuruşu yap!",
        "Rabona vuruşuyla kaleyi hedefle!",
        "Topu kalecinin üstünden aşırarak (lob) şut çek!",
        "Gözlerin kapalı olarak kaleye şut çek!",
        "Geri geri gidip kaleye şut çek!",
        "Topu duvara çarpıp dönen topa gelişine vur!",
        "Topu 5 kere sektirdikten sonra şut çek!",
        "Frikik atışı kullanır gibi şut çek!",
        "Penaltı noktasına geçip kaleciyi ters köşeye yatır!",
        "Topu bacak arasından geçirip ardından şut çek!",
        "Topu havaya dikip göğsünle yumuşatarak şut çek!",
        "Topu sağa çekip sol ayakla uzak köşeye vur!",
        "Topu sola çekip sağ ayakla yakın köşeye vur!",
        "Topu topukla arkaya doğru şut çek!",
        "Tek ayak üzerinde durarak şut çek!",
        "Kendi etrafında 3 kez döndükten sonra şut çek!",
        "Sanki çok uzaktan (orta sahadan) şut çekiyormuş gibi vur!",
        "Yerden giden sert bir şut (füze) çek!",
        "Topu dizinle sektirip ardından vole vur!",
        "Sağ ayağının dışıyla köşeyi hedefle!",
        "Sol ayağının dışıyla köşeyi hedefle!",
        "Topu havaya atıp kafayla 90'a yolla!",
        "Topu bacak arasından vurarak şut çek!",
        "Sanki son dakika penaltısı atıyormuş gibi odaklanıp şut çek!",
        "Topu havaya kaldırıp göğüs-diz-ayak kombinasyonuyla şut çek!",
        "Koşarak gelip topa gelişine sert vur!",
        "Duran topa barajın üstünden aşıracak şekilde kavisli vur!",
        "Topu önce duvara vur, dönen topu kafayla kaleye tamamla!",
        "Topu havaya atıp rövaşata taklidi yaparak şut çek!",
        "Topu sektirirken omzunla pas verip ardından vur!",
        "Topu 3 saniye içinde kontrol edip şut çek!",
        "Topu kaleye sırtın dönükken havaya kaldırıp vole vur!",
        "Topu sürerek gel, kaleciyi çalımlayıp boş kaleye yuvarla!",
        "Topu iki ayağının arasında sıkıştırıp zıplatarak şut çek!",
        "Topu kalenin direğine nişanlamaya çalış!",
        "Topu havaya dikip kafanla sektirdikten sonra şut çek!",
        "Sağ ayakla plase, sol ayakla sert vuruş kombinasyonu yap!",
        "Topu önce göğsünle kontrol et, yere düşmeden vole vur!",
        "Topu topuğunla havaya kaldırıp gelişine vur (rainbow kick)!",
        "Topu sürerken aniden durup dönerek şut çek!",
        "Topu köşeye yavaş ama çok hassas bir şekilde yuvarla!",
        "Topa burnunla vurarak şut çek!",
        "Topu havaya atıp voleyle direkte patlatmayı dene!",
        "Topu önce kafanla kontrol et, sonra ayağınla şut çek!",
        "Topu arkadaşına pas ver, onun geri pasına gelişine vur!",
        "Topu kalenin tam doksanına (çatala) vurmayı dene!",
        "En havalı şut stilini sergileyerek kaleyi hedefle!"
    )

    private val basketbolTasks = listOf(
        "Sağ elle turnike atışı yap!",
        "Sol elle turnike atışı yap!",
        "Serbest atış çizgisinden şut çek!",
        "Üç sayılık çizgiden atış yap!",
        "Topu parmağında 5 saniye döndürmeyi dene!",
        "Gözün kapalı serbest atış yap!",
        "Bacak arasından top geçirerek dripling yap!",
        "Arkası dönük şekilde (geriye doğru) potaya atış yap!",
        "Topu panyaya (arka tahtaya) çarptırıp rebound al ve basket at!",
        "Topu bacak arasından geçirip hemen turnike at!",
        "Topu belinin etrafında 5 kez döndürdükten sonra şut çek!",
        "Topu 5 kere sektirip smaç yapıyormuş gibi zıpla ve at!",
        "Crossover (yön değiştirme) çalımları atıp şut çek!",
        "Topu kafanın etrafında 5 kez döndürdükten sonra şut çek!",
        "Tek ayak üzerinde durarak potaya şut çek!",
        "Step-back (geri adım atarak) şut çek!",
        "Fade-away (arkaya doğru eğilerek) şut çek!",
        "Topu havaya atıp havada kaparak (alley-oop) potaya at!",
        "Topu bacak aralarından 8 çizecek şekilde geçir!",
        "Topu iki elinle sektirip göğüs pası ver!",
        "Topu yere çarptırarak (bounce pass) arkadaşına yolla!",
        "Topu tek elle potaya fırlatmayı dene!",
        "Dripling yaparken aniden durup jump-shot at!",
        "Topu sırtının arkasından dolandırarak yön değiştir!",
        "Topu sektirirken diz çöküp kalkmaya çalış!",
        "Topu havaya atıp yere düşmeden kafanla dokun!",
        "Topu yerde yuvarlayıp arkasından koşarak kap ve şut çek!",
        "Topu potaya atmadan önce 3 kez sektirip derin nefes al!",
        "Topu göğüs hizasında tutup potaya kavisli bir atış yolla!",
        "Topu bacak arasından geçirip arkadan dolaştırma kombinasyonu yap!",
        "Topu parmağında döndürürken diğer elinle şut çekme taklidi yap!",
        "Dripling yaparken yön değiştirip sol elle turnikeye gir!",
        "Topu potaya çarptırıp havada smaç basmayı dene!",
        "Topu havaya fırlatıp en yüksek noktada yakalamaya çalış!",
        "Topu 10 saniye boyunca hiç durmadan çok hızlı sektir!",
        "Topu sırtının arkasından atıp önünde yakala!",
        "Tek elle dripling yaparken sol ayağını kaldır!",
        "Topu potanın çemberine çarptırmadan doğrudan sokmayı dene (swish)!",
        "Topu sektirirken kendi etrafında 360 derece dön!",
        "Topu bacak arasından geçirirken gözlerini kapat!",
        "Topu iki elinle göğsüne bastırıp zıplayarak şut çek!",
        "Topu sürerek gelip savunmacıyı geçme taklidi yap ve şut at!",
        "Topu havaya atıp arkaya doğru pas ver!",
        "Topu parmak uçlarında sektirerek kontrol et!",
        "Topu potaya en uzak mesafeden (yarı sahadan) atmayı dene!",
        "Dripling yaparken bacak arasından geçirip step-back yap!",
        "Topu panyaya çarptırıp havada yakalayarak basket at!",
        "Topu sektirirken sol ayağınla zıplayıp sağ elinle atış yap!",
        "Topu sırtında yuvarlamayı dene!",
        "Topu sektirirken arkadaşına bakmadan (no-look) pas ver!",
        "Topu potanın tam çemberine çarptırıp geri almaya çalış!",
        "En artistik şut stilini sergileyerek potaya atış yap!"
    )

    val TASKS: List<Task> by lazy {
        val list = mutableListOf<Task>()
        val suits = listOf("S", "H", "D", "C")
        val ranks = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")
        
        val suitNames = mapOf("S" to "Maça", "H" to "Kupa", "D" to "Karo", "C" to "Sinek")
        val rankNames = mapOf("J" to "Vale", "Q" to "Kız", "K" to "Papaz", "A" to "As")

        val gamesWithCards = listOf("hizli-sut", "tabu", "kart-gorevleri", "buz-kirma", "basketbol", "dans-yarismasi", "yaratici-hikaye")

        for (gameId in gamesWithCards) {
            var cardIndex = 0
            for (suit in suits) {
                for (rank in ranks) {
                    val cardCode = "$rank$suit"
                    val rankName = rankNames[rank] ?: rank
                    val suitName = suitNames[suit] ?: suit
                    val cardName = "$suitName $rankName"

                    val taskText = when (gameId) {
                        "tabu" -> {
                            val targetWord = tabuWords.getOrNull(cardIndex) ?: "Araba"
                            "Yasaklı kelimeleri kullanmadan '$targetWord' kelimesini anlat!"
                        }
                        "buz-kirma" -> {
                            val question = icebreakerQuestions.getOrNull(cardIndex) ?: "En büyük korkun nedir?"
                            "Yanındaki kişiye sor: $question"
                        }
                        "yaratici-hikaye" -> {
                            val starter = storyStarters.getOrNull(cardIndex) ?: "Karanlık bir gecede..."
                            "Bu cümleyle başlayan bir hikaye uydur: '$starter'"
                        }
                        "kart-gorevleri" -> {
                            val dare = cardDares.getOrNull(cardIndex) ?: "Sağındaki kişiye iltifat et."
                            dare
                        }
                        "hizli-sut" -> {
                            val targetTask = hizliSutTasks.getOrNull(cardIndex) ?: "Sağ ayağınla kaleye sert bir şut çek!"
                            targetTask
                        }
                        "basketbol" -> {
                            val targetTask = basketbolTasks.getOrNull(cardIndex) ?: "Sağ elle turnike atışı yap!"
                            targetTask
                        }
                        "dans-yarismasi" -> {
                            val targetTask = dansTasks.getOrNull(cardIndex) ?: "Erik dalı oyna"
                            targetTask
                        }
                        else -> "Bir hikaye anlat veya taklit yap!"
                    }

                    val duration = if (gameId == "tabu") 30 else 15

                    list.add(
                        Task(
                            id = "$gameId-$cardCode",
                            gameId = gameId,
                            cardCode = cardCode,
                            text = taskText,
                            duration = duration,
                            hasVideo = true
                        )
                    )
                    cardIndex++
                }
            }
        }
        list
    }
}
