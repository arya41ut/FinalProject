package com.example.finalproject.ui.screens

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finalproject.ui.theme.FinalProjectTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun SpinRoomScreen(
    inviteCode: String,
    participantCount: Int = 1,
    isRoomReady: Boolean = false,
    onStart: () -> Unit = {},
    onCancel: () -> Unit = {},
    viewModel: SpinRoomViewModel = viewModel()
) {
    var remainingTime by remember { mutableStateOf(60) }
    var timerRunning by remember { mutableStateOf(true) }
    var showError by remember { mutableStateOf(false) }
    var isTransitioningToChat by remember { mutableStateOf(false) }
    
    LaunchedEffect(inviteCode) {
        viewModel.joinRoom(inviteCode)
    }
    
    val currentParticipantCount by viewModel.participantCount.collectAsState()
    val roomReady by viewModel.isRoomReady.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            showError = true
            delay(3000)
            showError = false
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            if (!isTransitioningToChat) {
                Log.d("SpinRoomScreen", "Leaving room on dispose (not transitioning to chat)")
                viewModel.leaveRoom()
            } else {
                Log.d("SpinRoomScreen", "Skipping leaveRoom since we're transitioning to chat")
            }
        }
    }
    
    LaunchedEffect(key1 = timerRunning) {
        while (timerRunning && remainingTime > 0) {
            delay(1000L)
            remainingTime--
        }
        
        if (remainingTime <= 0) {
            viewModel.leaveRoom()
            onCancel()
        }
    }
    
    LaunchedEffect(key1 = roomReady) {
        if (roomReady) {
            timerRunning = false
        }
    }
    
    LaunchedEffect(key1 = currentParticipantCount) {
        if (currentParticipantCount >= 2) {
            timerRunning = false
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Waiting for others to join",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0E0E0), shape = RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Invite Code:",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = inviteCode,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
            
            if (currentParticipantCount >= 2) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Successfully joined the room!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Participants: $currentParticipantCount/2",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            
            val participants by viewModel.participants.collectAsState()
            if (participants.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0F0F0), shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Participants:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    participants.forEach { participant ->
                        Text(
                            text = "• $participant",
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (currentParticipantCount < 2) {
                Text(
                    text = "Room closes in: ${remainingTime}s",
                    fontSize = 16.sp,
                    color = if (remainingTime < 10) Color.Red else Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        viewModel.leaveRoom()
                        onCancel()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = { 
                        isTransitioningToChat = true
                        onStart() 
                    },
                    enabled = currentParticipantCount >= 2,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text("Start")
                }
            }
        }
        
        if (showError && errorMessage != null) {
            Snackbar(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Text(text = errorMessage!!)
            }
        }
    }
}

@Composable
fun UserIcon() {
    Box(
        modifier = Modifier
            .size(60.dp)
            .background(Color.Black, shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
    }
}

@Composable
fun RouletteWheel(
    modifier: Modifier = Modifier,
    sections: Int = 8
) {
    val colors = listOf(Color.White, Color(0xFFE0F7D9))
    val textMeasurer = rememberTextMeasurer()
    
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width.coerceAtMost(size.height) / 2
        
        drawCircle(
            color = Color.Black,
            radius = radius,
            center = center,
            style = Stroke(width = 4f)
        )
        
        val sectionAngle = 360f / sections
        for (i in 0 until sections) {
            val startAngle = i * sectionAngle
            
            rotate(startAngle, center) {
                drawArc(
                    color = colors[i % colors.size],
                    startAngle = 0f,
                    sweepAngle = sectionAngle,
                    useCenter = true,
                    topLeft = center - Offset(radius, radius),
                    size = Size(radius * 2, radius * 2)
                )
                
                drawLine(
                    color = Color.Black,
                    start = center,
                    end = Offset(
                        center.x + radius * cos(0f * PI.toFloat() / 180f),
                        center.y + radius * sin(0f * PI.toFloat() / 180f)
                    ),
                    strokeWidth = 2f
                )
                
                val textLayout = textMeasurer.measure(
                    text = (i + 1).toString(),
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )
                
                val textX = center.x + (radius * 0.6f) * cos((sectionAngle / 2) * PI.toFloat() / 180f)
                val textY = center.y + (radius * 0.6f) * sin((sectionAngle / 2) * PI.toFloat() / 180f)
                
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        textX - textLayout.size.width / 2,
                        textY - textLayout.size.height / 2
                    )
                )
            }
        }
        
        drawCircle(
            color = Color.Black,
            radius = 10f,
            center = center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SpinRoomScreenPreview() {
    FinalProjectTheme {
        SpinRoomScreen("123456")
    }
} 