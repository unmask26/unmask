package com.example.unmask.data

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

@Serializable
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val nickname: String? = "",
    val gender: String = "Erkek",
    val birthDate: String = "",
    val isAdult: Boolean = false,
    val adultPassword: String? = "",
    val banUntil: Long? = 0L
)

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
    val banUntil: Long = 0
)

@Serializable
data class OnlineSession(
    val id: String = "",
    val user1Id: String = "",
    val user1Name: String = "",
    val user2Id: String = "",
    val user2Name: String = "",
    val status: String = "category_selection", // "category_selection", "game_selection", "playing", "finished"
    val user1Categories: List<String> = emptyList(),
    val user2Categories: List<String> = emptyList(),
    val commonCategory: String = "",
    val selectedGameId: String = "",
    val currentTurn: String = "", // userId
    val activeCardCode: String = "",
    val activeTaskId: String = "",
    val videoUrl: String = "",
    val videoSenderId: String = "",
    val downloadRequestStatus: String = "none", // "none", "requested", "approved", "rejected"
    val lastHeartbeat: Long = 0
)
