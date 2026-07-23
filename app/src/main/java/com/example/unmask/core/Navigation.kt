package com.example.unmask.core

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.unmask.data.DefaultDataRepository
import com.example.unmask.features.auth.LoginScreen
import com.example.unmask.features.profile.ProfileScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.unmask.features.lobby.MainAppScreen
import com.example.unmask.features.lobby.QRScreen
import com.example.unmask.features.game.CameraTaskScreen

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val repository = remember { DefaultDataRepository(context.applicationContext) }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                repository = repository,
                onLoginSuccess = { 
                    navController.navigate("profile") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onLoginAlreadyCompleted = {
                    navController.navigate("mainTabApp") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("profile") {
            ProfileScreen(
                repository = repository,
                onProfileSaved = { 
                    navController.navigate("mainTabApp") {
                        popUpTo("profile") { inclusive = true }
                    }
                }
            )
        }
        composable("mainTabApp") {
            MainAppScreen(
                repository = repository,
                onNavigateToQR = { gameId ->
                    if (gameId != null) {
                        navController.navigate("qr?gameId=$gameId")
                    } else {
                        navController.navigate("qr")
                    }
                },
                onLogout = { 
                    navController.navigate("login") {
                        popUpTo("mainTabApp") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "qr?gameId={gameId}",
            arguments = listOf(
                navArgument("gameId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId")
            QRScreen(
                repository = repository,
                activeGameId = gameId,
                onBack = { 
                    navController.popBackStack() 
                },
                onCardScanned = { targetGameId, taskId ->
                    navController.navigate("cameraTask/$targetGameId/$taskId")
                }
            )
        }
        composable("cameraTask/{gameId}/{taskId}") { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId") ?: ""
            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
            CameraTaskScreen(
                repository = repository,
                gameId = gameId,
                taskId = taskId,
                onBack = { 
                    navController.popBackStack() 
                },
                onRecordingFinished = {
                    // Pop back to mainTabApp by popping cameraTask and qr
                    navController.popBackStack("mainTabApp", false)
                }
            )
        }
    }
}
