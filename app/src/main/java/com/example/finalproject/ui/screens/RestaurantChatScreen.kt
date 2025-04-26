package com.example.finalproject.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject.model.RestaurantSuggestion
import com.example.finalproject.ui.theme.FinalProjectTheme
import com.example.finalproject.api.ChatGptHelper
import com.example.finalproject.websocket.RoomWebSocketClient
import com.example.finalproject.websocket.RoomWebSocketListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.finalproject.service.RestaurantSelectionService.SelectionStrategy
import androidx.compose.ui.text.font.FontStyle

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val message: String,
    val isCurrentUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantChatScreen(
    roomCode: String,
    currentUsername: String,
    participants: List<String>,
    suggestionsPerUser: Int = 4,
    onBackClick: () -> Unit = {},
    onFinish: (List<String>) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var aiPromptText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(listOf()) }
    var restaurantSuggestions by remember { mutableStateOf<List<String>>(listOf()) }
    var hasFinishedSuggestions by remember { mutableStateOf(false) }
    var isAiLoading by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(true) }
    var isTransitioningToResults by remember { mutableStateOf(false) }
    
    var selectedRestaurant by remember { mutableStateOf("") }
    var selectionExplanation by remember { mutableStateOf("") }
    var showSelectionDialog by remember { mutableStateOf(false) }
    var showSelectionOptionsDialog by remember { mutableStateOf(false) }
    
    val chatGptHelper = remember { ChatGptHelper() }
    val scope = rememberCoroutineScope()
    
    var observedParticipants by remember { mutableStateOf((participants + "Other User").toSet()) }
    
    val suggestionCounts = remember { mutableStateMapOf<String, Int>() }
    participants.forEach { suggestionCounts[it] = 0 }
    suggestionCounts["Other User"] = 0
    
    val totalSuggestions = messages
        .filter { it.message.startsWith("I suggest:") }
        .distinctBy { 
            val normalizedSender = if (it.sender.contains("@")) {
                it.sender.substringBefore("@")
            } else {
                it.sender 
            }
            "${normalizedSender}:${it.message.substringAfter("I suggest:").trim()}"
        }
        .count()
    val requiredSuggestions = participants.size * suggestionsPerUser
    
    fun getAiSuggestions(prompt: String) {
        if (prompt.isBlank()) return
        
        val userSuggestionCount = suggestionCounts[currentUsername] ?: 0
        val remainingCount = suggestionsPerUser - userSuggestionCount
        
        if (remainingCount <= 0) {
            messages = messages + ChatMessage(
                sender = "System",
                message = "You've already made your $suggestionsPerUser suggestions. Wait for others to finish.",
                isCurrentUser = false
            )
            return
        }
        
        isAiLoading = true
        
        messages = messages + ChatMessage(
            sender = "AI Assistant",
            message = "Generating restaurant suggestions for: \"$prompt\"...",
            isCurrentUser = false
        )
        
        scope.launch {
            chatGptHelper.getRestaurantSuggestions(prompt).fold(
                onSuccess = { suggestions ->
                    val aiMessage = ChatMessage(
                        sender = "AI Assistant",
                        message = "Click on a restaurant to add it to your suggestions:",
                        isCurrentUser = false
                    )
                    
                    val suggestionMessages = suggestions.map { suggestion ->
                        ChatMessage(
                            sender = "AI Suggestion",
                            message = suggestion,
                            isCurrentUser = false
                        )
                    }
                    
                    messages = messages.filterNot { 
                        it.sender == "AI Assistant" && it.message.startsWith("Generating")
                    } + aiMessage + suggestionMessages
                    
                    aiPromptText = ""
                },
                onFailure = { error ->
                    messages = messages.filterNot { it.sender == "AI Assistant" && it.message.startsWith("Generating") } +
                        ChatMessage(
                            sender = "AI Assistant",
                            message = "Sorry, I couldn't generate suggestions: ${error.message}",
                            isCurrentUser = false
                        )
                }
            )
            
            isAiLoading = false
        }
    }
    
    val webSocketListener = remember {
        object : RoomWebSocketListener {
            override fun onRoomJoined(roomCode: String) {
                Log.d("RestaurantChatScreen", "onRoomJoined called, roomCode: $roomCode")
                scope.launch {
                    messages = messages.filterNot { it.sender == "System" && (
                        it.message == "Disconnected from the room" || 
                        it.message.startsWith("Error:")
                    )}
                    
                    messages = messages + ChatMessage(
                        sender = "System",
                        message = "Connected to room: $roomCode",
                        isCurrentUser = false
                    )
                }
            }
            
            override fun onParticipantUpdate(
                roomCode: String, 
                count: Int, 
                isReady: Boolean, 
                participants: List<String>?
            ) {
                scope.launch {
                    messages = messages.filterNot { message ->
                        message.sender == "System" && (
                            message.message == "Disconnected from the room" ||
                            message.message == "Error: Session closed." ||
                            message.message.startsWith("Error: ")
                        )
                    }
                }
            }
            
            override fun onError(roomCode: String, message: String) {
                Log.d("RestaurantChatScreen", "onError called, roomCode: $roomCode, message: $message")
                scope.launch {
                    messages = messages + ChatMessage(
                        sender = "System",
                        message = "Error: $message",
                        isCurrentUser = false
                    )
                    
                    if (message.contains("Session closed") || 
                        message.contains("Connection error") || 
                        message.contains("timeout")) {
                        Log.d("RestaurantChatScreen", "Connection error detected: $message")
                    }
                }
            }
            
            override fun onDisconnected(roomCode: String) {
                Log.d("RestaurantChatScreen", "onDisconnected called, roomCode: $roomCode")
                scope.launch {
                    messages = messages + ChatMessage(
                        sender = "System",
                        message = "Disconnected from the room",
                        isCurrentUser = false
                    )
                    
                    Log.d("RestaurantChatScreen", "Room disconnected: $roomCode")
                }
            }
            
            override fun onRestaurantSuggestion(roomCode: String, userId: String, suggestion: String) {
                scope.launch {
                    Log.d("RestaurantChatScreen", "Received restaurant suggestion via WebSocket from $userId: '$suggestion'")
                    
                    Log.d("RestaurantChatScreen", "currentUsername=$currentUsername, userId=$userId")
                    
                    messages = messages.filterNot { message ->
                        message.sender == "System" && (
                            message.message == "Disconnected from the room" ||
                            message.message == "Error: Session closed." ||
                            message.message.startsWith("Error: ")
                        )
                    }
                    
                    val hasTempMessage = messages.any { message ->
                        message.id.startsWith("temp-") && 
                        message.message == "I suggest: $suggestion" && 
                        message.sender == currentUsername
                    }
                    
                    Log.d("RestaurantChatScreen", "Has temp message for this suggestion: $hasTempMessage")
                    
                    observedParticipants = observedParticipants.plus(userId)
                    
                    if (userId.contains("@")) {
                        val displayName = userId.substringBefore("@")
                        observedParticipants = observedParticipants.plus(displayName)
                    }
                    
                    Log.d("RestaurantChatScreen", "Current observed participants: $observedParticipants")
                    
                    val suggestionText = "I suggest: $suggestion"
                    val isDuplicate = messages.any { message ->
                        !message.id.startsWith("temp-") && message.message == suggestionText
                    }
                    
                    Log.d("RestaurantChatScreen", "Is duplicate suggestion: $isDuplicate (from $userId, suggestion: '$suggestion')")
                    
                    if (!isDuplicate) {
                        messages = messages.filterNot { message ->
                            message.id.startsWith("temp-") && message.message == "I suggest: $suggestion"
                        }
                        
                        val isFromCurrentUser = hasTempMessage || 
                                              userId == currentUsername || 
                                              (userId.contains("@") && userId.substringBefore("@") == currentUsername)
                        
                        Log.d("RestaurantChatScreen", "isFromCurrentUser determined to be: $isFromCurrentUser")
                        
                        val actualSender = if (userId == "unknown" || userId == "Unknown" || userId.isBlank()) {
                            if (isFromCurrentUser || hasTempMessage) currentUsername else "Unknown User"
                        } else if (isFromCurrentUser) {
                            currentUsername
                        } else {
                            userId
                        }
                        
                        Log.d("RestaurantChatScreen", "Using actualSender: $actualSender")
                        
                        if (actualSender == "Unknown User" || actualSender == "Unknown") {
                            observedParticipants = observedParticipants.plus("Other User")
                        }
                        
                        messages = messages + ChatMessage(
                            sender = actualSender,
                            message = "I suggest: $suggestion",
                            isCurrentUser = isFromCurrentUser
                        )
                        
                        Log.d("RestaurantChatScreen", "Added suggestion from $actualSender (isCurrentUser=$isFromCurrentUser)")
                    } else {
                        Log.d("RestaurantChatScreen", "Skipped adding duplicate suggestion: $suggestion from $userId")
                        
                        messages = messages.filterNot { message ->
                            message.id.startsWith("temp-") && message.message == "I suggest: $suggestion"
                        }
                    }
                }
            }
            
            override fun onRestaurantSelected(roomCode: String, restaurant: String, explanation: String) {
                scope.launch {
                    Log.d("RestaurantChatScreen", "Received restaurant selection: $restaurant, explanation: $explanation")
                    
                    selectedRestaurant = restaurant
                    selectionExplanation = explanation
                    
                    showSelectionDialog = true
                    
                    messages = messages + ChatMessage(
                        sender = "System",
                        message = "🎉 Restaurant selected: $restaurant 🎉",
                        isCurrentUser = false
                    )
                    
                    messages = messages + ChatMessage(
                        sender = "System",
                        message = "Reason: $explanation",
                        isCurrentUser = false
                    )
                }
            }
        }
    }
    
    fun connectToRoom() {
        val userManager = com.example.finalproject.auth.UserManager.getInstance(com.example.finalproject.MainApplication.instance)
        val currentUser = userManager.getUser()
        
        if (currentUser != null) {
            Log.d("RestaurantChatScreen", "Current user details - Username: ${currentUser.username}, Email: ${currentUser.email}, ID: ${currentUser.id}")
            
            currentUser.email?.let { email ->
                observedParticipants = observedParticipants.plus(email)
                Log.d("RestaurantChatScreen", "Added current user email to observed participants: $email")
            }
            
            Log.d("RestaurantChatScreen", "Joining WebSocket room: $roomCode as ${currentUser.username}")
            RoomWebSocketClient.joinRoom(roomCode, currentUser, webSocketListener)
        } else {
            Log.w("RestaurantChatScreen", "Adding listener without joining room: no current user")
            RoomWebSocketClient.addListener(roomCode, webSocketListener)
        }
    }
    
    LaunchedEffect(roomCode) {
        connectToRoom()
    }
    
    DisposableEffect(roomCode) {
        onDispose {
            if (!isTransitioningToResults) {
                val userManager = com.example.finalproject.auth.UserManager.getInstance(com.example.finalproject.MainApplication.instance)
                val currentUser = userManager.getUser()
                
                Log.d("RestaurantChatScreen", "Leaving WebSocket room: $roomCode")
                if (currentUser != null) {
                    RoomWebSocketClient.leaveRoom(roomCode, currentUser)
                } else {
                    RoomWebSocketClient.leaveRoom(roomCode)
                }
                RoomWebSocketClient.removeListener(roomCode, webSocketListener)
            } else {
                Log.d("RestaurantChatScreen", "Skipping leaveRoom since we're transitioning to results")
                RoomWebSocketClient.removeListener(roomCode, webSocketListener)
            }
        }
    }
    
    LaunchedEffect(messages.size) {
        Log.d("RestaurantChatScreen", "Updating counts based on messages (count: ${messages.size})")
        
        val newSuggestionCounts = mutableMapOf<String, Int>()
        
        val uniqueSuggestions = mutableSetOf<String>()
        
        val processedSuggestions = mutableMapOf<String, String>()
        val currentUserSuggestions = mutableSetOf<String>()
        
        messages.forEach { message ->
            if (message.message.startsWith("I suggest:") && !message.id.startsWith("temp-")) {
                val suggestion = message.message.substringAfter("I suggest:").trim()
                val normalizedSender = if (message.sender.contains("@")) message.sender.substringBefore("@") else message.sender
                
                Log.d("RestaurantChatScreen", "Processing message: $suggestion from $normalizedSender (isCurrentUser=${message.isCurrentUser})")
                
                if (message.isCurrentUser || normalizedSender == currentUsername) {
                    currentUserSuggestions.add(suggestion)
                    Log.d("RestaurantChatScreen", "Added to current user suggestions: $suggestion")
                }
                
                if (!uniqueSuggestions.contains(suggestion)) {
                    uniqueSuggestions.add(suggestion)
                    
                    if (message.isCurrentUser || normalizedSender == currentUsername) {
                        processedSuggestions[suggestion] = currentUsername
                    } else if (normalizedSender == "Unknown" || normalizedSender == "unknown" || normalizedSender == "Unknown User") {
                        if (!processedSuggestions.containsKey(suggestion)) {
                            processedSuggestions[suggestion] = "Other User"
                        }
                    } else {
                        processedSuggestions[suggestion] = normalizedSender
                    }
                }
            }
        }
        
        processedSuggestions.forEach { (suggestion, sender) ->
            newSuggestionCounts[sender] = (newSuggestionCounts[sender] ?: 0) + 1
            Log.d("RestaurantChatScreen", "Attributing suggestion '$suggestion' to $sender")
        }
        
        if (currentUserSuggestions.isNotEmpty()) {
            val userCount = currentUserSuggestions.size
            newSuggestionCounts[currentUsername] = userCount
            Log.d("RestaurantChatScreen", "Current user ($currentUsername) has $userCount suggestions: $currentUserSuggestions")
        }
        
        suggestionCounts.clear()
        participants.forEach { suggestionCounts[it] = 0 }
        suggestionCounts["Other User"] = 0
        
        newSuggestionCounts.forEach { (user, count) ->
            suggestionCounts[user] = count
            Log.d("RestaurantChatScreen", "Final count for user: $user = $count")
        }
        
        val messageSenders = messages
            .filter { message -> message.message.startsWith("I suggest:") && !message.id.startsWith("temp-") }
            .map { message -> message.sender }
            .toSet()
        
        if (messageSenders.isNotEmpty()) {
            observedParticipants = observedParticipants.plus(messageSenders)
            if (messageSenders.any { it == "Unknown" || it == "unknown" || it == "Unknown User" }) {
                observedParticipants = observedParticipants.plus("Other User")
            }
        }
        
        restaurantSuggestions = uniqueSuggestions.toList()
    }
    
    LaunchedEffect(totalSuggestions) {
        if (totalSuggestions >= requiredSuggestions && !hasFinishedSuggestions) {
            hasFinishedSuggestions = true
            delay(1000)
            isTransitioningToResults = true
            onFinish(restaurantSuggestions)
        }
    }
    
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(key1 = true) {
        messages = listOf(
            ChatMessage(
                sender = "System",
                message = "Welcome to Restaurant Roulette! Each person should suggest $suggestionsPerUser restaurants.\n\nTo suggest a restaurant, type: I suggest: [restaurant name]\n\nYou can also use the AI assistant to get restaurant suggestions.",
                isCurrentUser = false
            )
        )
    }
    
    fun sendSuggestionViaWebSocket(suggestion: String) {
        val byteLength = suggestion.toByteArray(Charsets.UTF_8).size
        Log.d("RestaurantChatScreen", "Attempting to send suggestion via WebSocket: '$suggestion' (${suggestion.length} chars, $byteLength bytes)")
        
        val stompFrame = """
            SEND
            destination:/app/room/$roomCode/suggestion
            content-type:application/json
            content-length:$byteLength
            
            $suggestion\u0000
        """.trimIndent()
        
        Log.d("RestaurantChatScreen", "STOMP frame format for debugging:\n$stompFrame")
        
        scope.launch {
            var sendSuccess = false
            var errorMessage = ""
            
            try {
                RoomWebSocketClient.sendRestaurantSuggestion(roomCode, suggestion)
                Log.d("RestaurantChatScreen", "Suggestion sent successfully to room $roomCode")
                sendSuccess = true
            } catch (e: Exception) {
                Log.e("RestaurantChatScreen", "Error in initial send attempt: ${e.message}")
                errorMessage = e.message ?: "Unknown error"
                
                try {
                    Log.d("RestaurantChatScreen", "Attempting to reconnect and resend")
                } catch (e2: Exception) {
                    Log.e("RestaurantChatScreen", "Failed to send after reconnection: ${e2.message}")
                    errorMessage = e2.message ?: "Failed to reconnect"
                }
            }
            
            if (!sendSuccess) {
                messages = messages + ChatMessage(
                    sender = "System",
                    message = "Failed to send your suggestion: $errorMessage",
                    isCurrentUser = false
                )
            }
        }
    }
    
    suspend fun reconnectToRoom(): Boolean {
        return try {
            Log.d("RestaurantChatScreen", "Attempting to reconnect to room $roomCode")
            
            val userManager = com.example.finalproject.auth.UserManager.getInstance(com.example.finalproject.MainApplication.instance)
            val currentUser = userManager.getUser() ?: return false
            
            RoomWebSocketClient.leaveRoom(roomCode, currentUser)
            
            delay(100)
            
            connectToRoom()
            
            delay(300)
            
            Log.d("RestaurantChatScreen", "Reconnection attempt completed")
            true
        } catch (e: Exception) {
            Log.e("RestaurantChatScreen", "Error reconnecting to room: ${e.message}", e)
            false
        }
    }
    
    fun isExistingSuggestion(messages: List<ChatMessage>, suggestion: String): Boolean {
        return messages.any { message ->
            message.message == "I suggest: $suggestion"
        }
    }
    
    fun requestRestaurantSelection(strategy: String) {
        Log.d("RestaurantChatScreen", "Requesting restaurant selection with strategy: $strategy")
        
        if (messages.none { it.message.startsWith("I suggest:") }) {
            messages = messages + ChatMessage(
                sender = "System",
                message = "Error: Add at least one restaurant suggestion before selecting.",
                isCurrentUser = false
            )
            return
        }
        
        messages = messages + ChatMessage(
            sender = "System",
            message = "Selecting a restaurant...",
            isCurrentUser = false
        )
        
        RoomWebSocketClient.requestRestaurantSelection(roomCode, strategy)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Restaurant Suggestions")
                        Text(
                            text = "Room: $roomCode",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Text(
                        text = "$totalSuggestions/$requiredSuggestions",
                        modifier = Modifier.padding(end = 16.dp),
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (totalSuggestions > 0) {
                        IconButton(onClick = { showSelectionOptionsDialog = true }) {
                            Icon(
                                Icons.Default.RestaurantMenu,
                                contentDescription = "Select Restaurant"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    val isAlreadyAdded = if (message.sender == "AI Suggestion") {
                        messages.any { checkMessage -> 
                            checkMessage.message.startsWith("I suggest:") &&
                            checkMessage.message.substringAfter("I suggest:").trim() == message.message
                        }
                    } else false
                    
                    ChatMessageBubble(
                        message = message,
                        isAlreadySelected = isAlreadyAdded,
                        onSuggestionClick = { suggestion ->
                            val userSuggestionCount = suggestionCounts[currentUsername] ?: 0
                            
                            if (isAlreadyAdded) {
                                Log.d("RestaurantChatScreen", "Skipping already added suggestion: $suggestion")
                                return@ChatMessageBubble
                            }
                            
                            val alreadyExistsInMessages = isExistingSuggestion(messages, suggestion)
                            
                            if (alreadyExistsInMessages) {
                                Log.d("RestaurantChatScreen", "Skipping already existing suggestion: $suggestion")
                                return@ChatMessageBubble
                            }
                            
                            if (userSuggestionCount < suggestionsPerUser) {
                                Log.d("RestaurantChatScreen", "Processing suggestion click: $suggestion (count=$userSuggestionCount/$suggestionsPerUser)")
                                
                                sendSuggestionViaWebSocket(suggestion)
                                
                                messages = messages + ChatMessage(
                                    id = "temp-" + UUID.randomUUID().toString(),
                                    sender = currentUsername,
                                    message = "I suggest: $suggestion",
                                    isCurrentUser = true
                                )
                                
                                Log.d("RestaurantChatScreen", "Added temporary local message for suggestion: $suggestion")
                                
                                messages = messages.map { existingMessage ->
                                    if (existingMessage.id == message.id) {
                                        existingMessage.copy()
                                    } else {
                                        existingMessage
                                    }
                                }
                            } else {
                                messages = messages + ChatMessage(
                                    sender = "System",
                                    message = "You've already made your $suggestionsPerUser suggestions. Wait for others to finish.",
                                    isCurrentUser = false
                                )
                            }
                        }
                    )
                }
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Suggestions Remaining:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    Log.d("RestaurantChatScreen", "Displaying participants: $observedParticipants")
                    Log.d("RestaurantChatScreen", "Suggestion counts: $suggestionCounts")
                    
                    val displayedParticipants = observedParticipants.toList().distinct()
                    
                    displayedParticipants.forEach { participant ->
                        if (participant == "System" || participant == "AI Assistant" || participant == "AI Suggestion" || participant == "Unknown" || participant == "Unknown User") {
                            return@forEach
                        }
                        
                        val displayName = if (participant.contains("@")) {
                            participant.substringBefore("@")
                        } else {
                            participant
                        }
                        
                        val count = suggestionCounts[participant] ?: 0
                        val remaining = suggestionsPerUser - count
                        
                        if (count == 0 && !participants.contains(participant) && !participants.contains(displayName)) {
                            return@forEach
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Row {
                                repeat(remaining) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(1.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                CircleShape
                                            )
                                    )
                                }
                                repeat(count) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(1.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = aiPromptText,
                    onValueChange = { aiPromptText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text("Ask AI for restaurant suggestions...") },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            getAiSuggestions(aiPromptText)
                            focusManager.clearFocus()
                        }
                    ),
                    leadingIcon = {
                        Icon(
                            Icons.Default.CloudQueue,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                
                IconButton(
                    onClick = {
                        getAiSuggestions(aiPromptText)
                        focusManager.clearFocus()
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .size(48.dp),
                    enabled = !isAiLoading,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                    )
                ) {
                    if (isAiLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Get AI Suggestions")
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text("Type a restaurant suggestion...") },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                val userSuggestionCount = suggestionCounts[currentUsername] ?: 0
                                if (userSuggestionCount < suggestionsPerUser) {
                                    val formattedMessage = if (!inputText.startsWith("I suggest:") && 
                                                             !inputText.startsWith("i suggest:")) {
                                        "I suggest: $inputText"
                                    } else {
                                        inputText
                                    }
                                    
                                    val suggestion = if (formattedMessage.startsWith("I suggest:") || 
                                                        formattedMessage.startsWith("i suggest:")) {
                                        formattedMessage.substringAfter("suggest:").trim()
                                    } else {
                                        formattedMessage.trim()
                                    }
                                    
                                    val alreadyExistsInMessages = isExistingSuggestion(messages, suggestion)
                                    
                                    if (alreadyExistsInMessages) {
                                        messages = messages + ChatMessage(
                                            sender = "System",
                                            message = "This restaurant has already been suggested. Please suggest a different restaurant.",
                                            isCurrentUser = false
                                        )
                                    } else {
                                        sendSuggestionViaWebSocket(suggestion)
                                        
                                        messages = messages + ChatMessage(
                                            id = "temp-" + UUID.randomUUID().toString(),
                                            sender = currentUsername,
                                            message = "I suggest: $suggestion",
                                            isCurrentUser = true
                                        )
                                        
                                        Log.d("RestaurantChatScreen", "Added temporary local message for keyboard suggestion: $suggestion")
                                    }
                                    
                                    inputText = ""
                                    focusManager.clearFocus()
                                } else {
                                    messages = messages + ChatMessage(
                                        sender = "System",
                                        message = "You've already made your $suggestionsPerUser suggestions. Wait for others to finish.",
                                        isCurrentUser = false
                                    )
                                    inputText = ""
                                    focusManager.clearFocus()
                                }
                            }
                        }
                    )
                )
                
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val userSuggestionCount = suggestionCounts[currentUsername] ?: 0
                            if (userSuggestionCount < suggestionsPerUser) {
                                val formattedMessage = if (!inputText.startsWith("I suggest:") && 
                                                         !inputText.startsWith("i suggest:")) {
                                    "I suggest: $inputText"
                                } else {
                                    inputText
                                }
                                
                                val suggestion = if (formattedMessage.startsWith("I suggest:") || 
                                                    formattedMessage.startsWith("i suggest:")) {
                                    formattedMessage.substringAfter("suggest:").trim()
                                } else {
                                    formattedMessage.trim()
                                }
                                
                                val alreadyExistsInMessages = isExistingSuggestion(messages, suggestion)
                                
                                if (alreadyExistsInMessages) {
                                    messages = messages + ChatMessage(
                                        sender = "System",
                                        message = "This restaurant has already been suggested. Please suggest a different restaurant.",
                                        isCurrentUser = false
                                    )
                                } else {
                                    sendSuggestionViaWebSocket(suggestion)
                                    
                                    messages = messages + ChatMessage(
                                        id = "temp-" + UUID.randomUUID().toString(),
                                        sender = currentUsername,
                                        message = "I suggest: $suggestion",
                                        isCurrentUser = true
                                    )
                                    
                                    Log.d("RestaurantChatScreen", "Added temporary local message for send button suggestion: $suggestion")
                                }
                                
                                inputText = ""
                                focusManager.clearFocus()
                            } else {
                                messages = messages + ChatMessage(
                                    sender = "System",
                                    message = "You've already made your $suggestionsPerUser suggestions. Wait for others to finish.",
                                    isCurrentUser = false
                                )
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        }
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
        
        if (showSelectionOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showSelectionOptionsDialog = false },
                title = { Text("Select Restaurant") },
                text = { 
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Choose a method to select the final restaurant:")
                        
                        Button(
                            onClick = { 
                                requestRestaurantSelection("RANDOM")
                                showSelectionOptionsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Random Selection")
                        }
                        
                        Button(
                            onClick = { 
                                requestRestaurantSelection("HIGHEST_VOTES")
                                showSelectionOptionsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Highest Votes")
                        }
                        
                        Button(
                            onClick = { 
                                requestRestaurantSelection("AI_RECOMMEND")
                                showSelectionOptionsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("AI Recommendation")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSelectionOptionsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        if (showSelectionDialog) {
            AlertDialog(
                onDismissRequest = { showSelectionDialog = false },
                title = { Text("Restaurant Selected! 🎉") },
                text = { 
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = selectedRestaurant,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = selectionExplanation,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            showSelectionDialog = false
                            
                            isTransitioningToResults = true
                            
                            onFinish(listOf(selectedRestaurant))
                        }
                    ) {
                        Text("Confirm Selection")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSelectionDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isAlreadySelected: Boolean = false,
    onSuggestionClick: ((String) -> Unit)? = null
) {
    val isSystem = message.sender == "System"
    val isAiSuggestion = message.sender == "AI Suggestion"
    
    val displayName = when {
        isSystem -> "System"
        isAiSuggestion -> "AI Suggestion"
        message.sender.contains("@") -> message.sender.substringBefore("@")
        else -> message.sender
    }
    
    val alignment = when {
        isSystem || isAiSuggestion -> Alignment.CenterHorizontally
        message.isCurrentUser -> Alignment.End
        else -> Alignment.Start
    }
    
    val bubbleColor = when {
        isSystem -> MaterialTheme.colorScheme.surfaceVariant
        isAiSuggestion -> if (isAlreadySelected) 
            MaterialTheme.colorScheme.tertiaryContainer
        else 
            MaterialTheme.colorScheme.primaryContainer
        message.isCurrentUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    
    val textColor = when {
        isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
        isAiSuggestion -> if (isAlreadySelected) 
            MaterialTheme.colorScheme.onTertiaryContainer
        else 
            MaterialTheme.colorScheme.onPrimaryContainer
        message.isCurrentUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    
    val bubbleShape = when {
        isSystem || isAiSuggestion -> RoundedCornerShape(16.dp)
        message.isCurrentUser -> RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
        else -> RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }
    
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeString = sdf.format(Date(message.timestamp))
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalAlignment = alignment
    ) {
        if (!isSystem && !isAiSuggestion && !message.isCurrentUser) {
            Text(
                text = displayName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
        }
        
        val boxModifier = if (isAiSuggestion && onSuggestionClick != null && !isAlreadySelected) {
            Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .clickable { onSuggestionClick(message.message) }
                .padding(12.dp)
        } else {
            Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(12.dp)
        }
        
        Box(
            modifier = boxModifier
        ) {
            Column {
                if (isAiSuggestion && isAlreadySelected) {
                    Text(
                        text = "Added to your suggestions",
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.8f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.message,
                        color = textColor,
                        fontWeight = if (isAiSuggestion && isAlreadySelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (isAiSuggestion) {
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isAlreadySelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Added to suggestions",
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (onSuggestionClick != null) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add to suggestions",
                                tint = textColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                
                if (!isAiSuggestion) {
                    Text(
                        text = timeString,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RestaurantSuggestionsList(
    suggestions: List<RestaurantSuggestion>,
    onSelectRestaurant: (RestaurantSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(suggestions) { suggestion ->
            RestaurantItem(
                restaurant = suggestion,
                onClick = { onSelectRestaurant(suggestion) }
            )
        }
    }
}

@Composable
fun RestaurantItem(
    restaurant: RestaurantSuggestion,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        androidx.compose.material3.Card(
            modifier = Modifier
                .width(200.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = restaurant.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = restaurant.location,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    restaurant.rating?.let { rating ->
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = rating.toString(),
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    restaurant.priceLevel?.let { priceLevel ->
                        Text(
                            text = "$".repeat(priceLevel),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RestaurantChatScreenPreview() {
    FinalProjectTheme {
        RestaurantChatScreen(
            roomCode = "ABC123",
            currentUsername = "User1",
            participants = listOf("User1", "User2"),
            suggestionsPerUser = 4
        )
    }
} 