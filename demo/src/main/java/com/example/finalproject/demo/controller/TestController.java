package com.example.finalproject.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import com.example.finalproject.demo.dto.RoomMessage;
import com.example.finalproject.demo.service.RoomService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RoomService roomService;

    @GetMapping("/websocket-status")
    public ResponseEntity<?> getWebSocketStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "WebSocket endpoints are configured");
        response.put("endpoints", new String[] {"/ws", "/ws/websocket"});
        response.put("message", "Use these endpoints for WebSocket connections");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/send-test-message/{roomCode}")
    public ResponseEntity<?> sendTestMessage(
            @PathVariable String roomCode,
            @RequestParam(defaultValue = "This is a test message") String message) {
        
        try {
            RoomMessage testMessage = RoomMessage.createUpdateMessage(
                1,
                true,
                java.util.Arrays.asList("TestUser"),
                java.util.Arrays.asList(message)
            );
            
            String destination = "/topic/room/" + roomCode;
            messagingTemplate.convertAndSend(destination, testMessage);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Test message sent to " + destination);
            response.put("content", testMessage);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send test message: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/test-suggestion/{roomCode}")
    public ResponseEntity<?> testAddSuggestion(
            @PathVariable String roomCode,
            @RequestParam String suggestion) {
        
        try {
            var roomOpt = roomService.getRoomByInviteCode(roomCode);
            if (roomOpt.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Room not found: " + roomCode);
                return ResponseEntity.badRequest().body(response);
            }
            
            var room = roomOpt.get();
            boolean added = roomService.addRestaurantSuggestion(room.getId(), suggestion);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", added ? "success" : "failed");
            response.put("roomCode", roomCode);
            response.put("roomId", room.getId());
            response.put("suggestion", suggestion);
            response.put("message", added ? "Suggestion added" : "Suggestion not added");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 