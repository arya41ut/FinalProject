package com.example.finalproject.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalproject.MainApplication
import com.example.finalproject.auth.UserManager
import com.example.finalproject.websocket.RoomWebSocketClient
import com.example.finalproject.websocket.RoomWebSocketListener
import com.example.finalproject.service.RestaurantSelectionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.finalproject.api.User

class SpinRoomViewModel : ViewModel(), RoomWebSocketListener {
    private val TAG = "SpinRoomViewModel"
    
    private val _participantCount = MutableStateFlow(1)
    val participantCount: StateFlow<Int> = _participantCount.asStateFlow()
    
    private val _isRoomReady = MutableStateFlow(false)
    val isRoomReady: StateFlow<Boolean> = _isRoomReady.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _participants = MutableStateFlow<List<String>>(emptyList())
    val participants: StateFlow<List<String>> = _participants.asStateFlow()
    
    private val _selectedRestaurant = MutableStateFlow<String?>(null)
    val selectedRestaurant: StateFlow<String?> = _selectedRestaurant.asStateFlow()
    
    private val _selectionExplanation = MutableStateFlow<String?>(null)
    val selectionExplanation: StateFlow<String?> = _selectionExplanation.asStateFlow()
    
    private val _isSelectionInProgress = MutableStateFlow(false)
    val isSelectionInProgress: StateFlow<Boolean> = _isSelectionInProgress.asStateFlow()
    
    private val userManager = UserManager.getInstance(MainApplication.instance)
    private var currentRoomCode: String? = null
    
    private val enableTestSimulation = false
    
    fun joinRoom(roomCode: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Attempting to join room with code: $roomCode")
                
                val actualRoomCode = extractRoomCode(roomCode)
                Log.d(TAG, "Extracted room code: $actualRoomCode")
                
                currentRoomCode = actualRoomCode
                
                val currentUser = userManager.getUser()
                if (currentUser != null) {
                    Log.d(TAG, "Joining room with WebSocket: $actualRoomCode (user: ${currentUser.username})")
                    RoomWebSocketClient.joinRoom(actualRoomCode, currentUser, this@SpinRoomViewModel)
                    
                    _participants.value = listOf(currentUser.username)
                    
                    if (enableTestSimulation) {
                        startTestSimulation()
                    }
                } else {
                    _errorMessage.value = "User not logged in"
                    Log.e(TAG, "Failed to join room: user not logged in")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to join room: ${e.message}"
                Log.e(TAG, "Failed to join room: ${e.message}", e)
            }
        }
    }
    
    private fun extractRoomCode(input: String): String {
        val codeRegex = "[A-Z0-9]{6}".toRegex()
        val matchResult = codeRegex.find(input)
        
        if (matchResult != null) {
            return matchResult.value
        }
        
        if (input.contains("with code:")) {
            return input.substringAfterLast("with code:").trim()
        }
        
        return input.trim()
    }
    
    private fun startTestSimulation() {
        viewModelScope.launch {
            delay(3000)
            
            _participantCount.value = 2
            _isRoomReady.value = true
            
            val currentParticipants = _participants.value.toMutableList()
            currentParticipants.add("Test User")
            _participants.value = currentParticipants
            
            Log.d(TAG, "TEST: Simulated second participant joining")
        }
    }
    
    fun leaveRoom() {
        viewModelScope.launch {
            try {
                currentRoomCode?.let { roomCode ->
                    val currentUser = userManager.getUser()
                    if (currentUser != null) {
                        Log.d(TAG, "Leaving room via WebSocket: $roomCode (user: ${currentUser.username})")
                        RoomWebSocketClient.leaveRoom(roomCode, currentUser)
                    } else {
                        Log.d(TAG, "Leaving room (no user): $roomCode")
                        RoomWebSocketClient.leaveRoom(roomCode)
                    }
                    
                    currentRoomCode = null
                    
                    _participantCount.value = 1
                    _isRoomReady.value = false
                    _participants.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error leaving room: ${e.message}", e)
            }
        }
    }
    
    fun requestRestaurantSelection(strategy: RestaurantSelectionService.SelectionStrategy) {
        viewModelScope.launch {
            try {
                currentRoomCode?.let { roomCode ->
                    Log.d(TAG, "Requesting restaurant selection for room $roomCode with strategy: $strategy")
                    
                    if (!RoomWebSocketClient.isConnectedToRoom(roomCode)) {
                        Log.e(TAG, "Cannot select restaurant - not connected to room $roomCode")
                        _errorMessage.value = "Not connected to room. Please try again."
                        return@launch
                    }
                    
                    _isSelectionInProgress.value = true
                    
                    RoomWebSocketClient.requestRestaurantSelection(roomCode, strategy.name)
                    
                    delay(10000)
                    if (_isSelectionInProgress.value && _selectedRestaurant.value == null) {
                        Log.e(TAG, "Restaurant selection timed out")
                        _errorMessage.value = "Restaurant selection timed out. Please try again."
                        _isSelectionInProgress.value = false
                    }
                } ?: run {
                    Log.e(TAG, "Cannot select restaurant - no room code")
                    _errorMessage.value = "Not connected to a room."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting restaurant selection: ${e.message}", e)
                _errorMessage.value = "Error selecting restaurant: ${e.message}"
                _isSelectionInProgress.value = false
            }
        }
    }
    
    fun clearSelectedRestaurant() {
        _selectedRestaurant.value = null
        _selectionExplanation.value = null
    }
    
    override fun onRoomJoined(roomCode: String) {
        Log.d(TAG, "WebSocket: Successfully joined room: $roomCode")
    }
    
    override fun onParticipantUpdate(roomCode: String, count: Int, isReady: Boolean, participants: List<String>?) {
        Log.d(TAG, "WebSocket: Room update - room: $roomCode, count: $count, isReady: $isReady, participants: $participants")
        
        _participantCount.value = count
        _isRoomReady.value = isReady
        
        participants?.let {
            Log.d(TAG, "WebSocket: Participants list received with ${it.size} participants: $it")
            _participants.value = it
        }
    }
    
    override fun onError(roomCode: String, message: String) {
        Log.e(TAG, "WebSocket: Error in room $roomCode: $message")
        _errorMessage.value = message
    }
    
    override fun onDisconnected(roomCode: String) {
        Log.d(TAG, "WebSocket: Disconnected from room: $roomCode")
    }
    
    override fun onRestaurantSuggestion(roomCode: String, userId: String, suggestion: String) {
        Log.d(TAG, "WebSocket: Received restaurant suggestion from $userId in room $roomCode: $suggestion")
    }
    
    override fun onRestaurantSelected(roomCode: String, restaurant: String, explanation: String) {
        Log.d(TAG, "WebSocket: Received restaurant selection for room $roomCode: $restaurant, explanation: $explanation")
        
        _selectedRestaurant.value = restaurant
        _selectionExplanation.value = explanation
        _isSelectionInProgress.value = false
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, leaving room")
        leaveRoom()
        
        currentRoomCode?.let { 
            RoomWebSocketClient.removeListener(it, this)
        }
    }
} 