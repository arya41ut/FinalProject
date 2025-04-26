package com.example.finalproject.demo.controller;

import com.example.finalproject.demo.dto.RoomMessage;
import com.example.finalproject.demo.dto.RoomUpdateMessage;
import com.example.finalproject.demo.dto.AiSuggestionRequest;
import com.example.finalproject.demo.dto.RestaurantSelectionRequest;
import com.example.finalproject.demo.model.Room;
import com.example.finalproject.demo.model.User;
import com.example.finalproject.demo.service.RoomService;
import com.example.finalproject.demo.service.UserService;
import com.example.finalproject.demo.service.RestaurantSelectionService;
import com.example.finalproject.demo.service.RestaurantVotingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class RoomWebSocketController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final RestaurantSelectionService restaurantSelectionService;
    private final RestaurantVotingService restaurantVotingService;
    private static final Logger logger = LoggerFactory.getLogger(RoomWebSocketController.class);

    @Transactional
    @MessageMapping("/room/{inviteCode}/join")
    public void handleJoinRoom(@DestinationVariable String inviteCode, @Payload Object userIdPayload) {
        System.out.println("============== WEBSOCKET: JOIN ROOM START ==============");
        System.out.println("Received join request: inviteCode=" + inviteCode + ", userIdPayload=" + userIdPayload + " (type: " + (userIdPayload != null ? userIdPayload.getClass().getName() : "null") + ")");
        
        Long parsedUserId;
        try {
            if (userIdPayload == null) {
                parsedUserId = 0L;
            } else if (userIdPayload instanceof Number) {
                parsedUserId = ((Number) userIdPayload).longValue();
            } else if (userIdPayload instanceof String) {
                String cleaned = ((String) userIdPayload).replaceAll("[^0-9]", "");
                if (!cleaned.isEmpty()) {
                    parsedUserId = Long.parseLong(cleaned);
                } else {
                    parsedUserId = 0L;
                }
            } else if (userIdPayload instanceof byte[]) {
                String str = new String((byte[]) userIdPayload);
                System.out.println("Converted byte array to string: " + str);
                str = str.replaceAll("[^0-9]", "");
                if (!str.isEmpty()) {
                    parsedUserId = Long.parseLong(str);
                } else {
                    parsedUserId = 0L;
                }
            } else {
                String str = userIdPayload.toString().replaceAll("[^0-9]", "");
                if (!str.isEmpty()) {
                    parsedUserId = Long.parseLong(str);
                } else {
                    parsedUserId = 0L;
                }
            }
        } catch (Exception e) {
            System.out.println("Error parsing userId from payload: " + e.getMessage());
            parsedUserId = 0L;
        }
        
        final Long userId = parsedUserId;
        
        System.out.println("Parsed userId: " + userId);
        logger.info("Received join room request for inviteCode: {} from user: {}", inviteCode, userId);
        
        Optional<Room> roomOpt = roomService.getRoomByInviteCode(inviteCode);
        System.out.println("Room found: " + roomOpt.isPresent());
        
        if (roomOpt.isPresent()) {
            Room room = roomOpt.get();
            
            if (userId > 0) {
                boolean userAdded = false;
                try {
                    Optional<User> joiningUser = userService.findById(userId);
                    if (joiningUser.isPresent()) {
                        User user = joiningUser.get();
                        boolean userExists = room.getUsers().stream()
                            .anyMatch(u -> u.getId().equals(userId));
                        
                        if (!userExists) {
                            room.getUsers().add(user);
                            roomService.saveRoom(room);
                            System.out.println("Added user " + user.getUsername() + " to room");
                            userAdded = true;
                        } else {
                            System.out.println("User already in room");
                        }
                    } else {
                        System.out.println("User with ID " + userId + " not found");
                    }
                } catch (Exception e) {
                    System.out.println("Error adding user to room: " + e.getMessage());
                }
                
                if (userAdded) {
                    roomOpt = roomService.getRoomByInviteCode(inviteCode);
                    if (roomOpt.isPresent()) {
                        room = roomOpt.get();
                    }
                }
            }
            
            int participantCount = room.getUsers().size();
            System.out.println("Room ID: " + room.getId() + ", Participant count: " + participantCount);
            
            List<String> participants = room.getUsers().stream()
                .map(User::getUsername)
                .collect(Collectors.toList());
            System.out.println("Participants: " + participants);
            
            System.out.println("Restaurant suggestions: " + room.getRestaurantSuggestions());
            
            RoomMessage message = RoomMessage.createUpdateMessage(
                participantCount,
                false,
                participants,
                room.getRestaurantSuggestions()
            );
            System.out.println("Created message: " + message);
            
            String messageJson = message.toJson();
            System.out.println("Message JSON: " + messageJson);
            
            String destination = "/topic/room/" + inviteCode;
            System.out.println("Sending to destination: " + destination);
            messagingTemplate.convertAndSend(destination, message);
            
            logger.info("Sent join update for room: {}, participant count: {}", inviteCode, participantCount);
            System.out.println("Message sent successfully");
        } else {
            System.out.println("Room not found with invite code: " + inviteCode);
            logger.warn("Room not found with inviteCode: {}", inviteCode);
            
            RoomMessage errorMessage = RoomMessage.createErrorMessage("Room not found with invite code: " + inviteCode);
            System.out.println("Created error message: " + errorMessage);
            
            String destination = "/topic/room/" + inviteCode;
            System.out.println("Sending error to destination: " + destination);
            messagingTemplate.convertAndSend(destination, errorMessage);
            System.out.println("Error message sent");
        }
        System.out.println("============== WEBSOCKET: JOIN ROOM END ==============");
    }
    
    @Transactional
    @MessageMapping("/room/{inviteCode}/leave")
    public void handleLeaveRoom(@DestinationVariable String inviteCode, @Payload Object userIdPayload) {
        System.out.println("============== WEBSOCKET: LEAVE ROOM START ==============");
        System.out.println("Received leave request: inviteCode=" + inviteCode + ", userIdPayload=" + userIdPayload + " (type: " + (userIdPayload != null ? userIdPayload.getClass().getName() : "null") + ")");
        
        Long parsedUserId;
        try {
            if (userIdPayload == null) {
                parsedUserId = 0L;
            } else if (userIdPayload instanceof Number) {
                parsedUserId = ((Number) userIdPayload).longValue();
            } else if (userIdPayload instanceof String) {
                String cleaned = ((String) userIdPayload).replaceAll("[^0-9]", "");
                if (!cleaned.isEmpty()) {
                    parsedUserId = Long.parseLong(cleaned);
                } else {
                    parsedUserId = 0L;
                }
            } else if (userIdPayload instanceof byte[]) {
                String str = new String((byte[]) userIdPayload);
                System.out.println("Converted byte array to string: " + str);
                str = str.replaceAll("[^0-9]", "");
                if (!str.isEmpty()) {
                    parsedUserId = Long.parseLong(str);
                } else {
                    parsedUserId = 0L;
                }
            } else {
                String str = userIdPayload.toString().replaceAll("[^0-9]", "");
                if (!str.isEmpty()) {
                    parsedUserId = Long.parseLong(str);
                } else {
                    parsedUserId = 0L;
                }
            }
        } catch (Exception e) {
            System.out.println("Error parsing userId from payload: " + e.getMessage());
            parsedUserId = 0L;
        }
        
        final Long userId = parsedUserId;
        
        System.out.println("Parsed userId: " + userId);
        logger.info("Received leave room request for inviteCode: {} from user: {}", inviteCode, userId);
        
        Optional<Room> roomOpt = roomService.getRoomByInviteCode(inviteCode);
        System.out.println("Room found: " + roomOpt.isPresent());
        
        if (roomOpt.isPresent()) {
            Room room = roomOpt.get();
            int participantCount = room.getUsers().size();
            System.out.println("Room ID: " + room.getId() + ", Participant count: " + participantCount);
            
            List<String> participants = room.getUsers().stream()
                .map(User::getUsername)
                .collect(Collectors.toList());
            System.out.println("Participants: " + participants);
            
            RoomMessage message = RoomMessage.createUpdateMessage(
                participantCount,
                false,
                participants,
                room.getRestaurantSuggestions()
            );
            System.out.println("Created message: " + message);
            
            String messageJson = message.toJson();
            System.out.println("Message JSON: " + messageJson);
            
            String destination = "/topic/room/" + inviteCode;
            System.out.println("Sending to destination: " + destination);
            messagingTemplate.convertAndSend(destination, message);
            
            logger.info("Sent leave update for room: {}, participant count: {}", inviteCode, participantCount);
            System.out.println("Message sent successfully");
        } else {
            System.out.println("Room not found with invite code: " + inviteCode);
            logger.warn("Room not found with inviteCode: {}", inviteCode);
            
            RoomMessage errorMessage = RoomMessage.createErrorMessage("Room not found with invite code: " + inviteCode);
            System.out.println("Created error message: " + errorMessage);
            
            String destination = "/topic/room/" + inviteCode;
            System.out.println("Sending error to destination: " + destination);
            messagingTemplate.convertAndSend(destination, errorMessage);
            System.out.println("Error message sent");
        }
        System.out.println("============== WEBSOCKET: LEAVE ROOM END ==============");
    }

    @Transactional
    @MessageMapping("/room/{inviteCode}/suggest")
    public void handleSuggestion(@DestinationVariable String inviteCode, @Payload String restaurantName) {
        System.out.println("============== WEBSOCKET: SUGGEST RESTAURANT START ==============");
        System.out.println("Received suggestion: inviteCode=" + inviteCode + ", restaurant='" + restaurantName + "'");
        logger.info("Received restaurant suggestion for room: {}: '{}'", inviteCode, restaurantName);
        
        try {
            if (restaurantName.startsWith("VOTE:")) {
                handleVoteMessage(inviteCode, restaurantName);
                return;
            }

            String cleanRestaurantName = restaurantName.trim();
            System.out.println("Cleaned restaurant name: '" + cleanRestaurantName + "'");
            
            Optional<Room> roomOpt = roomService.getRoomByInviteCode(inviteCode);
            System.out.println("Room found: " + roomOpt.isPresent());
            
            if (roomOpt.isPresent()) {
                Room room = roomOpt.get();
                System.out.println("Room ID: " + room.getId());
                System.out.println("Adding suggestion: '" + cleanRestaurantName + "'");
                
                boolean added = roomService.addRestaurantSuggestion(room.getId(), cleanRestaurantName);
                System.out.println("Suggestion added successfully: " + added);
                
                roomOpt = roomService.getRoomByInviteCode(inviteCode);
                System.out.println("Updated room found: " + roomOpt.isPresent());
                
                if (roomOpt.isPresent()) {
                    Room updatedRoom = roomOpt.get();
                    System.out.println("Updated suggestions: " + updatedRoom.getRestaurantSuggestions());
                    
                    List<String> participants = updatedRoom.getUsers().stream()
                        .map(User::getUsername)
                        .collect(Collectors.toList());
                    System.out.println("Participants: " + participants);
                    
                    RoomMessage message = RoomMessage.createUpdateMessage(
                        updatedRoom.getUsers().size(),
                        false,
                        participants,
                        updatedRoom.getRestaurantSuggestions()
                    );
                    System.out.println("Created message: " + message);
                    
                    String messageJson = message.toJson();
                    System.out.println("Message JSON: " + messageJson);
                    
                    String destination = "/topic/room/" + inviteCode;
                    System.out.println("Sending to destination: " + destination);
                    messagingTemplate.convertAndSend(destination, message);
                    
                    logger.info("Sent suggestion update for room: {}, suggestions: {}", 
                        inviteCode, updatedRoom.getRestaurantSuggestions());
                    System.out.println("Message sent successfully");
                } else {
                    System.out.println("Updated room not found (strange error)");
                }
            } else {
                System.out.println("Room not found with invite code: " + inviteCode);
                logger.warn("Room not found with inviteCode: {}", inviteCode);
                
                RoomMessage errorMessage = RoomMessage.createErrorMessage("Room not found with invite code: " + inviteCode);
                System.out.println("Created error message: " + errorMessage);
                
                String destination = "/topic/room/" + inviteCode;
                System.out.println("Sending error to destination: " + destination);
                messagingTemplate.convertAndSend(destination, errorMessage);
                System.out.println("Error message sent");
            }
        } catch (Exception e) {
            logger.error("Error processing restaurant suggestion: ", e);
            System.out.println("Error processing suggestion: " + e.getMessage());
            e.printStackTrace();
            
            RoomMessage errorMessage = RoomMessage.createErrorMessage("Error processing suggestion: " + e.getMessage());
            String destination = "/topic/room/" + inviteCode;
            messagingTemplate.convertAndSend(destination, errorMessage);
        }
        System.out.println("============== WEBSOCKET: SUGGEST RESTAURANT END ==============");
    }

    private void handleVoteMessage(String inviteCode, String voteMessage) {
        System.out.println("============== WEBSOCKET: HANDLE VOTE START ==============");
        System.out.println("Processing vote message: " + voteMessage);
        
        try {
            String[] parts = voteMessage.split(":");
            if (parts.length < 4) {
                System.out.println("Invalid vote message format: " + voteMessage);
                logger.warn("Invalid vote message format: {}", voteMessage);
                
                RoomMessage errorMessage = RoomMessage.createErrorMessage("Invalid vote message format");
                messagingTemplate.convertAndSend("/topic/room/" + inviteCode, errorMessage);
                return;
            }
            
            String restaurant = parts[1];
            String username = parts[2];
            boolean approved = Boolean.parseBoolean(parts[3]);
            
            System.out.println("Parsed vote: restaurant='" + restaurant + "', username='" + username + "', approved=" + approved);
            
            Optional<Room> roomOpt = roomService.getRoomByInviteCode(inviteCode);
            if (roomOpt.isEmpty()) {
                System.out.println("Room not found with invite code: " + inviteCode);
                logger.warn("Room not found with inviteCode: {}", inviteCode);
                
                RoomMessage errorMessage = RoomMessage.createErrorMessage("Room not found with invite code: " + inviteCode);
                messagingTemplate.convertAndSend("/topic/room/" + inviteCode, errorMessage);
                return;
            }
            
            Room room = roomOpt.get();
            int participantCount = room.getUsers().size();
            
            Map<String, Boolean> votes = restaurantVotingService.recordVote(inviteCode, restaurant, username, approved);
            
            RoomMessage voteUpdateMessage = RoomMessage.createVoteUpdateMessage(inviteCode, restaurant, votes);
            messagingTemplate.convertAndSend("/topic/room/" + inviteCode, voteUpdateMessage);
            
            boolean allVoted = restaurantVotingService.allParticipantsVoted(inviteCode, restaurant, participantCount);
            if (allVoted) {
                boolean allApproved = restaurantVotingService.allVotesApproved(inviteCode, restaurant);
                
                System.out.println("All participants voted. All approved: " + allApproved);
                
                if (allApproved) {
                    RoomMessage selectionMessage = RoomMessage.createSelectionMessage(
                        restaurant, 
                        "Selected by unanimous approval", 
                        inviteCode
                    );
                    messagingTemplate.convertAndSend("/topic/room/" + inviteCode, selectionMessage);
                    
                    restaurantVotingService.clearVotes(inviteCode, restaurant);
                } else {
                    RoomMessage rejectionMessage = RoomMessage.createErrorMessage(
                        "Restaurant '" + restaurant + "' was not unanimously approved"
                    );
                    messagingTemplate.convertAndSend("/topic/room/" + inviteCode, rejectionMessage);
                    
                    restaurantVotingService.clearVotes(inviteCode, restaurant);
                }
            }
            
        } catch (Exception e) {
            System.out.println("Error processing vote message: " + e.getMessage());
            e.printStackTrace();
            logger.error("Error processing vote message", e);
            
            RoomMessage errorMessage = RoomMessage.createErrorMessage("Error processing vote: " + e.getMessage());
            messagingTemplate.convertAndSend("/topic/room/" + inviteCode, errorMessage);
        }
        
        System.out.println("============== WEBSOCKET: HANDLE VOTE END ==============");
    }

    @Transactional
    @MessageMapping("/room/{inviteCode}/ai-suggest")
    public void handleAiSuggestion(@DestinationVariable String inviteCode, @Payload AiSuggestionRequest request) {
        System.out.println("============== WEBSOCKET: AI SUGGEST RESTAURANT START ==============");
        System.out.println("Received AI suggestion request: inviteCode=" + inviteCode + ", prompt=" + request.getPrompt());
        logger.info("Received AI restaurant suggestion request for room: {}: {}", inviteCode, request.getPrompt());
        
        Optional<Room> roomOpt = roomService.getRoomByInviteCode(inviteCode);
        System.out.println("Room found: " + roomOpt.isPresent());
        
        if (roomOpt.isPresent()) {
            Room room = roomOpt.get();
            System.out.println("Room ID: " + room.getId());
            System.out.println("Generating suggestions for prompt: " + request.getPrompt());
            
            int addedCount = roomService.addAiRestaurantSuggestions(room.getId(), request.getPrompt());
            System.out.println("Added " + addedCount + " AI-generated suggestions");
            
            roomOpt = roomService.getRoomByInviteCode(inviteCode);
            System.out.println("Updated room found: " + roomOpt.isPresent());
            
            if (roomOpt.isPresent()) {
                Room updatedRoom = roomOpt.get();
                System.out.println("Updated suggestions: " + updatedRoom.getRestaurantSuggestions());
                
                List<String> participants = updatedRoom.getUsers().stream()
                    .map(User::getUsername)
                    .collect(Collectors.toList());
                System.out.println("Participants: " + participants);
                
                RoomMessage message = RoomMessage.createUpdateMessage(
                    updatedRoom.getUsers().size(),
                    false,
                    participants,
                    updatedRoom.getRestaurantSuggestions()
                );
                System.out.println("Created message: " + message);
                
                String messageJson = message.toJson();
                System.out.println("Message JSON: " + messageJson);
                
                String destination = "/topic/room/" + inviteCode;
                System.out.println("Sending to destination: " + destination);
                messagingTemplate.convertAndSend(destination, message);
                
                logger.info("Sent AI suggestion update for room: {}, suggestions: {}", 
                    inviteCode, updatedRoom.getRestaurantSuggestions());
                System.out.println("Message sent successfully");
            } else {
                System.out.println("Updated room not found (strange error)");
            }
        } else {
            System.out.println("Room not found with invite code: " + inviteCode);
            logger.warn("Room not found with inviteCode: {}", inviteCode);
            
            RoomMessage errorMessage = RoomMessage.createErrorMessage("Room not found with invite code: " + inviteCode);
            System.out.println("Created error message: " + errorMessage);
            
            String destination = "/topic/room/" + inviteCode;
            System.out.println("Sending error to destination: " + destination);
            messagingTemplate.convertAndSend(destination, errorMessage);
            System.out.println("Error message sent");
        }
        System.out.println("============== WEBSOCKET: AI SUGGEST RESTAURANT END ==============");
    }

    @Transactional
    @MessageMapping("/room/{inviteCode}/select-restaurant")
    public void handleRestaurantSelection(@DestinationVariable String inviteCode, 
                                         @Payload RestaurantSelectionRequest request) {
        System.out.println("============== WEBSOCKET: SELECT RESTAURANT START ==============");
        System.out.println("Received restaurant selection request: inviteCode=" + inviteCode + 
                         ", strategy=" + request.getStrategy());
        logger.info("Received restaurant selection request for room: {}, strategy: {}", 
                   inviteCode, request.getStrategy());
        
        Optional<Room> roomOpt = roomService.getRoomByInviteCode(inviteCode);
        System.out.println("Room found: " + roomOpt.isPresent());
        
        if (roomOpt.isPresent()) {
            Room room = roomOpt.get();
            System.out.println("Room ID: " + room.getId());
            
            Map<String, String> result = restaurantSelectionService.selectRestaurant(
                room.getId(), request.getStrategy());
            
            if (result.isEmpty()) {
                RoomMessage errorMessage = RoomMessage.createErrorMessage(
                    "Could not select a restaurant. Make sure there are suggestions available.");
                String destination = "/topic/room/" + inviteCode;
                messagingTemplate.convertAndSend(destination, errorMessage);
                
                logger.warn("Could not select restaurant for room: {}", inviteCode);
                System.out.println("Selection failed - no result returned");
            } else {
                String selectedRestaurant = result.get("restaurant");
                String explanation = result.get("explanation");
                
                System.out.println("Selected restaurant: " + selectedRestaurant);
                System.out.println("Selection explanation: " + explanation);
                
                RoomMessage selectionMessage = RoomMessage.createSelectionMessage(
                    selectedRestaurant, explanation, inviteCode);
                
                String messageJson = selectionMessage.toJson();
                System.out.println("Selection Message JSON: " + messageJson);
                
                String destination = "/topic/room/" + inviteCode;
                System.out.println("Sending to destination: " + destination);
                messagingTemplate.convertAndSend(destination, selectionMessage);
                
                logger.info("Sent restaurant selection for room: {}, selected: {}", 
                    inviteCode, selectedRestaurant);
                System.out.println("Selection message sent successfully");
            }
        } else {
            System.out.println("Room not found with invite code: " + inviteCode);
            logger.warn("Room not found with inviteCode: {}", inviteCode);
            
            RoomMessage errorMessage = RoomMessage.createErrorMessage(
                "Room not found with invite code: " + inviteCode);
            System.out.println("Created error message: " + errorMessage);
            
            String destination = "/topic/room/" + inviteCode;
            System.out.println("Sending error to destination: " + destination);
            messagingTemplate.convertAndSend(destination, errorMessage);
            System.out.println("Error message sent");
        }
        System.out.println("============== WEBSOCKET: SELECT RESTAURANT END ==============");
    }
} 