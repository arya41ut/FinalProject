package com.example.finalproject.demo.service;

import com.example.finalproject.demo.model.Room;
import com.example.finalproject.demo.model.User;
import com.example.finalproject.demo.repository.RoomRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import com.example.finalproject.demo.dto.ChatGptRequest;
import com.example.finalproject.demo.dto.ChatGptResponse;
import com.example.finalproject.demo.service.ChatGptService;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;
    private final ChatGptService chatGptService;
    // Map to store votes for each room: roomId -> (restaurant -> voteCount)
    private final Map<Long, Map<String, Integer>> roomVotes = new HashMap<>();
    
    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);
    private static final String ALPHANUMERIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private final Random random = new Random();

    /**
     * Generates a random 6-character alphanumeric code
     * @return A 6-character alphanumeric code
     */
    private String generateInviteCode() {
        StringBuilder code = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            int index = random.nextInt(ALPHANUMERIC_CHARS.length());
            code.append(ALPHANUMERIC_CHARS.charAt(index));
        }
        return code.toString();
    }
    
    /**
     * Creates a new room with the given creator
     * @param creator The user creating the room
     * @return The invite code for the room
     */
    public String createRoom(User creator) {
        Room room = new Room();
        // Let the database generate the ID
        room.setUsers(new ArrayList<>(List.of(creator)));
        room.setRestaurantSuggestions(new ArrayList<>());

        // Generate a 6-character alphanumeric invite code
        String inviteCode = generateInviteCode();
        
        // Make sure the invite code is unique
        while (roomRepository.findByInviteCode(inviteCode).isPresent()) {
            logger.info("Invite code collision detected: {}, generating new code", inviteCode);
            inviteCode = generateInviteCode();
        }
        
        logger.info("Generated invite code: {}", inviteCode);
        room.setInviteCode(inviteCode);

        // Save room to the database
        Room savedRoom = roomRepository.save(room);
        
        // Initialize votes map for this room
        roomVotes.put(savedRoom.getId(), new HashMap<>());

        return inviteCode;
    }

    public Optional<Room> joinRoom(String inviteCode, User user) {
        Optional<Room> roomOptional = roomRepository.findByInviteCode(inviteCode);
        if (roomOptional.isPresent()) {
            Room room = roomOptional.get();
            room.getUsers().add(user);
            // Save updated room to the database
            roomRepository.save(room);
            return Optional.of(room);
        }
        return Optional.empty();
    }
    
    public boolean addRestaurantSuggestion(Long roomId, String restaurantName) {
        Optional<Room> roomOptional = roomRepository.findById(roomId);
        if (roomOptional.isPresent()) {
            Room room = roomOptional.get();
            List<String> suggestions = room.getRestaurantSuggestions();
            
            // Check if suggestion already exists
            if (!suggestions.contains(restaurantName)) {
                suggestions.add(restaurantName);
                roomRepository.save(room);
                
                // Initialize votes for this restaurant
                roomVotes.computeIfAbsent(roomId, k -> new HashMap<>()).put(restaurantName, 0);
                
                return true;
            }
        }
        return false;
    }
    
    public boolean voteForRestaurant(Long roomId, String restaurantName) {
        Optional<Room> roomOptional = roomRepository.findById(roomId);
        if (roomOptional.isPresent()) {
            Room room = roomOptional.get();
            
            // Check if restaurant exists in this room
            if (room.getRestaurantSuggestions().contains(restaurantName)) {
                // Increment vote count
                Map<String, Integer> votes = roomVotes.computeIfAbsent(roomId, k -> new HashMap<>());
                votes.put(restaurantName, votes.getOrDefault(restaurantName, 0) + 1);
                return true;
            }
        }
        return false;
    }
    
    public Optional<String> getBestRestaurant(Long roomId) {
        Optional<Room> roomOptional = roomRepository.findById(roomId);
        if (roomOptional.isPresent() && roomVotes.containsKey(roomId)) {
            Map<String, Integer> votes = roomVotes.get(roomId);
            
            if (votes.isEmpty()) {
                return Optional.empty();
            }
            
            // Find restaurant with maximum votes
            return Optional.of(Collections.max(votes.entrySet(), Map.Entry.comparingByValue()).getKey());
        }
        return Optional.empty();
    }

    /**
     * Gets a room by its invite code
     * @param inviteCode The invite code of the room
     * @return Optional containing the room if found, or empty if not
     */
    public Optional<Room> getRoomByInviteCode(String inviteCode) {
        return roomRepository.findByInviteCode(inviteCode);
    }

    /**
     * Gets a room by its ID
     * @param id The ID of the room
     * @return Optional containing the room if found, or empty if not
     */
    public Optional<Room> findById(Long id) {
        return roomRepository.findById(id);
    }
    
    /**
     * Saves a room to the database
     * @param room The room to save
     * @return The saved room
     */
    public Room saveRoom(Room room) {
        return roomRepository.save(room);
    }

    /**
     * Gets AI-generated restaurant suggestions for a room based on a prompt
     * 
     * @param roomId The ID of the room
     * @param prompt The prompt to generate suggestions from
     * @return List of restaurant suggestions
     */
    public List<String> getAiRestaurantSuggestions(Long roomId, String prompt) {
        logger.info("Getting AI restaurant suggestions for room: {}", roomId);
        
        // Get existing suggestions for context if needed
        Optional<Room> roomOptional = roomRepository.findById(roomId);
        List<String> existingSuggestions = roomOptional.map(Room::getRestaurantSuggestions)
            .orElse(Collections.emptyList());
        
        // Append existing suggestions to prompt if there are any
        String enrichedPrompt = prompt;
        if (!existingSuggestions.isEmpty()) {
            enrichedPrompt += ". Current suggestions include: " + 
                existingSuggestions.stream().collect(Collectors.joining(", "));
        }
        
        // Generate suggestions using ChatGPT service
        ChatGptResponse response = chatGptService.getRestaurantSuggestions(
            new ChatGptRequest(enrichedPrompt, "restaurant_suggestion")
        );
        
        // Handle the response
        if (response.getError() != null) {
            logger.error("Error getting AI suggestions: {}", response.getError());
            return Collections.emptyList();
        }
        
        return response.getSuggestions();
    }
    
    /**
     * Adds AI-generated restaurant suggestions to a room
     * 
     * @param roomId The ID of the room
     * @param prompt The prompt to generate suggestions from
     * @return Number of suggestions added
     */
    public int addAiRestaurantSuggestions(Long roomId, String prompt) {
        List<String> suggestions = getAiRestaurantSuggestions(roomId, prompt);
        int addedCount = 0;
        
        for (String suggestion : suggestions) {
            boolean added = addRestaurantSuggestion(roomId, suggestion);
            if (added) {
                addedCount++;
            }
        }
        
        logger.info("Added {} AI-generated restaurant suggestions to room {}", addedCount, roomId);
        return addedCount;
    }
} 