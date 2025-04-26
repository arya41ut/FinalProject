package com.example.finalproject

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.finalproject.auth.FirebaseAuthViewModel
import com.example.finalproject.auth.FirebaseAuthViewModelFactory
import com.example.finalproject.auth.LocalAuthState
import com.example.finalproject.ui.screens.HomeScreen
import com.example.finalproject.ui.screens.LoginScreen
import com.example.finalproject.ui.screens.SpinRoomScreen
import com.example.finalproject.ui.screens.RestaurantChatScreen
import com.example.finalproject.ui.screens.GameResultScreen
import com.example.finalproject.ui.screens.CalendarScreen
import com.example.finalproject.ui.theme.FinalProjectTheme
import com.google.firebase.FirebaseApp
import com.example.finalproject.ui.screens.SpinRoomViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        try {
            FirebaseApp.initializeApp(this)
            Log.d("MainActivity", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing Firebase", e)
        }
        
        initializeWebSocket()
        
        setContent {
            FinalProjectTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation()
                    }
                }
            }
        }
    }
    
    private fun initializeWebSocket() {
        try {
            Log.d("MainActivity", "Initializing WebSocket client")
            com.example.finalproject.websocket.RoomWebSocketClient
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing WebSocket: ${e.message}", e)
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: FirebaseAuthViewModel = viewModel(factory = FirebaseAuthViewModelFactory(MainApplication.instance))
    val authState by authViewModel.authState.collectAsState()
    
    val startDestination = when (authState) {
        is LocalAuthState.LoggedIn -> "home"
        else -> "login"
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(
                onSignInSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        
        composable("home") {
            HomeScreen(
                onCreateRoom = { roomCode ->
                    navController.navigate("spin-room/$roomCode")
                },
                onJoinRoom = { message ->
                    val roomCode = if (message.contains("code:")) {
                        message.substringAfterLast("code:").trim()
                    } else {
                        message
                    }
                    navController.navigate("spin-room/$roomCode")
                },
                onNavigateToCalendar = {
                    navController.navigate("calendar?restaurant=null")
                }
            )
        }
        
        composable(
            route = "calendar?restaurant={restaurant}",
            arguments = listOf(
                navArgument("restaurant") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val selectedRestaurant = backStackEntry.arguments?.getString("restaurant")
            CalendarScreen(
                onBackToHome = {
                    navController.popBackStack()
                },
                selectedRestaurant = selectedRestaurant
            )
        }
        
        composable("spin-room/{roomCode}") { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: ""
            SpinRoomScreen(
                inviteCode = roomCode,
                participantCount = 1,
                isRoomReady = false,
                onStart = { 
                    navController.navigate("restaurant-chat/$roomCode")
                },
                onCancel = { navController.popBackStack() }
            )
        }
        
        composable("restaurant-chat/{roomCode}") { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: ""
            
            val authState by authViewModel.authState.collectAsState()
            val currentUser = when (authState) {
                is LocalAuthState.LoggedIn -> (authState as LocalAuthState.LoggedIn).user
                else -> null
            }
            
            if (currentUser != null) {
                RestaurantChatScreen(
                    roomCode = roomCode,
                    currentUsername = currentUser.username,
                    participants = listOf(currentUser.username, "Other User"),
                    onBackClick = { navController.popBackStack() },
                    onFinish = { suggestions ->
                        val suggestionsJson = com.google.gson.Gson().toJson(suggestions)
                        navController.navigate("game-result/$roomCode/$suggestionsJson")
                    }
                )
            }
        }
        
        composable(
            route = "game-result/{roomCode}/{suggestions}",
            arguments = listOf(
                navArgument("roomCode") { type = NavType.StringType },
                navArgument("suggestions") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: ""
            val suggestionsJson = backStackEntry.arguments?.getString("suggestions") ?: "[]"
            
            val suggestions = try {
                com.google.gson.Gson().fromJson(suggestionsJson, Array<String>::class.java).toList()
            } catch (e: Exception) {
                listOf<String>()
            }
            
            val authState by authViewModel.authState.collectAsState()
            val currentUser = when (authState) {
                is LocalAuthState.LoggedIn -> (authState as LocalAuthState.LoggedIn).user
                else -> null
            }
            
            val participants = if (currentUser != null) {
                listOf(currentUser.username, "Other User")
            } else {
                listOf("User1", "User2")
            }
            
            GameResultScreen(
                roomCode = roomCode,
                participants = participants,
                restaurantSuggestions = suggestions,
                onBackToHome = { 
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onScheduleToCalendar = { restaurant ->
                    navController.navigate("calendar?restaurant=${restaurant}")
                }
            )
        }
    }
}

@Preview
@Composable
fun MainPreview() {
    FinalProjectTheme {
        AppNavigation()
    }
}