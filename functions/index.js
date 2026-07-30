const admin = require("firebase-admin");

// Render Environment Variable üzerinden key'i okuyoruz
const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

console.log("UNMASK Bildirim Servisi Başlatıldı. Firestore dinleniyor...");

// direct_game_requests koleksiyonunda yeni eklenen dokümanları dinle
db.collection("direct_game_requests")
  .where("status", "==", "pending")
  .onSnapshot((snapshot) => {
    snapshot.docChanges().forEach(async (change) => {
      // Sadece 'added' (yeni eklenen) durumundakilere bildirim at
      if (change.type === "added") {
        const data = change.doc.data();
        const requestId = change.doc.id;
        
        await sendNotification(requestId, data);
      }
    });
  }, (error) => {
    console.error("Firestore dinleme hatası:", error);
  });


async function sendNotification(requestId, data) {
  const receiverId = data.receiverId || "";
  const senderNickname = data.senderNickname || "Bir oyuncu";
  const selectedCategory = data.selectedCategory || "";

  if (!receiverId) {
    console.log("receiverId bulunamadı, bildirim gönderilmedi.");
    return;
  }

  // Alıcının FCM token'ını Firestore'dan al
  const userDoc = await db.collection("users").doc(receiverId).get();
  const fcmToken = userDoc.data()?.fcmToken;

  if (!fcmToken) {
    console.log(`Kullanıcı ${receiverId} için FCM token bulunamadı.`);
    return;
  }

  // Kategori adı
  const catNames = {
    iliskiler: "İLİŞKİLER",
    adrenalin: "ADRENALİN",
    bilgi: "BİLGİ",
    aktuel: "AKTÜEL",
    hatiralar: "HATIRALAR",
    fanteziler: "FANTEZİLER",
    adult: "ADULT (+18)",
    softhub: "SOFTHUB",
  };
  const catName = catNames[selectedCategory.toLowerCase()] || selectedCategory.toUpperCase();
  const body = catName
    ? `@${senderNickname} size ${catName} lobisinde oyun daveti gönderdi! 🎮`
    : `@${senderNickname} sizinle oyun oynamak istiyor!`;

  // FCM v1 mesajı
  const message = {
    token: fcmToken,
    android: {
      priority: "high",
      notification: {
        title: "🎮 OYUN İSTEĞİ GELDİ!",
        body: body,
        sound: "default",
        channelId: "game_invitations_channel",
      },
    },
    data: {
      requestId: requestId,
      senderId: data.senderId || "",
      senderNickname: senderNickname,
      selectedCategory: selectedCategory,
    },
  };

  try {
    const response = await admin.messaging().send(message);
    console.log(`FCM bildirimi başarıyla gönderildi (${requestId}):`, response);
  } catch (error) {
    console.error(`FCM gönderim hatası (${requestId}):`, error);
  }
}
