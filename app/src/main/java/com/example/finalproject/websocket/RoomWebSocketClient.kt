package com.example.finalproject.websocket

import android.util.Log
import com.example.finalproject.api.User
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap

enum class RoomMessageType {
    JOIN,
    LEAVE,
    UPDATE,
    ERROR,
    SUBSCRIBE,
    SELECTION,
    VOTE
}

data class RoomMessage(
    val type: RoomMessageType,
    val roomCode: String? = null,
    val participantCount: Int? = null,
    val isReady: Boolean? = null,
    val participants: List<String>? = null,
    val message: String? = null,
    val userId: String? = null,
    val restaurantSuggestions: List<String>? = null,
    val selectedRestaurant: String? = null,
    val selectionExplanation: String? = null,
    val votes: Map<String, Boolean>? = null,
    val voterUsername: String? = null,
    val approved: Boolean? = null
) {
    companion object {
        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            
        fun toJson(message: RoomMessage): String {
            val adapter = moshi.adapter(RoomMessage::class.java)
            return adapter.toJson(message)
        }
        
        fun fromJson(json: String): RoomMessage? {
            return try {
                val adapter = moshi.adapter(RoomMessage::class.java)
                adapter.fromJson(json)
            } catch (e: Exception) {
                Log.e("RoomMessage", "Error parsing JSON: $e")
                null
            }
        }
    }
}

interface RoomWebSocketListener {
    fun onRoomJoined(roomCode: String)
    fun onParticipantUpdate(roomCode: String, count: Int, isReady: Boolean, participants: List<String>? = null)
    fun onError(roomCode: String, message: String)
    fun onDisconnected(roomCode: String)
    fun onRestaurantSuggestion(roomCode: String, userId: String, suggestion: String)
    fun onRestaurantSelected(roomCode: String, restaurant: String, explanation: String)
}

object RoomWebSocketClient {
    private const val TAG = "RoomWebSocketClient"
    
    private const val WS_ENDPOINT = "ws://10.0.2.2:8080/ws"
    private const val SOCKJS_ENDPOINT = "ws://10.0.2.2:8080/ws/websocket"
    
    private var currentEndpoint = WS_ENDPOINT
    private var shouldUseSockJS = false
    
    private val client = OkHttpClient.Builder()
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
        
    private val roomConnections = ConcurrentHashMap<String, WebSocket>()
    private val roomListeners = ConcurrentHashMap<String, MutableList<RoomWebSocketListener>>()
    
    fun joinRoom(roomCode: String, user: User, listener: RoomWebSocketListener) {
        val cleanRoomCode = extractRoomCode(roomCode)
        Log.d(TAG, "Using room code: $cleanRoomCode for WebSocket connection")
        
        addListener(cleanRoomCode, listener)
        
        val endpoint = if (shouldUseSockJS) SOCKJS_ENDPOINT else WS_ENDPOINT
        currentEndpoint = endpoint
        
        Log.d(TAG, "Connecting to WebSocket endpoint at: $endpoint")
        val request = Request.Builder()
            .url(endpoint)
            .build()
            
        val webSocket = client.newWebSocket(request, createListener(cleanRoomCode, user))
        roomConnections[cleanRoomCode] = webSocket
        Log.d(TAG, "DEBUG: Added WebSocket to roomConnections for room '$cleanRoomCode'")
        Log.d(TAG, "DEBUG: roomConnections now contains ${roomConnections.size} entries with keys: ${roomConnections.keys.joinToString()}")
        
        val connectFrame = buildStompConnectFrame()
        Log.d(TAG, "Sending STOMP CONNECT frame: $connectFrame")
        webSocket.send(connectFrame)
    }
    
    private fun extractRoomCode(input: String): String {
        Log.d(TAG, "Extracting room code from: '$input'")
        
        if (input.contains("Successfully joined") || input.contains("joined the room")) {
            val codeRegex = "[A-Z0-9]{6}".toRegex()
            val matchResult = codeRegex.find(input)
            
            if (matchResult != null) {
                val extractedCode = matchResult.value
                Log.d(TAG, "Found room code using regex: '$extractedCode'")
                return extractedCode
            }
            
            if (input.contains("with code:")) {
                val code = input.substringAfterLast("with code:").trim()
                Log.d(TAG, "Extracted room code after 'with code:': '$code'")
                return code
            }
            
            Log.w(TAG, "Could not extract room code from success message: '$input', using fallback")
            
            return "B8RI31"
        } else {
            return input.trim()
        }
    }
    
    fun leaveRoom(roomCode: String, user: User? = null) {
        val cleanRoomCode = extractRoomCode(roomCode)
        val webSocket = roomConnections[cleanRoomCode] ?: return
        
        if (user != null) {
            val leaveFrame = buildStompLeaveFrame(cleanRoomCode, user.email)
            Log.d(TAG, "Sending STOMP leave frame: $leaveFrame")
            webSocket.send(leaveFrame)
        }
        
        val disconnectFrame = "DISCONNECT\n\n\u0000"
        webSocket.send(disconnectFrame)
        
        webSocket.close(1000, "Leaving room")
        Log.d(TAG, "DEBUG: Not removing WebSocket from roomConnections for room $cleanRoomCode on leave")
        roomListeners.remove(cleanRoomCode)
    }
    
    fun addListener(roomCode: String, listener: RoomWebSocketListener) {
        val cleanRoomCode = extractRoomCode(roomCode)
        val listeners = roomListeners.getOrPut(cleanRoomCode) { mutableListOf() }
        if (!listeners.contains(listener)) {
            Log.d(TAG, "Adding listener for room: $cleanRoomCode")
            listeners.add(listener)
        }
    }
    
    fun removeListener(roomCode: String, listener: RoomWebSocketListener) {
        val cleanRoomCode = extractRoomCode(roomCode)
        roomListeners[cleanRoomCode]?.remove(listener)
    }
    
    private fun createListener(roomCode: String, user: User): WebSocketListener {
        return object : WebSocketListener() {
            private var isConnected = false
            private var isSubscribed = false
            private val currentUser = user
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened for room: $roomCode")
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received (length: ${text.length}). Connected: $isConnected, Subscribed: $isSubscribed, Room: $roomCode")
                Log.d(TAG, "DEBUG RAW MESSAGE: First 100 chars: ${text.take(100)}")
                
                if (text.startsWith("CONNECTED")) {
                    Log.d(TAG, "STOMP CONNECTED frame received: $text")
                    isConnected = true
                    
                    val subscribeFrame = buildStompSubscribeFrame(roomCode)
                    webSocket.send(subscribeFrame)
                    Log.d(TAG, "Sent STOMP SUBSCRIBE frame: $subscribeFrame")
                    
                    val joinFrame = buildStompJoinFrame(roomCode, user.email)
                    webSocket.send(joinFrame)
                    Log.d(TAG, "Sent STOMP JOIN frame: $joinFrame")
                    
                } else if (text.startsWith("MESSAGE")) {
                    Log.d(TAG, "STOMP MESSAGE frame received.")
                    
                    val parts = text.split("\n\n", limit = 2)
                    if (parts.size < 2) {
                        Log.e(TAG, "Invalid STOMP MESSAGE frame format: $text")
                        return
                    }
                    
                    val headers = parts[0]
                    val body = parts[1].removeSuffix("\u0000")
                    
                    Log.d(TAG, "STOMP MESSAGE Headers: ${headers.take(100)}...")
                    Log.d(TAG, "STOMP MESSAGE Body: ${body.take(100)}...")
                    
                    val destinationHeader = headers.lines().find { it.startsWith("destination:") }
                    if (destinationHeader == null) {
                        Log.e(TAG, "Missing destination header in STOMP MESSAGE frame")
                        return
                    }
                    
                    val destination = destinationHeader.substring("destination:".length)
                    Log.d(TAG, "STOMP MESSAGE destination: $destination")
                    
                    if (destination == "/topic/room/$roomCode") {
                        processRoomMessage(body, roomCode)
                    } else {
                        Log.d(TAG, "Received message for unknown destination: $destination")
                    }
                } else if (text.startsWith("ERROR")) {
                    Log.e(TAG, "CRITICAL: STOMP ERROR frame received. Check for session errors: $text")
                    handleStompError(text, roomCode)
                } else {
                    Log.d(TAG, "Received unknown STOMP frame type: ${text.lines().firstOrNull() ?: "empty"}")
                }
            }
            
            private fun handleStompConnected(webSocket: WebSocket, roomCode: String) {
                Log.d(TAG, "STOMP CONNECTED frame received")
                isConnected = true
                
                val subscribeFrame = buildStompSubscribeFrame(roomCode)
                Log.d(TAG, "Sending STOMP SUBSCRIBE frame: $subscribeFrame")
                webSocket.send(subscribeFrame)
                
                val currentUser = this.currentUser
                if (currentUser != null) {
                    val joinFrame = buildStompJoinFrame(roomCode, currentUser.email)
                    Log.d(TAG, "Sending JOIN message for user: ${currentUser.email}")
                    Log.d(TAG, "JOIN frame: $joinFrame")
                    webSocket.send(joinFrame)
                }
                
                roomListeners[roomCode]?.forEach { it.onRoomJoined(roomCode) }
            }
            
            private fun handleStompMessage(webSocket: WebSocket, text: String, roomCode: String) {
                Log.d(TAG, "STOMP MESSAGE frame received")
                
                val headers = mutableMapOf<String, String>()
                var bodyStartIndex = -1
                
                val lines = text.split("\n")
                var i = 1
                while (i < lines.size) {
                    val line = lines[i]
                    if (line.isEmpty()) {
                        bodyStartIndex = i + 1
                        break
                    }
                    
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val key = line.substring(0, colonIndex)
                        val value = line.substring(colonIndex + 1)
                        headers[key] = value
                    }
                    i++
                }
                
                if (bodyStartIndex == -1 || bodyStartIndex >= lines.size) {
                    Log.e(TAG, "Invalid STOMP MESSAGE frame, no body found")
                    return
                }
                
                val bodyBuilder = StringBuilder()
                while (bodyStartIndex < lines.size) {
                    val line = lines[bodyStartIndex].replace("\u0000", "")
                    if (line.isEmpty() && bodyStartIndex == lines.size - 1) {
                        break
                    }
                    bodyBuilder.append(line)
                    if (bodyStartIndex < lines.size - 1) {
                        bodyBuilder.append("\n")
                    }
                    bodyStartIndex++
                }
                
                val body = bodyBuilder.toString().trim()
                Log.d(TAG, "STOMP message headers: $headers")
                Log.d(TAG, "STOMP message body: '$body'")
                
                val destination = headers["destination"] ?: ""
                
                if (destination.contains("/topic")) {
                    try {
                        val message = RoomMessage.fromJson(body)
                        
                        if (message != null) {
                            Log.d(TAG, "Successfully parsed JSON message from server")
                            handleJsonMessage(roomCode, message)
                        } else {
                            try {
                                if (body.trim().startsWith("{") && body.trim().endsWith("}")) {
                                    Log.d(TAG, "Message appears to be JSON but couldn't be parsed with our adapter")
                                    
                                    val participantCountMatch = "\"participantCount\"\\s*:\\s*(\\d+)".toRegex().find(body)
                                    val isReadyMatch = "\"isReady\"\\s*:\\s*(true|false)".toRegex().find(body)
                                    val participantsMatch = "\"participants\"\\s*:\\s*\\[(.*?)\\]".toRegex().find(body)
                                    
                                    if (participantCountMatch != null) {
                                        val count = participantCountMatch.groupValues[1].toIntOrNull() ?: 1
                                        val isReady = isReadyMatch?.groupValues?.get(1)?.equals("true") ?: false
                                        
                                        val participants = participantsMatch?.groupValues?.get(1)?.let {
                                            it.split(",").map { name -> 
                                                name.trim().replace("\"", "")
                                            }.filter { name -> name.isNotEmpty() }
                                        }
                                        
                                        Log.d(TAG, "Extracted from JSON: count=$count, isReady=$isReady, participants=$participants")
                                        roomListeners[roomCode]?.forEach { listener ->
                                            listener.onParticipantUpdate(roomCode, count, isReady, participants)
                                        }
                                    }
                                } else {
                                    val participantCount = body.toIntOrNull()
                                    if (participantCount != null) {
                                        Log.d(TAG, "Received participant count update: $participantCount")
                                        val isReady = participantCount >= 2
                                        roomListeners[roomCode]?.forEach { listener ->
                                            listener.onParticipantUpdate(roomCode, participantCount, isReady, null)
                                        }
                                    } else {
                                        Log.d(TAG, "Received non-numeric update: $body")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing message content: ${e.message}", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing topic message: ${e.message}", e)
                    }
                } else if (destination.contains("/queue") || destination.contains("/user")) {
                    Log.d(TAG, "Received direct message: $body")
                }
            }
            
            private fun handleJsonMessage(roomCode: String, message: RoomMessage) {
                Log.d(TAG, "Handling JSON message: $message")
                
                val messageRoomCode = message.roomCode ?: roomCode
                
                when (message.type) {
                    RoomMessageType.UPDATE -> {
                        if (message.restaurantSuggestions != null && message.restaurantSuggestions.isNotEmpty()) {
                            Log.d(TAG, "Processing restaurant suggestions: ${message.restaurantSuggestions}")
                            
                            val userId = message.userId ?: "Unknown"
                            
                            roomListeners[messageRoomCode]?.forEach { listener ->
                                message.restaurantSuggestions.forEach { suggestion ->
                                    listener.onRestaurantSuggestion(messageRoomCode, userId, suggestion)
                                }
                            }
                        }
                        
                        val count = message.participantCount ?: 1
                        val isReady = message.isReady ?: false
                        Log.d(TAG, "Processing UPDATE message: count=$count, isReady=$isReady, " +
                              "participants=${message.participants?.joinToString() ?: "null"}")
                        
                        roomListeners[messageRoomCode]?.forEach { listener ->
                            listener.onParticipantUpdate(messageRoomCode, count, isReady, message.participants)
                        }
                    }
                    RoomMessageType.JOIN -> {
                        Log.d(TAG, "Received JOIN message: userId=${message.userId}")
                    }
                    RoomMessageType.LEAVE -> {
                        Log.d(TAG, "Received LEAVE message: userId=${message.userId}")
                    }
                    RoomMessageType.ERROR -> {
                        message.message?.let { errorMsg ->
                            Log.e(TAG, "Received ERROR message: $errorMsg")
                            roomListeners[messageRoomCode]?.forEach { listener ->
                                listener.onError(messageRoomCode, errorMsg)
                            }
                        }
                    }
                    RoomMessageType.SELECTION -> {
                        val restaurant = message.selectedRestaurant
                        val explanation = message.selectionExplanation
                        
                        if (restaurant != null) {
                            Log.d(TAG, "Received SELECTION message: restaurant=$restaurant, explanation=$explanation")
                            roomListeners[messageRoomCode]?.forEach { listener ->
                                listener.onRestaurantSelected(messageRoomCode, restaurant, explanation ?: "No explanation provided")
                            }
                        } else {
                            Log.e(TAG, "Received SELECTION message with null restaurant")
                        }
                    }
                    RoomMessageType.VOTE -> {
                        val restaurant = message.selectedRestaurant
                        val votes = message.votes
                        
                        Log.d(TAG, "Received VOTE message: restaurant=$restaurant, votes=$votes")
                        
                        if (restaurant != null && votes != null) {
                            votes.forEach { (username, approved) ->
                                val voteMessage = "VOTE:$restaurant:$username:$approved"
                                Log.d(TAG, "Processing vote from update: $voteMessage")
                                
                                roomListeners[messageRoomCode]?.forEach { listener ->
                                    listener.onRestaurantSuggestion(messageRoomCode, username, voteMessage)
                                }
                            }
                        } else if (message.voterUsername != null && message.approved != null) {
                            val username = message.voterUsername
                            val approved = message.approved
                            val voteMessage = "VOTE:$restaurant:$username:$approved"
                            
                            Log.d(TAG, "Processing individual vote: $voteMessage")
                            roomListeners[messageRoomCode]?.forEach { listener ->
                                listener.onRestaurantSuggestion(messageRoomCode, username, voteMessage)
                            }
                        }
                    }
                    else -> {
                        Log.d(TAG, "Received unhandled message type: ${message.type}")
                    }
                }
            }
            
            private fun handleStompError(text: String, roomCode: String) {
                Log.e(TAG, "STOMP ERROR frame received: $text")
                
                val messageStartIndex = text.indexOf("message:")
                val errorMsg = if (messageStartIndex != -1) {
                    val msgLineEnd = text.indexOf('\n', messageStartIndex)
                    if (msgLineEnd != -1) {
                        text.substring(messageStartIndex + 8, msgLineEnd).trim()
                    } else {
                        text.substring(messageStartIndex + 8).trim()
                    }
                } else {
                    "Unknown STOMP error"
                }
                
                Log.e(TAG, "CRITICAL: Parsed STOMP error message: '$errorMsg', source text: '$text'")
                
                roomListeners[roomCode]?.forEach { listener ->
                    listener.onError(roomCode, errorMsg)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
                Log.d(TAG, "CLOSING DEBUG: Connection state before close: connected=$isConnected, subscribed=$isSubscribed, room=$roomCode")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                Log.d(TAG, "CLOSED DEBUG: Connection state at close: connected=$isConnected, subscribed=$isSubscribed, room=$roomCode")
                Log.d(TAG, "DEBUG: Not removing WebSocket from roomConnections for room $roomCode on close")
                roomListeners[roomCode]?.forEach { it.onDisconnected(roomCode) }
                roomListeners.remove(roomCode)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                Log.e(TAG, "FAILURE DEBUG: Connection state at failure: connected=$isConnected, subscribed=$isSubscribed, room=$roomCode, response=${response?.message}")
                
                if (!shouldUseSockJS && currentEndpoint == WS_ENDPOINT) {
                    Log.d(TAG, "Direct WebSocket connection failed, will try SockJS endpoint next time")
                    shouldUseSockJS = true
                    
                    Log.d(TAG, "DEBUG: Not removing WebSocket from roomConnections for room $roomCode on failure")
                    if (roomListeners.containsKey(roomCode)) {
                        Log.d(TAG, "Retrying connection with SockJS endpoint")
                        joinRoom(roomCode, user, roomListeners[roomCode]?.firstOrNull() ?: return)
                        return
                    }
                }
                
                Log.d(TAG, "DEBUG: Not removing WebSocket from roomConnections for room $roomCode on general failure")
                roomListeners[roomCode]?.forEach { 
                    it.onError(roomCode, "Connection error: ${t.message}") 
                    it.onDisconnected(roomCode)
                }
                roomListeners.remove(roomCode)
            }
        }
    }
    
    fun closeAll() {
        for ((roomCode, webSocket) in roomConnections) {
            webSocket.close(1000, "Closing connection")
            Log.d(TAG, "Closed connection for room: $roomCode")
        }
        roomConnections.clear()
        roomListeners.clear()
    }
    
    private fun buildStompConnectFrame(): String {
        return "CONNECT\n" +
               "accept-version:1.2,1.1,1.0\n" +
               "host:10.0.2.2:8080\n" +
               "heart-beat:10000,10000\n" +
               "\n" +
               "\u0000"
    }
    
    private fun buildStompSubscribeFrame(roomCode: String): String {
        return "SUBSCRIBE\n" +
               "id:sub-$roomCode\n" +
               "destination:/topic/room/$roomCode\n" +
               "\n" + 
               "\u0000"
    }
    
    private fun buildStompJoinFrame(roomCode: String, userEmail: String): String {
        return "SEND\n" +
               "destination:/app/room/$roomCode/join\n" +
               "content-type:text/plain;charset=utf-8\n" +
               "content-length:${userEmail.length}\n" +
               "\n" +
               "$userEmail" +
               "\u0000"
    }
    
    private fun buildStompLeaveFrame(roomCode: String, userEmail: String): String {
        return "SEND\n" +
               "destination:/app/room/$roomCode/leave\n" +
               "content-type:text/plain;charset=utf-8\n" +
               "content-length:${userEmail.length}\n" +
               "\n" +
               "$userEmail" +
               "\u0000"
    }
    
    fun sendRestaurantSuggestion(roomCode: String, suggestion: String) {
        Log.d(TAG, "Sending restaurant suggestion to room $roomCode: $suggestion")
        
        Log.d(TAG, "DEBUGGING ACTIVE CONNECTIONS:")
        Log.d(TAG, "Current WebSocket connections: ${roomConnections.keys.joinToString()}")
        Log.d(TAG, "Current room listeners: ${roomListeners.keys.joinToString()}")
        
        val cleanRoomCode = extractRoomCode(roomCode)
        Log.d(TAG, "Using clean room code: $cleanRoomCode")
        Log.d(TAG, "DEBUG: Current roomConnections map contains ${roomConnections.size} entries with keys: ${roomConnections.keys.joinToString()}")
        
        val userManager = com.example.finalproject.auth.UserManager.getInstance(com.example.finalproject.MainApplication.instance)
        val currentUser = userManager.getUser()
        
        val userId = if (currentUser?.username?.isNotBlank() == true) {
            currentUser.username
        } else {
            currentUser?.email ?: "unknown"
        }
        
        Log.d(TAG, "Using userId for suggestion: $userId (username: ${currentUser?.username}, email: ${currentUser?.email})")
        
        val suggestionFrame = buildStompSuggestionFrame(cleanRoomCode, suggestion, userId)
        Log.d(TAG, "Built STOMP suggestion frame: $suggestionFrame")
        
        val webSocket = roomConnections[cleanRoomCode]
        Log.d(TAG, "DEBUG: Retrieved WebSocket for room '$cleanRoomCode': ${webSocket != null}")
        if (webSocket == null) {
            Log.e(TAG, "Cannot send suggestion - not connected to room $cleanRoomCode")
            
            Log.d(TAG, "Attempting to reconnect to room $cleanRoomCode")
            
            if (currentUser != null) {
                val listener = roomListeners[cleanRoomCode]?.firstOrNull() ?: object : RoomWebSocketListener {
                    override fun onRoomJoined(roomCode: String) {}
                    override fun onParticipantUpdate(roomCode: String, count: Int, isReady: Boolean, participants: List<String>?) {}
                    override fun onError(roomCode: String, message: String) {}
                    override fun onDisconnected(roomCode: String) {}
                    override fun onRestaurantSuggestion(roomCode: String, userId: String, suggestion: String) {}
                    override fun onRestaurantSelected(roomCode: String, restaurant: String, explanation: String) {}
                }
                
                joinRoom(cleanRoomCode, currentUser, listener)
                
                val newWebSocket = roomConnections[cleanRoomCode]
                if (newWebSocket != null) {
                    Log.d(TAG, "Reconnection successful, sending suggestion")
                    newWebSocket.send(suggestionFrame)
                    return
                } else {
                    Log.e(TAG, "Reconnection failed, unable to send suggestion")
                    return
                }
            } else {
                Log.e(TAG, "Cannot reconnect - no current user")
                return
            }
        }
        
        Log.d(TAG, "Sending suggestion frame via WebSocket")
        webSocket.send(suggestionFrame)
    }
    
    private fun buildStompSuggestionFrame(roomCode: String, suggestion: String, userId: String): String {
        val suggestionBytes = suggestion.toByteArray(Charsets.UTF_8)
        val byteLength = suggestionBytes.size
        
        val deviceId = android.os.Build.MODEL
        
        Log.d(TAG, "STOMP Frame Debug: suggestion='$suggestion', char-length=${suggestion.length}, byte-length=$byteLength, userId=$userId")
        
        return "SEND\n" +
               "destination:/app/room/$roomCode/suggest\n" +
               "content-type:text/plain;charset=utf-8\n" +
               "content-length:$byteLength\n" +
               "client-id:$deviceId\n" +
               "user-id:$userId\n" +
               "\n" +
               suggestion +
               "\u0000"
    }
    
    fun requestAiSuggestions(roomCode: String, prompt: String) {
        Log.d(TAG, "Requesting AI restaurant suggestions for room $roomCode with prompt: $prompt")
        
        val cleanRoomCode = extractRoomCode(roomCode)
        val webSocket = roomConnections[cleanRoomCode] ?: run {
            Log.e(TAG, "Cannot request AI suggestions - not connected to room $cleanRoomCode")
            return
        }
        
        val requestJson = """{"prompt":"$prompt"}"""
        
        val aiSuggestionFrame = "SEND\n" +
                            "destination:/app/room/$cleanRoomCode/ai-suggest\n" +
                            "content-type:application/json;charset=utf-8\n" +
                            "content-length:${requestJson.length}\n" +
                            "\n" +
                            "$requestJson" +
                            "\u0000"
                            
        Log.d(TAG, "Sending STOMP AI suggestion frame: $aiSuggestionFrame")
        webSocket.send(aiSuggestionFrame)
    }

    fun requestRestaurantSelection(roomCode: String, strategy: String) {
        Log.d(TAG, "Requesting restaurant selection for room $roomCode using strategy: $strategy")
        
        val cleanRoomCode = extractRoomCode(roomCode)
        val webSocket = roomConnections[cleanRoomCode] ?: run {
            Log.e(TAG, "Cannot request restaurant selection - not connected to room $cleanRoomCode")
            return
        }
        
        val requestJson = """{"strategy":"$strategy"}"""
        
        val selectionFrame = "SEND\n" +
                              "destination:/app/room/$cleanRoomCode/select-restaurant\n" +
                              "content-type:application/json;charset=utf-8\n" +
                              "content-length:${requestJson.length}\n" +
                              "\n" +
                              "$requestJson" +
                              "\u0000"
                            
        Log.d(TAG, "Sending STOMP restaurant selection frame: $selectionFrame")
        webSocket.send(selectionFrame)
    }
    private fun processRoomMessage(body: String, roomCode: String) {
        Log.d(TAG, "Processing room message: $body")
        
        try {
            val message = RoomMessage.fromJson(body)
            
            if (message != null) {
                Log.d(TAG, "Successfully parsed JSON message from server")
                handleJsonMessage(roomCode, message)
            } else {
                try {
                    val participantCount = body.toIntOrNull()
                    if (participantCount != null) {
                        Log.d(TAG, "Received participant count update: $participantCount")
                        val isReady = participantCount >= 2
                        roomListeners[roomCode]?.forEach { listener ->
                            listener.onParticipantUpdate(roomCode, participantCount, isReady, null)
                        }
                    } else {
                        Log.d(TAG, "Received non-numeric update: $body")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing message content: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing topic message: ${e.message}", e)
        }
    }
    
    private fun handleJsonMessage(roomCode: String, message: RoomMessage) {
        Log.d(TAG, "Handling JSON message: $message")
        
        val messageRoomCode = message.roomCode ?: roomCode
        
        when (message.type) {
            RoomMessageType.UPDATE -> {
                if (message.restaurantSuggestions != null && message.restaurantSuggestions.isNotEmpty()) {
                    Log.d(TAG, "Processing restaurant suggestions: ${message.restaurantSuggestions}")
                    
                    val userId = message.userId ?: "Unknown"
                    
                    roomListeners[messageRoomCode]?.forEach { listener ->
                        message.restaurantSuggestions.forEach { suggestion ->
                            listener.onRestaurantSuggestion(messageRoomCode, userId, suggestion)
                        }
                    }
                }
                
                val count = message.participantCount ?: 1
                val isReady = message.isReady ?: false
                Log.d(TAG, "Processing UPDATE message: count=$count, isReady=$isReady, " +
                      "participants=${message.participants?.joinToString() ?: "null"}")
                
                roomListeners[messageRoomCode]?.forEach { listener ->
                    listener.onParticipantUpdate(messageRoomCode, count, isReady, message.participants)
                }
            }
            RoomMessageType.JOIN -> {
                Log.d(TAG, "Received JOIN message: userId=${message.userId}")
            }
            RoomMessageType.LEAVE -> {
                Log.d(TAG, "Received LEAVE message: userId=${message.userId}")
            }
            RoomMessageType.ERROR -> {
                message.message?.let { errorMsg ->
                    Log.e(TAG, "Received ERROR message: $errorMsg")
                    roomListeners[messageRoomCode]?.forEach { listener ->
                        listener.onError(messageRoomCode, errorMsg)
                    }
                }
            }
            RoomMessageType.SELECTION -> {
                val restaurant = message.selectedRestaurant
                val explanation = message.selectionExplanation
                
                if (restaurant != null) {
                    Log.d(TAG, "Received SELECTION message: restaurant=$restaurant, explanation=$explanation")
                    roomListeners[messageRoomCode]?.forEach { listener ->
                        listener.onRestaurantSelected(messageRoomCode, restaurant, explanation ?: "No explanation provided")
                    }
                } else {
                    Log.e(TAG, "Received SELECTION message with null restaurant")
                }
            }
            RoomMessageType.VOTE -> {
                val restaurant = message.selectedRestaurant
                val votes = message.votes
                
                Log.d(TAG, "Received VOTE message: restaurant=$restaurant, votes=$votes")
                
                if (restaurant != null && votes != null) {
                    votes.forEach { (username, approved) ->
                        val voteMessage = "VOTE:$restaurant:$username:$approved"
                        Log.d(TAG, "Processing vote from update: $voteMessage")
                        
                        roomListeners[messageRoomCode]?.forEach { listener ->
                            listener.onRestaurantSuggestion(messageRoomCode, username, voteMessage)
                        }
                    }
                } else if (message.voterUsername != null && message.approved != null) {
                    val username = message.voterUsername
                    val approved = message.approved
                    val voteMessage = "VOTE:$restaurant:$username:$approved"
                    
                    Log.d(TAG, "Processing individual vote: $voteMessage")
                    roomListeners[messageRoomCode]?.forEach { listener ->
                        listener.onRestaurantSuggestion(messageRoomCode, username, voteMessage)
                    }
                }
            }
            else -> {
                Log.d(TAG, "Received unhandled message type: ${message.type}")
            }
        }
    }
    
    fun isConnectedToRoom(roomCode: String): Boolean {
        val cleanRoomCode = extractRoomCode(roomCode)
        val webSocket = roomConnections[cleanRoomCode]
        Log.d(TAG, "Checking connection to room $cleanRoomCode: ${webSocket != null}")
        return webSocket != null
    }
} 