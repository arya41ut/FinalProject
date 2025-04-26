package com.example.finalproject.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finalproject.MainApplication
import com.example.finalproject.auth.UserManager
import com.example.finalproject.ui.theme.FinalProjectTheme
import com.example.finalproject.websocket.RoomWebSocketClient
import com.example.finalproject.websocket.RoomWebSocketListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object ScreenStates {
    const val SPINNING = 0
    const val VOTING = 1
    const val RESULT = 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameResultScreen(
    roomCode: String,
    participants: List<String>,
    restaurantSuggestions: List<String>,
    onBackToHome: () -> Unit = {},
    onScheduleToCalendar: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val userManager = remember { UserManager.getInstance(MainApplication.instance) }
    val currentUser = remember { userManager.getUser() }
    val currentUsername = currentUser?.username ?: "Unknown User"
    
    var screenState by remember { mutableStateOf(ScreenStates.SPINNING) }
    
    var currentSelection by remember { mutableStateOf<String?>(null) }
    
    var winningRestaurant by remember { mutableStateOf<String?>(null) }
    var selectionExplanation by remember { mutableStateOf("") }
    var selectionError by remember { mutableStateOf<String?>(null) }
    
    val approvalVotes = remember { mutableStateMapOf<String, Boolean>() }
    
    val debugMode = remember { true }
    
    var showConfetti by remember { mutableStateOf(false) }
    
    val spinAngle = remember { Animatable(0f) }
    
    val resultScale = remember { Animatable(0.8f) }
    
    fun resetForNewSpin() {
        approvalVotes.clear()
        screenState = ScreenStates.SPINNING
        currentSelection = null
    }
    
    suspend fun selectRandomRestaurant() {
        spinAngle.animateTo(
            targetValue = 720f + (Math.random() * 360).toFloat(),
            animationSpec = tween(
                durationMillis = 3000,
                easing = FastOutSlowInEasing
            )
        )
        
        if (restaurantSuggestions.isNotEmpty()) {
            Log.d("GameResultScreen", "Requesting restaurant selection from backend")
            
            try {
                RoomWebSocketClient.requestRestaurantSelection(roomCode, "RANDOM")
            } catch (e: Exception) {
                Log.e("GameResultScreen", "Error requesting restaurant selection", e)
                selectionError = "Error requesting restaurant: ${e.message}"
            }
        } else {
            selectionError = "No restaurant suggestions available"
        }
    }
    
    fun checkVotingResult() {
        Log.d("GameResultScreen", "Checking voting result: approvalVotes=${approvalVotes.size}, participants=${participants.size}")
        Log.d("GameResultScreen", "Current votes: ${approvalVotes.entries.joinToString { "${it.key}=${it.value}" }}")
        
        if (approvalVotes.size >= participants.size) {
            val allApproved = approvalVotes.values.all { it }
            Log.d("GameResultScreen", "All participants voted. All approved: $allApproved")
            
            if (allApproved) {
                Log.d("GameResultScreen", "Everyone approved - setting winner: $currentSelection")
                winningRestaurant = currentSelection
                screenState = ScreenStates.RESULT
                
                scope.launch {
                    showConfetti = true
                    
                    resultScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                
                scope.launch {
                    try {
                        RoomWebSocketClient.requestRestaurantSelection(roomCode, "CONSENSUS")
                        Log.d("GameResultScreen", "Final selection confirmed with backend")
                    } catch (e: Exception) {
                        Log.e("GameResultScreen", "Error confirming final selection", e)
                    }
                }
            } else {
                Log.d("GameResultScreen", "Not everyone approved - preparing to spin again")
                scope.launch {
                    delay(1000)
                    resetForNewSpin()
                    selectRandomRestaurant()
                }
            }
        } else {
            Log.d("GameResultScreen", "Still waiting for more votes")
        }
    }
    
    fun voteOnSelection(approved: Boolean) {
        Log.d("GameResultScreen", "User ${currentUsername} is voting: approved=$approved")
        
        try {
            approvalVotes[currentUsername] = approved
            
            try {
                val voteMessage = "VOTE:${currentSelection}:${currentUsername}:${approved}"
                Log.d("GameResultScreen", "Sending vote message: $voteMessage")
                RoomWebSocketClient.sendRestaurantSuggestion(roomCode, voteMessage)
                
                checkVotingResult()
            } catch (e: Exception) {
                Log.e("GameResultScreen", "Error sending vote", e)
                selectionError = "Error sending vote: ${e.message}"
            }
        } catch (e: Exception) {
            Log.e("GameResultScreen", "Error in voteOnSelection", e)
            selectionError = "Error processing vote: ${e.message}"
        }
    }
    
    val webSocketListener = remember {
        object : RoomWebSocketListener {
            override fun onRoomJoined(roomCode: String) {
                if (debugMode) Log.d("GameResultScreen", "Connected to room: $roomCode")
            }
            
            override fun onParticipantUpdate(roomCode: String, count: Int, isReady: Boolean, participants: List<String>?) {
                if (debugMode) Log.d("GameResultScreen", "Participant update: count=$count, participants=$participants")
            }
            
            override fun onError(roomCode: String, message: String) {
                Log.e("GameResultScreen", "Error from WebSocket: $message")
                selectionError = "Error: $message"
            }
            
            override fun onDisconnected(roomCode: String) {
                if (debugMode) Log.d("GameResultScreen", "Disconnected from room: $roomCode")
            }
            
            override fun onRestaurantSuggestion(roomCode: String, userId: String, suggestion: String) {
                Log.d("GameResultScreen", "Message received: userId=$userId, message=$suggestion")
                
                if (suggestion.startsWith("VOTE:")) {
                    try {
                        val parts = suggestion.split(":")
                        Log.d("GameResultScreen", "Vote message parts: ${parts.joinToString()}")
                        
                        if (parts.size >= 4) {
                            val restaurant = parts[1]
                            val voter = parts[2]
                            val approved = parts[3].toBoolean()
                            
                            Log.d("GameResultScreen", "Parsed vote: restaurant='$restaurant', voter='$voter', approved=$approved")
                            Log.d("GameResultScreen", "Current selection: $currentSelection")
                            
                            if (restaurant == currentSelection) {
                                Log.d("GameResultScreen", "Vote is for current selection - registering vote")
                                Log.d("GameResultScreen", "Vote received: $voter voted ${if (approved) "YES" else "NO"} for $restaurant")
                                
                                approvalVotes[voter] = approved
                                Log.d("GameResultScreen", "Updated approvalVotes: ${approvalVotes.entries.joinToString { "${it.key}=${it.value}" }}")
                                
                                checkVotingResult()
                            } else {
                                Log.d("GameResultScreen", "Vote is NOT for current selection - ignoring")
                            }
                        } else {
                            Log.e("GameResultScreen", "Invalid vote message format: not enough parts")
                        }
                    } catch (e: Exception) {
                        Log.e("GameResultScreen", "Error parsing vote message", e)
                    }
                }
            }
            
            override fun onRestaurantSelected(roomCode: String, restaurant: String, explanation: String) {
                Log.d("GameResultScreen", "Restaurant selected from backend: $restaurant, explanation: $explanation")
                
                if (screenState == ScreenStates.SPINNING) {
                    currentSelection = restaurant
                    selectionExplanation = explanation
                    
                    screenState = ScreenStates.VOTING
                } else if (screenState == ScreenStates.RESULT && restaurant == winningRestaurant) {
                    if (selectionExplanation.isBlank()) {
                        selectionExplanation = explanation
                    }
                }
            }
        }
    }
    
    LaunchedEffect(roomCode) {
        if (restaurantSuggestions.isEmpty()) {
            selectionError = "No restaurant suggestions available"
            return@LaunchedEffect
        }
        
        try {
            RoomWebSocketClient.addListener(roomCode, webSocketListener)
            
            if (!RoomWebSocketClient.isConnectedToRoom(roomCode)) {
                Log.d("GameResultScreen", "Connecting to room: $roomCode")
                delay(500)
            }
            
            selectRandomRestaurant()
            
        } catch (e: Exception) {
            Log.e("GameResultScreen", "Error in WebSocket connection", e)
            selectionError = "Error: ${e.message}"
        }
    }
    
    DisposableEffect(roomCode) {
        onDispose {
            RoomWebSocketClient.removeListener(roomCode, webSocketListener)
        }
    }
    
    LaunchedEffect(screenState) {
        if (screenState == ScreenStates.SPINNING && currentSelection == null) {
            delay(500)
            selectRandomRestaurant()
            Log.d("GameResultScreen", "Triggering re-spin")
        }
    }
    
    LaunchedEffect(screenState) {
        if (screenState == ScreenStates.RESULT && winningRestaurant != null) {
            resultScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            
            delay(500)
            showConfetti = true
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when (screenState) {
                            ScreenStates.SPINNING -> "Selecting a Restaurant..."
                            ScreenStates.VOTING -> "Vote on This Selection"
                            ScreenStates.RESULT -> "Restaurant Chosen!"
                            else -> "Restaurant Selection"
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Home")
                    }
                },
                actions = {
                    if (winningRestaurant != null) {
                        IconButton(
                            onClick = {
                                val shareIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, 
                                        "Our Restaurant Roulette group chose: $winningRestaurant! 🍽️")
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share result"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (showConfetti) {
                ConfettiEffect()
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Room: $roomCode",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Participants: ${participants.joinToString(", ")}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You: $currentUsername",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (selectionError != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = selectionError!!,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                AnimatedVisibility(
                    visible = screenState == ScreenStates.SPINNING,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Selecting a restaurant...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .rotate(spinAngle.value),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restaurant,
                                contentDescription = "Spinning Wheel",
                                modifier = Modifier.size(100.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                AnimatedVisibility(
                    visible = screenState == ScreenStates.VOTING && currentSelection != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Selected Restaurant:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = currentSelection ?: "",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Text(
                                text = "Do you approve this selection?",
                                fontSize = 16.sp,
                                fontStyle = FontStyle.Italic
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (approvalVotes.containsKey(currentUsername)) {
                                    val approved = approvalVotes[currentUsername] == true
                                    
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (approved) 
                                                Color(0xFF4CAF50).copy(alpha = 0.2f) 
                                            else 
                                                Color(0xFFF44336).copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(16.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (approved) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = if (approved) "Approved" else "Rejected",
                                                tint = if (approved) Color(0xFF4CAF50) else Color(0xFFF44336)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (approved) "You approved this restaurant" else "You rejected this restaurant",
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { voteOnSelection(true) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50)
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Approve",
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Approve")
                                        }
                                    }
                                    
                                    Button(
                                        onClick = { voteOnSelection(false) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFF44336)
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Reject",
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Reject")
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Votes (${approvalVotes.size}/${participants.size}):",
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    participants.forEach { participant ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = participant,
                                                fontWeight = if (participant == currentUsername) 
                                                    FontWeight.Bold 
                                                else 
                                                    FontWeight.Normal
                                            )
                                            
                                            when {
                                                approvalVotes.containsKey(participant) -> {
                                                    val approved = approvalVotes[participant] == true
                                                    Icon(
                                                        imageVector = if (approved) Icons.Default.Check else Icons.Default.Close,
                                                        contentDescription = if (approved) "Approved" else "Rejected",
                                                        tint = if (approved) Color(0xFF4CAF50) else Color(0xFFF44336)
                                                    )
                                                }
                                                else -> {
                                                    Text(
                                                        text = "Waiting...",
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                        fontStyle = FontStyle.Italic
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                AnimatedVisibility(
                    visible = screenState == ScreenStates.RESULT && winningRestaurant != null,
                    enter = fadeIn() + scaleIn()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Trophy",
                            modifier = Modifier
                                .size(64.dp)
                                .scale(resultScale.value),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "The winner is",
                            fontSize = 16.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(resultScale.value),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = winningRestaurant ?: "",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp, horizontal = 16.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        
                        if (selectionExplanation.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = selectionExplanation,
                                textAlign = TextAlign.Center,
                                fontStyle = FontStyle.Italic,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        Button(
                            onClick = {
                                winningRestaurant?.let { restaurant ->
                                    onScheduleToCalendar(restaurant)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Schedule to Calendar",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Schedule to Calendar")
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = onBackToHome,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Home")
                }
            }
        }
    }
}

@Composable
fun ConfettiEffect() {
    val particles = remember { (1..50).map { ConfettiParticle() } }
    
    Box(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            ConfettiParticle(particle)
        }
    }
}

data class ConfettiParticle(
    val color: Color = listOf(
        Color(0xFFED436A),
        Color(0xFF3CC4BD),
        Color(0xFFFFC857),
        Color(0xFF5CBBFC),
        Color(0xFF9F5CF9)
    ).random(),
    val size: Float = (5..15).random().toFloat(),
    val initialX: Float = (0..1000).random().toFloat(),
    val initialY: Float = (-200..-50).random().toFloat(),
    val speed: Float = (2..7).random().toFloat()
)

@Composable
fun ConfettiParticle(particle: ConfettiParticle) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val yPos = infiniteTransition.animateFloat(
        initialValue = particle.initialY,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (3000 / particle.speed).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        )
    )
    
    val xPos = infiniteTransition.animateFloat(
        initialValue = particle.initialX,
        targetValue = particle.initialX + (if (particle.initialX % 2 == 0f) 100f else -100f),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val rotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        )
    )
    
    Box(
        modifier = Modifier
            .size(particle.size.dp)
            .offset(x = xPos.value.dp, y = yPos.value.dp)
            .rotate(rotation.value)
            .background(particle.color, RoundedCornerShape(2.dp))
    )
}

@Preview(showBackground = true)
@Composable
fun GameResultScreenPreview() {
    FinalProjectTheme {
        GameResultScreen(
            roomCode = "ABC123",
            participants = listOf("User1", "User2"),
            restaurantSuggestions = listOf(
                "Burger King", "McDonald's", "Wendy's", 
                "Pizza Hut", "Taco Bell", "Chipotle", "Subway",
                "KFC"
            )
        )
    }
}