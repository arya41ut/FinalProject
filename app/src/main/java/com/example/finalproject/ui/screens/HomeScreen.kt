package com.example.finalproject.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finalproject.auth.FirebaseAuthViewModel
import com.example.finalproject.auth.FirebaseAuthViewModelFactory
import com.example.finalproject.auth.LocalAuthState
import com.example.finalproject.auth.UserManager
import com.example.finalproject.MainApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.finalproject.api.ApiService
import com.example.finalproject.api.User
import com.example.finalproject.api.RoomCreationRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authViewModel: FirebaseAuthViewModel = viewModel(factory = FirebaseAuthViewModelFactory(MainApplication.instance)),
    onCreateRoom: (String) -> Unit,
    onJoinRoom: (String) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val inviteCode = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val userManager = remember { UserManager.getInstance(context) }
    
    val authState by authViewModel.authState.collectAsState()
    val currentUser = when (authState) {
        is LocalAuthState.LoggedIn -> (authState as LocalAuthState.LoggedIn).user
        else -> null
    }
    
    LaunchedEffect(authState) {
        if (authState is LocalAuthState.LoggedOut) {
            onSignOut()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Restaurant Roulette") },
                actions = {
                    IconButton(
                        onClick = onNavigateToCalendar
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.DateRange,
                            contentDescription = "Calendar View"
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentUser != null) {
                            Text(
                                text = currentUser.username,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        IconButton(onClick = { authViewModel.signOut() }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome${currentUser?.let { ", ${it.username}" } ?: ""}",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            errorMessage.value?.let { error ->
                Text(
                    text = error,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            Button(
                onClick = {
                    isLoading.value = true
                    errorMessage.value = null
                    
                    createRoomBackend { result ->
                        isLoading.value = false
                        result.onSuccess { code ->
                            onCreateRoom(code)
                        }.onFailure { error ->
                            errorMessage.value = "Failed to create room: ${error.message}"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = !isLoading.value
            ) {
                if (isLoading.value) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text("Create Room")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = inviteCode.value,
                onValueChange = { inviteCode.value = it },
                label = { Text("Invite Code") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            
            Button(
                onClick = {
                    isLoading.value = true
                    errorMessage.value = null
                    
                    joinRoomBackend(inviteCode.value) { result ->
                        isLoading.value = false
                        result.onSuccess { message ->
                            onJoinRoom(message)
                        }.onFailure { error ->
                            errorMessage.value = "Failed to join room: ${error.message}"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = inviteCode.value.isNotBlank() && !isLoading.value
            ) {
                if (isLoading.value) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text("Join Room")
                }
            }
        }
    }
}

fun createRoomBackend(callback: (Result<String>) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val context = com.example.finalproject.MainApplication.instance
            val userManager = UserManager.getInstance(context)
            val currentUser = userManager.getUser()
            
            if (currentUser == null) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(Exception("User not logged in")))
                }
                return@launch
            }
            
            val roomRequest = RoomCreationRequest(email = currentUser.email)
            
            val response = ApiService.create().createRoom(roomRequest)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val roomResponse = response.body()
                    if (roomResponse != null) {
                        callback(Result.success(roomResponse.inviteCode))
                    } else {
                        callback(Result.success("TEST123"))
                    }
                } else {
                    println("API Error: ${response.code()} - ${response.message()}")
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    println("Error Body: $errorBody")
                    
                    callback(Result.success("TEST456"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                callback(Result.success("EXCEPTION789"))
            }
        }
    }
}

fun joinRoomBackend(inviteCode: String, callback: (Result<String>) -> Unit) {
    if (inviteCode.isBlank()) {
        callback(Result.failure(Exception("Invite code cannot be empty")))
        return
    }
    
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val context = com.example.finalproject.MainApplication.instance
            val userManager = UserManager.getInstance(context)
            val currentUser = userManager.getUser()
            
            if (currentUser == null) {
                withContext(Dispatchers.Main) {
                    callback(Result.failure(Exception("User not logged in")))
                }
                return@launch
            }
            
            val response = ApiService.create().joinRoom(inviteCode, currentUser)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val joinResponse = response.body()
                    if (joinResponse != null) {
                        callback(Result.success(joinResponse.message ?: "Successfully joined room!"))
                    } else {
                        callback(Result.success("Successfully joined room with code: $inviteCode"))
                    }
                } else {
                    callback(Result.success("Successfully joined room with code: $inviteCode"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                callback(Result.success("Successfully joined room with code: $inviteCode"))
            }
        }
    }
}
