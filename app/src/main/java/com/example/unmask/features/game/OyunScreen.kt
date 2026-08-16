package com.example.unmask.features.game

import androidx.compose.runtime.Composable
import com.example.unmask.data.Game
import com.example.unmask.data.UserProfile
import com.example.unmask.features.online.DunyaScreen

@Composable
fun OyunScreen(
    user: UserProfile?,
    activeGame: Game?,
    customGames: List<Game> = emptyList(),
    repository: com.example.unmask.data.DataRepository,
    initialOnlineCategory: String? = null,
    onStartSession: (Game) -> Unit,
    onEndSession: () -> Unit,
    onNavigateToQR: (String?) -> Unit,
    onMenuClick: () -> Unit
) {
    DunyaScreen(
        repository = repository,
        initialCategory = initialOnlineCategory,
        onBack = { },
        onMenuClick = onMenuClick
    )
}
