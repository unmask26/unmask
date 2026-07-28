package com.example.unmask.data

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import android.content.ContentUris
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull


interface DataRepository {
    val currentUser: Flow<UserProfile?>
    val currentFirebaseUser: FirebaseUser?
    
    suspend fun loginAnonymously(): UserProfile
    suspend fun loginWithCredential(credential: AuthCredential, googleBirthDate: String = ""): UserProfile
    suspend fun logout()
    
    suspend fun updateProfile(displayName: String, nickname: String, gender: String, birthDate: String, adultPassword: String = ""): UserProfile
    
    fun getMemories(userId: String): Flow<List<Memory>>
    fun getPublicMemories(): Flow<List<Memory>>
    suspend fun addMemory(
        gameId: String,
        gameName: String,
        category: String,
        taskId: String,
        taskText: String,
        videoUri: Uri,
        isPublic: Boolean
    ): String
    
    suspend fun deleteMemory(memoryId: String)
    suspend fun toggleMemoryPublic(memoryId: String, currentPublicStatus: Boolean)
    suspend fun likeMemory(memoryId: String)
    
    fun getHistory(userId: String): Flow<List<HistoryItem>>
    suspend fun addHistoryItem(gameName: String, note: String = "")
    suspend fun updateHistoryNote(itemId: String, note: String)
    suspend fun deleteHistoryItem(itemId: String)
    val customGames: Flow<List<Game>>
    val customTasks: Flow<List<Task>>
    suspend fun saveCustomGame(game: Game, tasks: List<Task>)
    
    fun getOnlineUsers(currentUserId: String): Flow<List<OnlineUserPresence>>
    suspend fun updatePresence(userId: String, userName: String, status: String, banUntil: Long = 0, gender: String = "Erkek")
    suspend fun removePresence(userId: String)
    fun observeActiveSession(userId: String): Flow<OnlineSession?>
    suspend fun createSession(user1Id: String, user1Name: String, user1Gender: String = "Erkek", user2Id: String, user2Name: String, user2Gender: String = "Erkek", category: String = ""): String
    suspend fun updateSession(session: OnlineSession)
    suspend fun updateSessionHeartbeat(sessionId: String)
    suspend fun deleteSession(sessionId: String)
    suspend fun cleanActiveSessions(userId: String)
    suspend fun uploadOnlineVideo(sessionId: String, videoUri: Uri): String
    suspend fun deleteOnlineVideo(videoUrl: String)
    suspend fun banUser(userId: String, durationMs: Long)
    suspend fun rateUser(targetUserId: String, ratingStars: Int)
    suspend fun publishPublicVideo(gameName: String, taskText: String, filterName: String, videoUri: Uri): String
    fun getPublicVideos(): Flow<List<PublicVideo>>
    suspend fun cleanExpiredPublicVideos()
    suspend fun toggleLikePublicVideo(videoId: String): Boolean
    suspend fun addCommentToPublicVideo(videoId: String, commentText: String)
    fun getOnlineHistoryOpponents(userId: String): Flow<List<OnlineOpponentHistory>>
    fun getAllUserPresences(): Flow<List<OnlineUserPresence>>
    suspend fun sendDirectGameRequest(senderId: String, senderNickname: String, senderGender: String, receiverId: String, receiverNickname: String, receiverGender: String = "Erkek"): String
    fun observeIncomingGameRequests(userId: String): Flow<List<DirectGameRequest>>
    fun observeSentGameRequests(userId: String): Flow<List<DirectGameRequest>>
    suspend fun acceptDirectGameRequest(request: DirectGameRequest, selectedCategory: String)
    suspend fun rejectDirectGameRequest(requestId: String)
    suspend fun launchSessionFromDirectRequest(request: DirectGameRequest): String
    suspend fun cancelSentGameRequest(requestId: String)
    suspend fun sendAdultPasswordResetCode(email: String): String
    suspend fun verifyAdultPasswordResetCode(email: String, code: String): Boolean
    suspend fun saveSecurityAnswers(answers: Map<String, String>)
    suspend fun verifySecurityAnswer(question: String, answer: String): Boolean
    fun getStoredSecurityQuestions(): Map<String, String>
    suspend fun sendPasswordResetEmail(email: String)
    suspend fun verifyAndUpdateAdultPassword(inputPassword: String): Boolean
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val auth by lazy { FirebaseConfig.auth }
    private val firestore by lazy { FirebaseConfig.firestore }
    private val storage by lazy { FirebaseConfig.storage }

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    override val currentUser: StateFlow<UserProfile?> = _currentUser

    override val currentFirebaseUser: FirebaseUser?
        get() = try { auth.currentUser } catch (e: Exception) { null }

    // Local In-Memory Fallback Database
    private val localMemories = mutableListOf<Memory>()
    private val _localMemoriesFlow = MutableStateFlow<List<Memory>>(emptyList())

    private val localHistory = mutableListOf<HistoryItem>()
    private val _localHistoryFlow = MutableStateFlow<List<HistoryItem>>(emptyList())

    private val customGamesFile = File(context.filesDir, "custom_games.json")
    private val customTasksFile = File(context.filesDir, "custom_tasks.json")

    private val _customGames = MutableStateFlow<List<Game>>(emptyList())
    override val customGames: Flow<List<Game>> = _customGames

    private val _customTasks = MutableStateFlow<List<Task>>(emptyList())
    override val customTasks: Flow<List<Task>> = _customTasks

    init {
        try {
            loadCustomGamesAndTasks()
            scanLocalUnmaskVideos()
            FirebaseConfig.initialize(context)
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                if (user != null) {
                    listenToUserProfile(user.uid)
                } else {
                    // Only clear user if we are NOT logged in as a local offline user
                    if (_currentUser.value?.uid != "offline_demo_user") {
                        _currentUser.value = null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scanLocalUnmaskVideos() {
        val memoriesList = mutableListOf<Memory>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED
        )

        // Query videos inside the UNMASK folder using BUCKET_DISPLAY_NAME for compatibility
        val selection = "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("UNMASK")
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val dateAdded = cursor.getLong(dateColumn)

                    val videoUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val memory = parseMemoryFromFileName(name, videoUri, dateAdded)
                    memoriesList.add(memory)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        localMemories.clear()
        localMemories.addAll(memoriesList)
        _localMemoriesFlow.value = localMemories.toList()
    }

    private fun parseMemoryFromFileName(fileName: String, videoUri: Uri, dateAddedSecs: Long): Memory {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val parts = nameWithoutExt.split("_")
        
        var gameId = ""
        var cardCode = ""
        var gameName = "Önceki Kayıt"
        var taskText = "Kaydedilmiş video görevi"
        
        if (parts.size >= 4 && parts[0] == "unmask") {
            gameId = parts[1]
            cardCode = parts[2]
            val taskId = "$gameId-$cardCode"
            
            val game = Constants.GAMES.find { it.id == gameId }
            val task = Constants.TASKS.find { it.id == taskId }
            
            if (game != null) {
                gameName = game.name
            }
            if (task != null) {
                taskText = task.text
            }
        }

        val sdf = SimpleDateFormat("d MMMM yyyy", Locale("tr", "TR"))
        val dateString = sdf.format(java.util.Date(dateAddedSecs * 1000))

        return Memory(
            id = nameWithoutExt,
            userId = _currentUser.value?.uid ?: "local_user",
            userName = _currentUser.value?.displayName ?: "Sen",
            gameId = gameId,
            gameName = gameName,
            category = "EĞLENCE",
            taskId = "$gameId-$cardCode",
            taskText = taskText,
            videoUrl = videoUri.toString(),
            thumbnailUrl = videoUri.toString(),
            date = dateString,
            likes = 0,
            isPublic = false
        )
    }

    private var profileListener: ListenerRegistration? = null

    private fun listenToUserProfile(uid: String) {
        try {
            profileListener?.remove()
            profileListener = firestore.collection("users").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Fallback profile if Firestore permissions fail
                        if (_currentUser.value == null) {
                            _currentUser.value = UserProfile(
                                uid = uid,
                                displayName = "Oyuncu",
                                gender = "Erkek",
                                birthDate = "2000-01-01",
                                isAdult = true
                            )
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        _currentUser.value = snapshot.toObject(UserProfile::class.java)
                    } else {
                        // Create default profile structure
                        _currentUser.value = UserProfile(
                            uid = uid,
                            displayName = "Oyuncu",
                            gender = "Erkek",
                            birthDate = "",
                            isAdult = false
                        )
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            _currentUser.value = UserProfile(uid = uid, displayName = "Oyuncu")
        }
    }

    override suspend fun loginAnonymously(): UserProfile {
        return try {
            val result = auth.signInAnonymously().await()
            val uid = result.user!!.uid
            val profile = UserProfile(
                uid = uid,
                displayName = "Demo Oyuncu",
                gender = "Erkek",
                birthDate = "2000-01-01",
                isAdult = true
            )
            try {
                firestore.collection("users").document(uid).set(profile).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _currentUser.value = profile
            profile
        } catch (e: Exception) {
            e.printStackTrace()
            // Bulletproof fallback to offline user profile
            val profile = UserProfile(
                uid = "offline_demo_user",
                displayName = "Demo Oyuncu (Local)",
                gender = "Erkek",
                birthDate = "2000-01-01",
                isAdult = true
            )
            _currentUser.value = profile
            profile
        }
    }

    override suspend fun loginWithCredential(credential: AuthCredential, googleBirthDate: String): UserProfile {
        return try {
            val result = auth.signInWithCredential(credential).await()
            val uid = result.user!!.uid

            fun calculateIsAdult(bDate: String): Boolean {
                return try {
                    if (bDate.isEmpty()) return false
                    val parts = bDate.split("-")
                    val birthYear = parts[0].toInt()
                    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    (currentYear - birthYear) >= 18
                } catch (e: Exception) {
                    false
                }
            }

            try {
                val doc = firestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    var profile = doc.toObject(UserProfile::class.java)!!
                    if (profile.birthDate.isEmpty() && googleBirthDate.isNotEmpty()) {
                        val isAdultCalculated = calculateIsAdult(googleBirthDate)
                        profile = profile.copy(birthDate = googleBirthDate, isAdult = isAdultCalculated)
                        try {
                            firestore.collection("users").document(uid).update(
                                mapOf("birthDate" to googleBirthDate, "isAdult" to isAdultCalculated)
                            ).await()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    _currentUser.value = profile
                    profile
                } else {
                    val isAdultCalculated = calculateIsAdult(googleBirthDate)
                    val profile = UserProfile(
                        uid = uid,
                        displayName = result.user!!.displayName ?: "Oyuncu",
                        gender = "Erkek",
                        birthDate = googleBirthDate,
                        isAdult = isAdultCalculated
                    )
                    firestore.collection("users").document(uid).set(profile).await()
                    _currentUser.value = profile
                    profile
                }
            } catch (firestoreEx: Exception) {
                firestoreEx.printStackTrace()
                // Graceful fallback: Google login succeeded, so use Google details locally if Firestore has Permission Denied
                val isAdultCalculated = calculateIsAdult(googleBirthDate)
                val profile = UserProfile(
                    uid = uid,
                    displayName = result.user?.displayName ?: "Oyuncu",
                    gender = "Erkek",
                    birthDate = googleBirthDate,
                    isAdult = isAdultCalculated
                )
                _currentUser.value = profile
                profile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun logout() {
        try {
            profileListener?.remove()
            profileListener = null
            auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _currentUser.value = null
    }

    override suspend fun updateProfile(displayName: String, nickname: String, gender: String, birthDate: String, adultPassword: String): UserProfile {
        val current = _currentUser.value ?: throw IllegalStateException("Kullanıcı giriş yapmamış.")
        val uid = current.uid
        
        val tempProfile = current.copy(birthDate = birthDate)
        val isAdult = tempProfile.isUserAdult

        val updatedProfile = current.copy(
            displayName = displayName,
            nickname = nickname,
            gender = gender,
            birthDate = birthDate,
            isAdult = isAdult,
            adultPassword = adultPassword
        )

        if (uid != "offline_demo_user") {
            firestore.collection("users").document(uid).set(updatedProfile).await()
        }
        _currentUser.value = updatedProfile
        return updatedProfile
    }

    override fun getMemories(userId: String): Flow<List<Memory>> {
        scanLocalUnmaskVideos()
        return _localMemoriesFlow
    }

    override fun getPublicMemories(): Flow<List<Memory>> {
        scanLocalUnmaskVideos()
        return _localMemoriesFlow
    }

    override suspend fun addMemory(
        gameId: String,
        gameName: String,
        category: String,
        taskId: String,
        taskText: String,
        videoUri: Uri,
        isPublic: Boolean
    ): String {
        val user = _currentUser.value ?: throw IllegalStateException("Kullanıcı giriş yapmamış.")
        val dateString = android.text.format.DateFormat.format("d MMMM yyyy", java.util.Date()).toString()
        val generatedId = UUID.randomUUID().toString()

        // Save local video URI directly to the local database, no Firebase Storage or Firestore uploads
        val memory = Memory(
            id = generatedId,
            userId = user.uid,
            userName = user.displayName,
            gameId = gameId,
            gameName = gameName,
            category = category,
            taskId = taskId,
            taskText = taskText,
            videoUrl = videoUri.toString(),
            thumbnailUrl = videoUri.toString(),
            date = dateString,
            likes = 0,
            isPublic = false // Local-only
        )

        // Add to the front of local database cache so the newest video appears first
        localMemories.add(0, memory)
        _localMemoriesFlow.value = localMemories.toList()

        return generatedId
    }

    override suspend fun deleteMemory(memoryId: String) {
        val memory = localMemories.find { it.id == memoryId }
        localMemories.removeAll { it.id == memoryId }
        _localMemoriesFlow.value = localMemories.toList()

        // Delete the physical video file from device storage/MediaStore if present
        if (memory != null) {
            try {
                val uri = Uri.parse(memory.videoUrl)
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (_currentUser.value?.uid == "offline_demo_user") return

        try {
            firestore.collection("memories").document(memoryId).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun toggleMemoryPublic(memoryId: String, currentPublicStatus: Boolean) {
        val index = localMemories.indexOfFirst { it.id == memoryId }
        if (index != -1) {
            localMemories[index] = localMemories[index].copy(isPublic = !currentPublicStatus)
            _localMemoriesFlow.value = localMemories.toList()
        }

        if (_currentUser.value?.uid == "offline_demo_user") return

        try {
            firestore.collection("memories").document(memoryId)
                .update("isPublic", !currentPublicStatus)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun likeMemory(memoryId: String) {
        val index = localMemories.indexOfFirst { it.id == memoryId }
        if (index != -1) {
            localMemories[index] = localMemories[index].copy(likes = localMemories[index].likes + 1)
            _localMemoriesFlow.value = localMemories.toList()
        }

        if (_currentUser.value?.uid == "offline_demo_user") return

        try {
            firestore.collection("memories").document(memoryId)
                .update("likes", FieldValue.increment(1))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getHistory(userId: String): Flow<List<HistoryItem>> {
        if (userId == "offline_demo_user") {
            return _localHistoryFlow
        }
        return callbackFlow {
            val listener = firestore.collection("history")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(localHistory)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(HistoryItem::class.java)?.copy(id = doc.id)
                        }.sortedByDescending { it.date }
                        trySend(list)
                    }
                }
            awaitClose { listener.remove() }
        }
    }

    override suspend fun addHistoryItem(gameName: String, note: String) {
        val user = _currentUser.value ?: throw IllegalStateException("Kullanıcı giriş yapmamış.")
        val dateString = android.text.format.DateFormat.format("d MMMM yyyy", java.util.Date()).toString()
        val generatedId = UUID.randomUUID().toString()

        val item = HistoryItem(
            id = generatedId,
            userId = user.uid,
            gameName = gameName,
            date = dateString,
            note = note
        )

        localHistory.add(item)
        _localHistoryFlow.value = localHistory.toList()

        if (user.uid == "offline_demo_user") return

        try {
            firestore.collection("history").add(item)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun updateHistoryNote(itemId: String, note: String) {
        val index = localHistory.indexOfFirst { it.id == itemId }
        if (index != -1) {
            localHistory[index] = localHistory[index].copy(note = note)
            _localHistoryFlow.value = localHistory.toList()
        }

        if (_currentUser.value?.uid == "offline_demo_user") return

        try {
            firestore.collection("history").document(itemId)
                .update("note", note)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun deleteHistoryItem(itemId: String) {
        localHistory.removeAll { it.id == itemId }
        _localHistoryFlow.value = localHistory.toList()

        if (_currentUser.value?.uid == "offline_demo_user") return

        try {
            firestore.collection("history").document(itemId).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCustomGamesAndTasks() {
        try {
            if (customGamesFile.exists()) {
                val json = customGamesFile.readText()
                _customGames.value = Json.decodeFromString<List<Game>>(json)
            }
            if (customTasksFile.exists()) {
                val json = customTasksFile.readText()
                _customTasks.value = Json.decodeFromString<List<Task>>(json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun saveCustomGame(game: Game, tasks: List<Task>) {
        val updatedGames = _customGames.value.toMutableList().apply { add(game) }
        val updatedTasks = _customTasks.value.toMutableList().apply { addAll(tasks) }
        
        _customGames.value = updatedGames
        _customTasks.value = updatedTasks
        
        try {
            customGamesFile.writeText(Json.encodeToString(updatedGames))
            customTasksFile.writeText(Json.encodeToString(updatedTasks))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _simulatedSession = MutableStateFlow<OnlineSession?>(null)

    override fun getOnlineUsers(currentUserId: String): Flow<List<OnlineUserPresence>> = callbackFlow {
        if (currentUserId == "offline_demo_user" || currentUser.value?.uid == "offline_demo_user") {
            // Simulated lobby when offline/demo
            val simulatedUsers = listOf(
                OnlineUserPresence("bot_1", "Elif Demir", "idle", System.currentTimeMillis()),
                OnlineUserPresence("bot_2", "Kaan Kaya", "idle", System.currentTimeMillis()),
                OnlineUserPresence("bot_3", "Selin Yılmaz", "playing", System.currentTimeMillis())
            )
            trySend(simulatedUsers)
            awaitClose { }
        } else {
            val listener = firestore.collection("online_users")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val users = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(OnlineUserPresence::class.java)
                    }?.filter { 
                        it.userId != currentUserId && 
                        (it.status == "idle" || it.status.startsWith("searching:")) && 
                        Math.abs(System.currentTimeMillis() - it.lastActive) < 12_000 
                    } ?: emptyList()
                    trySend(users)
                }
            awaitClose { listener.remove() }
        }
    }

    override suspend fun updatePresence(userId: String, userName: String, status: String, banUntil: Long, gender: String) {
        if (userId == "offline_demo_user") return
        try {
            val presence = OnlineUserPresence(
                userId = userId,
                userName = userName,
                status = status,
                lastActive = System.currentTimeMillis(),
                banUntil = banUntil,
                score = _currentUser.value?.score ?: 100,
                gender = gender
            )
            firestore.collection("online_users").document(userId).set(presence).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun removePresence(userId: String) {
        if (userId == "offline_demo_user") return
        try {
            firestore.collection("online_users").document(userId).delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun observeActiveSession(userId: String): Flow<OnlineSession?> = callbackFlow {
        if (userId == "offline_demo_user") {
            val collectJob = kotlinx.coroutines.MainScope().launch {
                _simulatedSession.collect {
                    trySend(it)
                }
            }
            awaitClose { collectJob.cancel() }
        } else {
            val listener = firestore.collection("online_sessions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(null)
                        return@addSnapshotListener
                    }
                    val session = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(OnlineSession::class.java)
                    }?.find { (it.user1Id == userId || it.user2Id == userId) && it.status != "finished" }
                    trySend(session)
                }
            awaitClose { listener.remove() }
        }
    }

    override suspend fun createSession(
        user1Id: String,
        user1Name: String,
        user1Gender: String,
        user2Id: String,
        user2Name: String,
        user2Gender: String,
        category: String
    ): String {
        val sessionId = "session-" + UUID.randomUUID().toString()
        val session = OnlineSession(
            id = sessionId,
            user1Id = user1Id,
            user1Name = user1Name,
            user1Gender = user1Gender,
            user2Id = user2Id,
            user2Name = user2Name,
            user2Gender = user2Gender,
            status = "playing",
            commonCategory = category,
            selectedGameId = "online-matchmaking-game",
            currentTurn = user2Id, // Selected user gets first turn
            lastHeartbeat = System.currentTimeMillis()
        )
        
        if (user1Id == "offline_demo_user") {
            _simulatedSession.value = session
            
            // Simulating Bot category selection for offline play after 1500ms
            kotlinx.coroutines.MainScope().launch {
                delay(1500)
                val current = _simulatedSession.value
                if (current != null && current.user1Categories.isNotEmpty() && current.user2Categories.isEmpty()) {
                    val botCats = listOf("iliskiler", "adrenalin", "bilgi", "aktuel", "hatiralar", "fanteziler", "adult", "softhub").shuffled()
                    var bestCat = "iliskiler"
                    var minRankSum = 999
                    for (cat in botCats) {
                        val r1 = current.user1Categories.indexOf(cat)
                        val r2 = botCats.indexOf(cat)
                        if (r1 != -1 && r2 != -1) {
                            val sum = r1 + r2
                            if (sum < minRankSum) {
                                minRankSum = sum
                                bestCat = cat
                            }
                        }
                    }
                    _simulatedSession.value = current.copy(
                        user2Categories = botCats,
                        commonCategory = bestCat,
                        status = "game_selection",
                        lastHeartbeat = System.currentTimeMillis()
                    )
                }
            }
        } else {
            try {
                firestore.collection("online_sessions").document(sessionId).set(session).await()
                updatePresence(user1Id, user1Name, "playing", gender = user1Gender)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return sessionId
    }

    override suspend fun updateSession(session: OnlineSession) {
        if (session.user1Id == "offline_demo_user") {
            _simulatedSession.value = session
            
            // Simulate bot responses in category selection
            if (session.status == "category_selection" && session.user1Categories.isNotEmpty() && session.user2Categories.isEmpty()) {
                kotlinx.coroutines.MainScope().launch {
                    delay(2000)
                    val current = _simulatedSession.value
                    if (current != null) {
                        val botCats = listOf("iliskiler", "adrenalin", "bilgi", "aktuel", "hatiralar", "fanteziler", "adult", "softhub").shuffled()
                        var bestCat = "iliskiler"
                        var minRankSum = 999
                        for (cat in botCats) {
                            val r1 = current.user1Categories.indexOf(cat)
                            val r2 = botCats.indexOf(cat)
                            if (r1 != -1 && r2 != -1) {
                                val sum = r1 + r2
                                if (sum < minRankSum) {
                                    minRankSum = sum
                                    bestCat = cat
                                }
                            }
                        }
                        _simulatedSession.value = current.copy(
                            user2Categories = botCats,
                            commonCategory = bestCat,
                            status = "game_selection",
                            lastHeartbeat = System.currentTimeMillis()
                        )
                    }
                }
            }
            // Simulate bot gameplay card drawing or video answering
            else if (session.status == "playing" && session.activeCardCode.isEmpty() && session.currentTurn == "bot_1") {
                kotlinx.coroutines.MainScope().launch {
                    delay(2500)
                    val current = _simulatedSession.value
                    if (current != null) {
                        val gameTasks = Constants.TASKS.filter { it.gameId == current.selectedGameId }
                        val task = gameTasks.randomOrNull() ?: Constants.TASKS.first()
                        _simulatedSession.value = current.copy(
                            activeCardCode = task.cardCode,
                            activeTaskId = task.id,
                            lastHeartbeat = System.currentTimeMillis()
                        )
                    }
                }
            }
        } else {
            try {
                firestore.collection("online_sessions").document(session.id).set(session).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        if (_currentUser.value?.uid == "offline_demo_user") {
            _simulatedSession.value = null
        } else {
            try {
                firestore.collection("online_sessions").document(sessionId).delete().await()
                val current = _currentUser.value
                if (current != null) {
                    removePresence(current.uid)
                }
                // Clean up all temporary online videos for this session from Cloudflare R2 to reduce costs
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val clientConfig = com.amazonaws.ClientConfiguration().apply {
                            signerOverride = "AWSS3V4SignerType"
                        }
                        val s3Client = com.amazonaws.services.s3.AmazonS3Client(
                            com.amazonaws.auth.BasicAWSCredentials(
                                Constants.CLOUDFLARE_R2_ACCESS_KEY_ID,
                                Constants.CLOUDFLARE_R2_SECRET_ACCESS_KEY
                            ),
                            clientConfig
                        ).apply {
                            setRegion(com.amazonaws.regions.Region.getRegion(com.amazonaws.regions.Regions.US_EAST_1))
                            setEndpoint(Constants.CLOUDFLARE_R2_ENDPOINT)
                            setS3ClientOptions(
                                com.amazonaws.services.s3.S3ClientOptions.builder()
                                    .setPathStyleAccess(true)
                                    .disableChunkedEncoding()
                                    .build()
                            )
                        }
                        val prefix = "online_videos/$sessionId"
                        val listing = s3Client.listObjects(Constants.CLOUDFLARE_R2_BUCKET_NAME, prefix)
                        for (summary in listing.objectSummaries) {
                            s3Client.deleteObject(Constants.CLOUDFLARE_R2_BUCKET_NAME, summary.key)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun cleanActiveSessions(userId: String) {
        if (_currentUser.value?.uid == "offline_demo_user") {
            _simulatedSession.value = null
        } else {
            try {
                val query1 = firestore.collection("online_sessions")
                    .whereEqualTo("user1Id", userId)
                    .get().await()
                for (doc in query1.documents) {
                    deleteSession(doc.id)
                }

                val query2 = firestore.collection("online_sessions")
                    .whereEqualTo("user2Id", userId)
                    .get().await()
                for (doc in query2.documents) {
                    deleteSession(doc.id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun uploadOnlineVideo(sessionId: String, videoUri: Uri): String {
        if (_currentUser.value?.uid == "offline_demo_user") {
            return videoUri.toString()
        }
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                
                // 1. Copy stream to a temporary local file if it's a content URI, or read direct path if it's a file URI.
                val file = if (videoUri.scheme == "content") {
                    val tempFile = java.io.File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}.mp4")
                    resolver.openInputStream(videoUri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile
                } else {
                    java.io.File(videoUri.path ?: "")
                }

                if (!file.exists() || file.length() == 0L) {
                    throw java.io.FileNotFoundException("Video file not found or empty: ${videoUri.path}")
                }

                val key = "online_videos/${sessionId}_${System.currentTimeMillis()}.mp4"

                // 2. Initialize S3 client locally to compute Signature V4 query parameters
                val s3Client = com.amazonaws.services.s3.AmazonS3Client(
                    com.amazonaws.auth.BasicAWSCredentials(
                        Constants.CLOUDFLARE_R2_ACCESS_KEY_ID,
                        Constants.CLOUDFLARE_R2_SECRET_ACCESS_KEY
                    )
                ).apply {
                    setRegion(com.amazonaws.regions.Region.getRegion(com.amazonaws.regions.Regions.US_EAST_1))
                    setEndpoint(Constants.CLOUDFLARE_R2_ENDPOINT)
                    setS3ClientOptions(
                        com.amazonaws.services.s3.S3ClientOptions.builder()
                            .setPathStyleAccess(true)
                            .build()
                    )
                }

                // 3. Generate a presigned PUT URL (valid for 15 minutes)
                val expiration = java.util.Date(System.currentTimeMillis() + 15 * 60 * 1000)
                val presignedUrlRequest = com.amazonaws.services.s3.model.GeneratePresignedUrlRequest(
                    Constants.CLOUDFLARE_R2_BUCKET_NAME,
                    key
                ).apply {
                    method = com.amazonaws.HttpMethod.PUT
                    this.expiration = expiration
                    contentType = "video/mp4" // Explicitly sign with video/mp4 Content-Type!
                }

                val presignedUrl = s3Client.generatePresignedUrl(presignedUrlRequest).toString()

                // 4. Perform a standard OkHttp PUT request with the raw video bytes.
                // Include the signed Content-Type header to ensure R2 saves it correctly.
                val bytes = file.readBytes()
                val mediaType = "video/mp4".toMediaTypeOrNull()
                val requestBody = okhttp3.RequestBody.create(mediaType, bytes)
                val request = okhttp3.Request.Builder()
                    .url(presignedUrl)
                    .put(requestBody)
                    .header("Content-Type", "video/mp4") // Must match the signed content type
                    .build()

                val okHttpClient = okhttp3.OkHttpClient()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw java.io.IOException("Upload failed: HTTP ${response.code} ${response.message}")
                    }
                }

                // 5. Generate a presigned GET URL (valid for 1 hour) for secure, unblocked playback
                val getExpiration = java.util.Date(System.currentTimeMillis() + 60 * 60 * 1000) // 1 hour
                val presignedGetRequest = com.amazonaws.services.s3.model.GeneratePresignedUrlRequest(
                    Constants.CLOUDFLARE_R2_BUCKET_NAME,
                    key
                ).apply {
                    method = com.amazonaws.HttpMethod.GET
                    this.expiration = getExpiration
                }

                val playUrl = s3Client.generatePresignedUrl(presignedGetRequest).toString()

                // 6. Clean up the temp file if created
                if (videoUri.scheme == "content") {
                    file.delete()
                }

                playUrl
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    override suspend fun deleteOnlineVideo(videoUrl: String) {
        if (videoUrl.isEmpty() || videoUrl.contains("offline_demo_user")) return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val startIndex = videoUrl.indexOf("online_videos/")
                if (startIndex == -1) return@withContext
                
                var endIndex = videoUrl.indexOf('?', startIndex)
                if (endIndex == -1) {
                    endIndex = videoUrl.length
                }
                val key = videoUrl.substring(startIndex, endIndex)

                val clientConfig = com.amazonaws.ClientConfiguration().apply {
                    signerOverride = "AWSS3V4SignerType"
                }
                val s3Client = com.amazonaws.services.s3.AmazonS3Client(
                    com.amazonaws.auth.BasicAWSCredentials(
                        Constants.CLOUDFLARE_R2_ACCESS_KEY_ID,
                        Constants.CLOUDFLARE_R2_SECRET_ACCESS_KEY
                    ),
                    clientConfig
                ).apply {
                    setRegion(com.amazonaws.regions.Region.getRegion(com.amazonaws.regions.Regions.US_EAST_1))
                    setEndpoint(Constants.CLOUDFLARE_R2_ENDPOINT)
                    setS3ClientOptions(
                        com.amazonaws.services.s3.S3ClientOptions.builder()
                            .setPathStyleAccess(true)
                            .disableChunkedEncoding()
                            .build()
                    )
                }
                s3Client.deleteObject(Constants.CLOUDFLARE_R2_BUCKET_NAME, key)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun updateSessionHeartbeat(sessionId: String) {
        if (_currentUser.value?.uid == "offline_demo_user") {
            val current = _simulatedSession.value
            if (current != null) {
                _simulatedSession.value = current.copy(lastHeartbeat = System.currentTimeMillis())
            }
        } else {
            try {
                firestore.collection("online_sessions").document(sessionId)
                    .update("lastHeartbeat", System.currentTimeMillis()).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun banUser(userId: String, durationMs: Long) {
        val current = _currentUser.value ?: return
        val banTime = System.currentTimeMillis() + durationMs
        val updated = current.copy(banUntil = banTime)
        _currentUser.value = updated
        
        if (userId != "offline_demo_user") {
            try {
                firestore.collection("users").document(userId).update("banUntil", banTime).await()
                updatePresence(userId, current.displayName, "offline", banTime, gender = current.gender)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun rateUser(targetUserId: String, ratingStars: Int) {
        if (targetUserId == "offline_demo_user" || targetUserId.startsWith("bot_")) return
        try {
            val userRef = firestore.collection("users").document(targetUserId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentRatingCount = snapshot.getLong("ratingCount") ?: 0L
                val currentTotalRating = snapshot.getLong("totalRating") ?: 0L
                
                val newCount = currentRatingCount + 1
                val newTotal = currentTotalRating + ratingStars
                val newScore = ((newTotal * 20) / newCount).toInt().coerceAtMost(100)
                
                transaction.update(userRef, mapOf(
                    "ratingCount" to newCount,
                    "totalRating" to newTotal,
                    "score" to newScore
                ))
                null
            }.await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun publishPublicVideo(gameName: String, taskText: String, filterName: String, videoUri: Uri): String {
        val user = _currentUser.value ?: throw IllegalStateException("Kullanıcı giriş yapmamış.")
        val nsfwResult = com.example.unmask.core.NSFWDetector.analyzeVideo(context, videoUri)
        if (nsfwResult.isNSFW) {
            throw IllegalArgumentException("⚠️ Videonuz müstehcen/topluluk kurallarına aykırı içerik barındırdığı için paylaşılamaz!")
        }
        val videoUrl = uploadOnlineVideo("public_feed", videoUri)
        val docId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expires = now + 60 * 60 * 1000 // 1 hour expiration
        
        val publicVideo = PublicVideo(
            id = docId,
            userId = user.uid,
            userName = user.displayName,
            videoUrl = videoUrl,
            gameName = gameName,
            taskText = taskText,
            filterName = filterName,
            createdAt = now,
            expiresAt = expires
        )
        
        firestore.collection("public_videos").document(docId).set(publicVideo).await()
        return docId
    }

    override fun getPublicVideos(): Flow<List<PublicVideo>> = kotlinx.coroutines.flow.callbackFlow {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                cleanExpiredPublicVideos()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val listener = firestore.collection("public_videos")
            .whereGreaterThan("expiresAt", System.currentTimeMillis())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PublicVideo::class.java)
                } ?: emptyList()
                trySend(list.sortedByDescending { it.createdAt })
            }
        
        awaitClose {
            listener.remove()
        }
    }

    override suspend fun cleanExpiredPublicVideos() {
        val now = System.currentTimeMillis()
        try {
            val expiredDocs = firestore.collection("public_videos")
                .whereLessThanOrEqualTo("expiresAt", now)
                .get()
                .await()
                
            for (doc in expiredDocs.documents) {
                val videoUrl = doc.getString("videoUrl") ?: ""
                if (videoUrl.isNotEmpty()) {
                    deleteOnlineVideo(videoUrl)
                }
                firestore.collection("public_videos").document(doc.id).delete().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun toggleLikePublicVideo(videoId: String): Boolean {
        val user = _currentUser.value ?: throw IllegalStateException("Kullanıcı giriş yapmamış.")
        val docRef = firestore.collection("public_videos").document(videoId)
        var isLiked = false
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val likedBy = snapshot.get("likedBy") as? List<String> ?: emptyList()
            val mutableLikedBy = likedBy.toMutableList()
            
            if (mutableLikedBy.contains(user.uid)) {
                mutableLikedBy.remove(user.uid)
                isLiked = false
            } else {
                mutableLikedBy.add(user.uid)
                isLiked = true
            }
            val newLikesCount = mutableLikedBy.size
            transaction.update(docRef, mapOf(
                "likedBy" to mutableLikedBy,
                "likesCount" to newLikesCount
            ))
            null
        }.await()
        return isLiked
    }

    override suspend fun addCommentToPublicVideo(videoId: String, commentText: String) {
        val user = _currentUser.value ?: throw IllegalStateException("Kullanıcı giriş yapmamış.")
        val docRef = firestore.collection("public_videos").document(videoId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val commentsList = (snapshot.get("comments") as? List<Map<String, Any>> ?: emptyList()).map { map ->
                Comment(
                    id = map["id"] as? String ?: "",
                    userId = map["userId"] as? String ?: "",
                    userName = map["userName"] as? String ?: "",
                    text = map["text"] as? String ?: "",
                    createdAt = map["createdAt"] as? Long ?: 0L
                )
            }
            val mutableComments = commentsList.toMutableList()
            val newComment = Comment(
                id = UUID.randomUUID().toString(),
                userId = user.uid,
                userName = user.nickname?.takeIf { it.isNotBlank() } ?: user.displayName,
                text = commentText,
                createdAt = System.currentTimeMillis()
            )
            mutableComments.add(newComment)
            transaction.update(docRef, "comments", mutableComments)
            null
        }.await()
    }

    override fun getOnlineHistoryOpponents(userId: String): Flow<List<OnlineOpponentHistory>> = callbackFlow {
        if (userId == "offline_demo_user") {
            trySend(listOf(
                OnlineOpponentHistory(
                    opponentId = "demo_opp_1",
                    opponentName = "Ahmet",
                    opponentGender = "Erkek",
                    lastPlayedTimestamp = System.currentTimeMillis() - 300_000,
                    lastCategory = "iliskiler"
                )
            ))
            awaitClose { }
        } else {
            val listener = firestore.collection("online_sessions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val sessions = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(OnlineSession::class.java)
                    }.filter { it.user1Id == userId || it.user2Id == userId }

                    val grouped = sessions.groupBy { session ->
                        if (session.user1Id == userId) session.user2Id else session.user1Id
                    }

                    val opponents = grouped.mapNotNull { (oppId, oppSessions) ->
                        if (oppId.isEmpty()) return@mapNotNull null
                        val latestSession = oppSessions.maxByOrNull { it.lastHeartbeat } ?: return@mapNotNull null
                        val isUser1 = latestSession.user1Id == userId
                        val oppName = if (isUser1) latestSession.user2Name else latestSession.user1Name
                        val oppGender = if (isUser1) latestSession.user2Gender else latestSession.user1Gender
                        OnlineOpponentHistory(
                            opponentId = oppId,
                            opponentName = oppName.ifEmpty { "Oyuncu" },
                            opponentGender = oppGender,
                            lastPlayedTimestamp = latestSession.lastHeartbeat,
                            lastCategory = latestSession.commonCategory
                        )
                    }.sortedByDescending { it.lastPlayedTimestamp }

                    trySend(opponents)
                }
            awaitClose { listener.remove() }
        }
    }

    override fun getAllUserPresences(): Flow<List<OnlineUserPresence>> = callbackFlow {
        val listener = firestore.collection("online_users")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val presences = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(OnlineUserPresence::class.java)
                }
                trySend(presences)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun sendDirectGameRequest(
        senderId: String,
        senderNickname: String,
        senderGender: String,
        receiverId: String,
        receiverNickname: String,
        receiverGender: String
    ): String {
        val requestId = "req-" + UUID.randomUUID().toString()
        val request = DirectGameRequest(
            id = requestId,
            senderId = senderId,
            senderNickname = senderNickname,
            senderGender = senderGender,
            receiverId = receiverId,
            receiverNickname = receiverNickname,
            receiverGender = receiverGender,
            status = "pending",
            createdAt = System.currentTimeMillis()
        )
        firestore.collection("direct_game_requests").document(requestId).set(request).await()
        return requestId
    }

    override fun observeIncomingGameRequests(userId: String): Flow<List<DirectGameRequest>> = callbackFlow {
        val listener = firestore.collection("direct_game_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val requests = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DirectGameRequest::class.java)
                }.filter { it.receiverId == userId && (it.status == "pending" || it.status == "lobby_selected") }
                 .sortedByDescending { it.createdAt }
                trySend(requests)
            }
        awaitClose { listener.remove() }
    }

    override fun observeSentGameRequests(userId: String): Flow<List<DirectGameRequest>> = callbackFlow {
        val listener = firestore.collection("direct_game_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val requests = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DirectGameRequest::class.java)
                }.filter { it.senderId == userId && (it.status == "pending" || it.status == "lobby_selected") }
                 .sortedByDescending { it.createdAt }
                trySend(requests)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun acceptDirectGameRequest(request: DirectGameRequest, selectedCategory: String) {
        try {
            firestore.collection("direct_game_requests").document(request.id).update(
                mapOf(
                    "status" to "lobby_selected",
                    "selectedCategory" to selectedCategory
                )
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun launchSessionFromDirectRequest(request: DirectGameRequest): String {
        val sessionId = createSession(
            user1Id = request.senderId,
            user1Name = request.senderNickname,
            user1Gender = request.senderGender,
            user2Id = request.receiverId,
            user2Name = request.receiverNickname,
            user2Gender = request.receiverGender,
            category = request.selectedCategory
        )

        try {
            firestore.collection("direct_game_requests").document(request.id).update(
                mapOf(
                    "status" to "playing",
                    "sessionId" to sessionId
                )
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return sessionId
    }

    override suspend fun rejectDirectGameRequest(requestId: String) {
        try {
            firestore.collection("direct_game_requests").document(requestId).update("status", "rejected").await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun cancelSentGameRequest(requestId: String) {
        try {
            firestore.collection("direct_game_requests").document(requestId).delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun verifyAndUpdateAdultPassword(inputPassword: String): Boolean {
        val user = _currentUser.value ?: return false
        val savedPassword = user.adultPassword ?: ""
        
        if (savedPassword.isNotEmpty() && inputPassword == savedPassword) {
            return true
        }

        if (savedPassword.isEmpty()) {
            return true
        }

        val email = auth.currentUser?.email
        if (!email.isNullOrEmpty()) {
            try {
                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, inputPassword)
                auth.currentUser?.reauthenticate(credential)?.await()
                updateProfile(user.displayName, user.nickname ?: "", user.gender, user.birthDate, inputPassword)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private suspend fun sendDirectEmail(toEmail: String, code: String) {
        withContext(Dispatchers.IO) {
            val subject = "Unmask Adult Şifre Değişikliği"
            val messageText = "unmask adult şifre değişikliği talebiniz alındı. $code kodu ilgili yere yazınız."
            
            // 1. Write to Firestore mail queue for Firebase Trigger Email
            try {
                firestore.collection("mail").add(
                    mapOf(
                        "to" to listOf(toEmail),
                        "message" to mapOf(
                            "subject" to subject,
                            "text" to messageText
                        )
                    )
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Direct HTTP Dispatch via Transactional REST Mail API
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val jsonBody = """
                    {
                        "sender": {"name": "Unmask AI", "email": "noreply@unmaskai.com"},
                        "to": [{"email": "$toEmail"}],
                        "subject": "$subject",
                        "textContent": "$messageText"
                    }
                """.trimIndent()

                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = okhttp3.RequestBody.create(mediaType, jsonBody)
                
                val request = okhttp3.Request.Builder()
                    .url("https://api.brevo.com/v3/smtp/email")
                    .addHeader("accept", "application/json")
                    .addHeader("api-key", "xkeysib-live-smtp-unmask-verification")
                    .post(body)
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun sendAdultPasswordResetCode(email: String): String {
        val code = (100_000..999_999).random().toString()
        val uid = _currentUser.value?.uid ?: "demo"
        val emailMessage = "unmask adult şifre değişikliği talebiniz alındı. $code kodu ilgili yere yazınız."

        try {
            if (uid != "offline_demo_user") {
                // Save verification code record
                firestore.collection("adult_verification_codes").document(uid).set(
                    mapOf(
                        "email" to email,
                        "code" to code,
                        "emailMessage" to emailMessage,
                        "createdAt" to System.currentTimeMillis()
                    )
                ).await()

                // Trigger direct email dispatch
                sendDirectEmail(email, code)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return code
    }

    override suspend fun verifyAdultPasswordResetCode(email: String, code: String): Boolean {
        val uid = _currentUser.value?.uid ?: "demo"
        if (uid == "offline_demo_user") return true
        try {
            val doc = firestore.collection("adult_verification_codes").document(uid).get().await()
            if (doc.exists()) {
                val savedCode = doc.getString("code")
                return savedCode != null && savedCode == code.trim()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    override suspend fun saveSecurityAnswers(answers: Map<String, String>) {
        val uid = _currentUser.value?.uid ?: return
        if (uid == "offline_demo_user") return
        try {
            // Normalize answers: lowercase & trim
            val normalized = answers.mapValues { it.value.trim().lowercase() }
            firestore.collection("users").document(uid).update("securityAnswers", normalized).await()
            val updated = _currentUser.value?.copy(securityAnswers = normalized)
            if (updated != null) _currentUser.value = updated
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun verifySecurityAnswer(question: String, answer: String): Boolean {
        val uid = _currentUser.value?.uid ?: return false
        if (uid == "offline_demo_user") return true
        try {
            val storedAnswers = _currentUser.value?.securityAnswers ?: emptyMap()
            val stored = storedAnswers[question]?.trim()?.lowercase()
            return stored != null && stored == answer.trim().lowercase()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    override fun getStoredSecurityQuestions(): Map<String, String> {
        return _currentUser.value?.securityAnswers ?: emptyMap()
    }
}

