const admin = require("firebase-admin");
const express = require("express");

const app = express();
const port = process.env.PORT || 3000;

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

const sentVideoNotifications = new Map();

// online_sessions koleksiyonunda video veya durum güncellendiğinde bildirim gönder
db.collection("online_sessions")
  .onSnapshot((snapshot) => {
    snapshot.docChanges().forEach(async (change) => {
      if (change.type === "added" || change.type === "modified") {
        const newData = change.doc.data();
        const sessionId = change.doc.id;

        const videoUrl = newData.videoUrl || "";
        const videoSenderId = newData.videoSenderId || "";
        const videoWatched = newData.videoWatched || newData.videoWatchedByReceiver || false;
        const status = newData.status || "";

        // Oyun Bitti Bildirimi
        if (status === "finished") {
          await sendGameOverNotification(sessionId, newData);
          return;
        }

        // 1. Rakip kullanıcıda video izlenmişse, video silindiyse veya video boşsa BİLDİRİM GELMESİN
        if (!videoUrl || !videoSenderId || videoWatched === true) {
          if (!videoUrl) sentVideoNotifications.delete(sessionId);
          return;
        }

        // 2. Bu seanstaki bu videoUrl için daha önce bildirim gönderildiyse TEKRAR GÖNDERME (Tek 1 defa gelsin)
        if (sentVideoNotifications.get(sessionId) === videoUrl) {
          return;
        }

        // Bildirimi kaydet ve SADECE 1 DEFA GÖNDER
        sentVideoNotifications.set(sessionId, videoUrl);
        await sendVideoNotification(sessionId, newData);
      }
    });
  }, (error) => {
    console.error("online_sessions Firestore dinleme hatası:", error);
  });

async function sendVideoNotification(sessionId, sessionData) {
  const videoSenderId = sessionData.videoSenderId || "";
  const user1Id = sessionData.user1Id || "";
  const user2Id = sessionData.user2Id || "";

  const receiverId = videoSenderId === user1Id ? user2Id : user1Id;
  const senderNickname = (videoSenderId === user1Id ? sessionData.user1Name : sessionData.user2Name) || "Rakip";

  if (!receiverId) {
    console.log("Video bildirimi için receiverId bulunamadı.");
    return;
  }

  const userDoc = await db.collection("users").doc(receiverId).get();
  const userData = userDoc.data();
  if (!userData) return;

  // Kullanıcı "Video Geldi" bildirimlerini kapattıysa gönderme
  if (userData.notifyVideoReceived === false) {
    console.log(`Kullanıcı ${receiverId} video bildirimlerini kapatmış.`);
    return;
  }

  const fcmToken = userData.fcmToken;
  if (!fcmToken) {
    console.log(`Kullanıcı ${receiverId} için FCM token bulunamadı.`);
    return;
  }

  const title = "📹 YENİ VİDEO!";
  const body = `@${senderNickname} size bir video gönderdi`;

  const message = {
    token: fcmToken,
    notification: {
      title: title,
      body: body,
    },
    android: {
      priority: "high",
      ttl: 0,
      notification: {
        title: title,
        body: body,
        sound: "default",
        channelId: "game_invitations_channel",
        defaultSound: true,
        defaultVibrateTimings: true,
        notificationPriority: "PRIORITY_MAX",
        visibility: "PUBLIC",
      },
    },
    data: {
      type: "online_game_video",
      sessionId: sessionId,
      senderNickname: senderNickname,
      videoUrl: sessionData.videoUrl || "",
      title: title,
      body: body,
    },
  };

  try {
    const response = await admin.messaging().send(message);
    console.log(`Video FCM bildirimi başarıyla gönderildi (${sessionId}):`, response);
  } catch (error) {
    console.error(`Video FCM gönderim hatası (${sessionId}):`, error);
  }
}

async function sendGameOverNotification(sessionId, sessionData) {
  const user1Id = sessionData.user1Id || "";
  const user2Id = sessionData.user2Id || "";

  // Her iki oyuncuya da bildirim tercihlerine göre bildir
  const players = [
    { id: user1Id, opponentName: sessionData.user2Name || "Rakip" },
    { id: user2Id, opponentName: sessionData.user1Name || "Rakip" }
  ];

  for (const player of players) {
    if (!player.id) continue;
    const userDoc = await db.collection("users").doc(player.id).get();
    const userData = userDoc.data();
    if (!userData || userData.notifyGameOver === false) continue;

    const fcmToken = userData.fcmToken;
    if (!fcmToken) continue;

    const title = "🏆 OYUN BİTTİ!";
    const body = `@${player.opponentName} ile oynadığınız online oyun tamamlandı!`;

    const message = {
      token: fcmToken,
      notification: {
        title: title,
        body: body,
      },
      android: {
        priority: "high",
        notification: {
          title: title,
          body: body,
          sound: "default",
          channelId: "game_invitations_channel",
        },
      },
      data: {
        type: "game_over",
        sessionId: sessionId,
        title: title,
        body: body,
      }
    };

    try {
      await admin.messaging().send(message);
      console.log(`Oyun Bitti bildirimi gönderildi -> ${player.id}`);
    } catch (e) {
      console.error("Oyun bitti bildirimi hatası:", e);
    }
  }
}

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
  const userData = userDoc.data();
  if (!userData) return;

  // Kullanıcı "Oyun İsteği" bildirimlerini kapattıysa gönderme
  if (userData.notifyGameInvite === false) {
    console.log(`Kullanıcı ${receiverId} oyun isteği bildirimlerini kapatmış.`);
    return;
  }

  const fcmToken = userData.fcmToken;
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
  const title = "🎮 OYUN İSTEĞİ GELDİ!";
  const body = catName
    ? `@${senderNickname} size ${catName} lobisinde oyun daveti gönderdi! 🎮`
    : `@${senderNickname} sizinle oyun oynamak istiyor!`;

  // FCM v1 mesajı
  const message = {
    token: fcmToken,
    notification: {
      title: title,
      body: body,
    },
    android: {
      priority: "high",
      notification: {
        title: title,
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
      title: title,
      body: body,
    },
  };

  try {
    const response = await admin.messaging().send(message);
    console.log(`FCM bildirimi başarıyla gönderildi (${requestId}):`, response);
  } catch (error) {
    console.error(`FCM gönderim hatası (${requestId}):`, error);
  }
}

// ⏰ SIRA SENDE - Her 3 Dakikada Bir Hatırlatıcı (Turn Reminder Cron)
setInterval(async () => {
  try {
    const activeSessions = await db.collection("online_sessions")
      .where("status", "==", "playing")
      .get();

    const now = Date.now();
    const THREE_MINUTES = 3 * 60 * 1000;

    for (const doc of activeSessions.docs) {
      const data = doc.data();
      const currentTurn = data.currentTurn;
      const lastHeartbeat = data.lastHeartbeat || data.lastTurnTimestamp || 0;

      if (!currentTurn) continue;

      // Eğer kullanıcının sırası 3 dakikayı geçtiyse ve kullanıcı oyunu bitirene kadar henüz yapmadıysa
      if (now - lastHeartbeat >= THREE_MINUTES) {
        const userDoc = await db.collection("users").doc(currentTurn).get();
        const userData = userDoc.data();

        if (userData && userData.notifyTurnReminder !== false && userData.fcmToken) {
          const opponentName = (currentTurn === data.user1Id ? data.user2Name : data.user1Name) || "Rakibiniz";
          const title = "⏰ SIRA SENDE!";
          const body = `@${opponentName} sizden hamle bekliyor! Görevinizi tamamlamak için oyuna dönün.`;
          const message = {
            token: userData.fcmToken,
            notification: {
              title: title,
              body: body,
            },
            android: {
              priority: "high",
              notification: {
                title: title,
                body: body,
                sound: "default",
                channelId: "game_invitations_channel",
              },
            },
            data: {
              type: "turn_reminder",
              sessionId: doc.id,
              title: title,
              body: body,
            }
          };

          await admin.messaging().send(message);
          console.log(`3 dk Sıra Sende hatırlatıcısı gönderildi -> ${currentTurn}`);
        }
      }
    }
  } catch (e) {
    console.error("Turn reminder periyodik kontrol hatası:", e);
  }
}, 3 * 60 * 1000); // 3 dakikada bir kontrol et

// Render.com Web Service'i olarak ücretsiz host edebilmek için sahte bir health-check endpoint'i
app.get("/", (req, res) => {
  res.send("UNMASK Bildirim Servisi Aktif ve Firestore dinleniyor!");
});

app.listen(port, () => {
  console.log(`Web Service ${port} portunda HTTP isteklerini dinliyor...`);
});
