package com.example.finalproject.demo.controller;

import com.example.finalproject.demo.model.Room;
import com.example.finalproject.demo.model.User;
import com.example.finalproject.demo.service.RoomService;
import com.example.finalproject.demo.service.UserService;
import com.example.finalproject.demo.dto.RoomResponse;
import com.example.finalproject.demo.dto.JoinRoomResponse;
import com.example.finalproject.demo.service.RestaurantVotingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import com.example.finalproject.demo.dto.RoomMessage;
import com.example.finalproject.demo.dto.RoomMessageType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RestaurantVotingService restaurantVotingService;
    
    private static final Logger logger = LoggerFactory.getLogger(RoomController.class);
    
    private static String getCurrentUserEmail() {
        return com.example.finalproject.demo.controller.AuthController.getLastLoggedInUserEmail();
    }

    @PostMapping(value = {"/create", ""})
    public ResponseEntity<?> createRoom(@RequestBody(required = false) User creator) {
        logger.info("createRoom method called");
        logger.info("Creator: {}", creator);
        
        try {
            User userToUse = null;
            
            if (creator != null && creator.getEmail() != null && !creator.getEmail().isEmpty()) {
                Optional<User> userOptional = userService.findByEmail(creator.getEmail());
                if (userOptional.isPresent()) {
                    userToUse = userOptional.get();
                    logger.info("Found user by email: {}", userToUse.getUsername());
                }
            }
            
            if (userToUse == null) {
                logger.info("Creator is null or not found, trying to fallback to current user");
                String userEmail = getCurrentUserEmail();
                logger.info("Current user email: {}", userEmail);
                
                if (userEmail != null) {
                    Optional<User> userOptional = userService.findByEmail(userEmail);
                    logger.info("User found: {}", userOptional.isPresent());
                    
                    if (userOptional.isPresent()) {
                        userToUse = userOptional.get();
                        logger.info("Using current user: {}", userToUse.getUsername());
                    }
                }
            }
            
            if (userToUse == null) {
                logger.warn("No valid user found");
                Map<String, String> response = new HashMap<>();
                response.put("message", "No user provided and no current user logged in.");
                response.put("error", "User required");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            
            logger.info("Creating room with user: {}", userToUse.getUsername());
            String inviteCode = roomService.createRoom(userToUse);
            logger.info("Room created with invite code: {}", inviteCode);
            
            RoomResponse roomResponse = new RoomResponse(inviteCode);
            logger.info("Returning response: {}", roomResponse);
            
            return new ResponseEntity<>(roomResponse, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Error in createRoom: ", e);
            Map<String, String> response = new HashMap<>();
            response.put("message", "An error occurred: " + e.getMessage());
            response.put("error", "Internal server error");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestParam String inviteCode) {
        System.out.println("============== REST: JOIN ROOM START ==============");
        System.out.println("Join room API called with inviteCode: " + inviteCode);
        
        String userEmail = getCurrentUserEmail();
        System.out.println("Current user email: " + userEmail);
        
        if (userEmail == null) {
            System.out.println("No user is currently logged in");
            Map<String, String> response = new HashMap<>();
            response.put("message", "No user is currently logged in.");
            response.put("error", "Authentication required");
            System.out.println("Returning unauthorized response");
            System.out.println("============== REST: JOIN ROOM END (UNAUTHORIZED) ==============");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
        
        Optional<User> userOptional = userService.findByEmail(userEmail);
        System.out.println("User found: " + userOptional.isPresent());
        
        if (userOptional.isEmpty()) {
            System.out.println("Current user not found in database");
            Map<String, String> response = new HashMap<>();
            response.put("message", "Current user not found.");
            response.put("error", "User not found");
            System.out.println("Returning unauthorized response");
            System.out.println("============== REST: JOIN ROOM END (USER NOT FOUND) ==============");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
        
        User currentUser = userOptional.get();
        System.out.println("Current user: " + currentUser.getUsername() + " (ID: " + currentUser.getId() + ")");
        
        Optional<Room> roomOptional = roomService.joinRoom(inviteCode, currentUser);
        System.out.println("Room found and joined: " + roomOptional.isPresent());
        
        if (roomOptional.isPresent()) {
            Room room = roomOptional.get();
            System.out.println("Room ID: " + room.getId());
            System.out.println("Room users: " + room.getUsers().size());
            
            try {
                System.out.println("Attempting to send WebSocket notification");
                String destination = "/app/room/" + inviteCode + "/join";
                Long payload = currentUser.getId();
                
                System.out.println("WS Destination: " + destination);
                System.out.println("WS Payload: " + payload);
                
                messagingTemplate.convertAndSend(destination, payload);
                System.out.println("WebSocket notification sent successfully");
                
                logger.info("Sent WebSocket join notification for user {} in room {}", currentUser.getUsername(), inviteCode);
            } catch (Exception e) {
                System.out.println("Error sending WebSocket notification: " + e.getMessage());
                e.printStackTrace();
                logger.error("Error sending WebSocket notification: ", e);
            }
            
            JoinRoomResponse joinResponse = new JoinRoomResponse(room.getId());
            System.out.println("Prepared join response: " + joinResponse);
            System.out.println("============== REST: JOIN ROOM END (SUCCESS) ==============");
            return ResponseEntity.ok(joinResponse);
        } else {
            System.out.println("Invalid invite code: " + inviteCode);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Invalid invite code!");
            response.put("error", "Room not found");
            System.out.println("Returning not found response");
            System.out.println("============== REST: JOIN ROOM END (ROOM NOT FOUND) ==============");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/{roomId}/suggest")
    public ResponseEntity<?> addRestaurantSuggestion(
            @PathVariable Long roomId,
            @RequestParam String restaurantName) {
        
        boolean added = roomService.addRestaurantSuggestion(roomId, restaurantName);
        
        if (added) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Restaurant suggestion added successfully!");
            response.put("roomId", roomId);
            response.put("restaurantName", restaurantName);
            return ResponseEntity.ok(response);
        } else {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Could not add restaurant suggestion. Room not found or restaurant already suggested.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }
    
    @PostMapping("/{roomId}/vote")
    public ResponseEntity<?> voteForRestaurant(
            @PathVariable Long roomId,
            @RequestParam String restaurantName) {
        
        boolean voted = roomService.voteForRestaurant(roomId, restaurantName);
        
        if (voted) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Vote recorded successfully!");
            response.put("roomId", roomId);
            response.put("restaurantName", restaurantName);
            return ResponseEntity.ok(response);
        } else {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Could not vote for restaurant. Room or restaurant not found.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }
    
    @GetMapping("/{roomId}/bestRestaurant")
    public ResponseEntity<?> getBestRestaurant(@PathVariable Long roomId) {
        Optional<String> bestRestaurantOpt = roomService.getBestRestaurant(roomId);
        
        if (bestRestaurantOpt.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("roomId", roomId);
            response.put("bestRestaurant", bestRestaurantOpt.get());
            return ResponseEntity.ok(response);
        } else {
            Map<String, String> response = new HashMap<>();
            response.put("error", "No best restaurant found. Room may not exist or no votes have been cast.");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/test-create")
    public ResponseEntity<?> testCreateRoom(@RequestBody(required = false) Map<String, Object> requestBody) {
        logger.info("testCreateRoom method called");
        logger.info("Request body: {}", requestBody);
        
        try {
            Optional<User> userOptional = userService.findById(1L);
            if (userOptional.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("message", "No user found with ID 1");
                errorResponse.put("error", "User not found");
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
            }
            
            String inviteCode = roomService.createRoom(userOptional.get());
            RoomResponse roomResponse = new RoomResponse(inviteCode);
            
            return new ResponseEntity<>(roomResponse, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Error in testCreateRoom: ", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error: " + e.getMessage());
            errorResponse.put("error", "Internal server error");
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/test")
    public ResponseEntity<?> testEndpoint() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Room controller is working!");
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roomId}/suggestions")
    public ResponseEntity<?> getRoomSuggestions(@PathVariable Long roomId) {
        String userEmail = getCurrentUserEmail();
        if (userEmail == null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "No user is currently logged in.");
            response.put("error", "Authentication required");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
        
        Optional<Room> roomOptional = roomService.findById(roomId);
        if (roomOptional.isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Room not found with id: " + roomId);
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
        
        Room room = roomOptional.get();
        
        Map<String, Object> response = new HashMap<>();
        response.put("roomId", roomId);
        response.put("restaurantSuggestions", room.getRestaurantSuggestions());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/ai-suggest")
    public ResponseEntity<?> addAiRestaurantSuggestions(
            @PathVariable Long roomId,
            @RequestParam String prompt) {
        
        String userEmail = getCurrentUserEmail();
        if (userEmail == null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "No user is currently logged in.");
            response.put("error", "Authentication required");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
        
        Optional<Room> roomOptional = roomService.findById(roomId);
        if (roomOptional.isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Room not found with id: " + roomId);
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
        
        int addedCount = roomService.addAiRestaurantSuggestions(roomId, prompt);
        
        roomOptional = roomService.findById(roomId);
        Room room = roomOptional.get();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", addedCount + " AI-generated restaurant suggestions added successfully!");
        response.put("roomId", roomId);
        response.put("restaurantSuggestions", room.getRestaurantSuggestions());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{roomId}/start-vote")
    public ResponseEntity<?> startRestaurantVoting(
            @PathVariable Long roomId,
            @RequestParam String restaurant) {
        
        String userEmail = getCurrentUserEmail();
        if (userEmail == null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "No user is currently logged in.");
            response.put("error", "Authentication required");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
        
        Optional<Room> roomOptional = roomService.findById(roomId);
        if (roomOptional.isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Room not found with id: " + roomId);
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
        
        Room room = roomOptional.get();
        String inviteCode = room.getInviteCode();
        
        try {
            restaurantVotingService.clearVotes(inviteCode, restaurant);
            
            RoomMessage voteMessage = new RoomMessage();
            voteMessage.setType(RoomMessageType.VOTE);
            voteMessage.setRoomCode(inviteCode);
            voteMessage.setSelectedRestaurant(restaurant);
            voteMessage.setVotes(new HashMap<>());
            
            messagingTemplate.convertAndSend("/topic/room/" + inviteCode, voteMessage);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Voting started for restaurant: " + restaurant);
            response.put("roomId", roomId);
            response.put("inviteCode", inviteCode);
            response.put("restaurant", restaurant);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Error starting vote: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
} 