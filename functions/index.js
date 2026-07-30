const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

// Firestore'da yeni davet oluştuğunda ANINDA tetiklenir
exports.sendGameInviteNotification = functions.firestore
  .document("direct_game_requests/{requestId}")
  .onCreate(async (snap, context) => {
    const data = snap.data();

    if (!data || data.status !== "pending") return null;

    const receiverId = data.receiverId || "";
    const senderNickname = data.senderNickname || "Bir oyuncu";
    const selectedCategory = data.selectedCategory || "";
    const requestId = context.params.requestId;

    if (!receiverId) {
      console.log("receiverId bulunamadı, bildirim gönderilmedi.");
      return null;
    }

    // Alıcının FCM token'ını Firestore'dan al
    const userDoc = await admin.firestore().collection("users").doc(receiverId).get();
    const fcmToken = userDoc.data()?.fcmToken;

    if (!fcmToken) {
      console.log(`Kullanıcı ${receiverId} için FCM token bulunamadı.`);
      return null;
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

    // FCM v1 mesajı — anında, yüksek öncelikli
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
      console.log("FCM bildirimi gönderildi:", response);
    } catch (error) {
      console.error("FCM gönderim hatası:", error);
    }

    return null;
  });
