package com.example.unmask.data

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import kotlinx.serialization.Serializable

typealias Category = String

@Serializable
data class Game(
    val id: String = "",
    val name: String = "",
    val category: Category = "",
    val price: Int = 0,
    val isFree: Boolean = true
)

@Serializable
data class Task(
    val id: String = "",
    val gameId: String = "",
    val cardCode: String = "",
    val text: String = "",
    val duration: Int = 15,
    val hasVideo: Boolean = true
)

@com.google.firebase.firestore.IgnoreExtraProperties
@Serializable
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val nickname: String? = "",
    val gender: String = "Erkek",
    val birthDate: String = "",
    val isAdult: Boolean = false,
    val adultPassword: String? = "",
    val banUntil: Long? = 0L,
    val score: Int = 100,
    val ratingCount: Int = 0,
    val totalRating: Int = 0,
    val securityAnswers: Map<String, String> = emptyMap(),
    val following: List<String> = emptyList()
) {
    val isUserAdult: Boolean
        get() {
            if (isAdult) return true
            if (birthDate.isBlank()) return false
            return try {
                val parts = birthDate.trim().split("-", ".", "/")
                if (parts.size != 3) return false
                val (year, month, day) = if (parts[0].length == 4) {
                    Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                } else {
                    Triple(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                }
                val calBirth = java.util.Calendar.getInstance().apply {
                    set(year, month - 1, day, 0, 0, 0)
                }
                val cal18Mins = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.YEAR, -18)
                }
                !calBirth.after(cal18Mins)
            } catch (e: Exception) {
                false
            }
        }
}

@Serializable
data class Memory(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val gameId: String = "",
    val gameName: String = "",
    val category: Category = "",
    val taskId: String = "",
    val taskText: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val date: String = "",
    val likes: Int = 0,
    val isPublic: Boolean = true
)

@Serializable
data class HistoryItem(
    val id: String = "",
    val userId: String = "",
    val gameName: String = "",
    val date: String = "",
    val note: String = ""
)

@Serializable
data class OnlineUserPresence(
    val userId: String = "",
    val userName: String = "",
    val status: String = "idle", // "idle", "playing", "offline"
    val lastActive: Long = 0,
    val banUntil: Long = 0,
    val score: Int = 100,
    val gender: String = "Erkek"
)

@Serializable
data class OnlineSession(
    val id: String = "",
    val user1Id: String = "",
    val user1Name: String = "",
    val user2Id: String = "",
    val user2Name: String = "",
    val user1Gender: String = "Erkek",
    val user2Gender: String = "Erkek",
    val status: String = "category_selection", // "category_selection", "game_selection", "playing", "rating", "finished"
    val user1Categories: List<String> = emptyList(),
    val user2Categories: List<String> = emptyList(),
    val commonCategory: String = "",
    val selectedGameId: String = "",
    val currentTurn: String = "", // userId
    val activeCardCode: String = "",
    val activeTaskId: String = "",
    val activeTaskText: String = "",
    val videoUrl: String = "",
    val videoSenderId: String = "",
    val downloadRequestStatus: String = "none", // "none", "requested", "approved", "rejected"
    val lastHeartbeat: Long = 0,
    val user1TaskCount: Int = 0,
    val user2TaskCount: Int = 0,
    val user1Rating: Int = 0,
    val user2Rating: Int = 0,
    val usedTaskIds: List<String> = emptyList(),   // Tracks game-based tasks used in this session
    val usedTaskTexts: List<String> = emptyList(), // Tracks matchmaking tasks used in this session
    val replayRequestStatus: String = "none",      // "none", "requested", "accepted", "rejected"
    val replayRequesterId: String = "",
    val replayRequesterName: String = ""
)

@Serializable
data class Comment(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val text: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class PublicVideo(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val videoUrl: String = "",
    val gameName: String = "",
    val taskText: String = "",
    val filterName: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val likesCount: Int = 0,
    val likedBy: List<String> = emptyList(),
    val comments: List<Comment> = emptyList()
)

@Serializable
data class DirectGameRequest(
    val id: String = "",
    val senderId: String = "",
    val senderNickname: String = "",
    val senderGender: String = "Erkek",
    val receiverId: String = "",
    val receiverNickname: String = "",
    val receiverGender: String = "Erkek",
    val selectedCategory: String = "",
    val status: String = "pending", // "pending", "lobby_selected", "playing", "rejected"
    val createdAt: Long = 0L,
    val sessionId: String = ""
)

@Serializable
data class OnlineOpponentHistory(
    val opponentId: String = "",
    val opponentName: String = "",
    val opponentGender: String = "Erkek",
    val lastPlayedTimestamp: Long = 0L,
    val lastCategory: String = ""
)

