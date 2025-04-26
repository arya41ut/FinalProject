package com.example.finalproject.demo.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomMessage {
    private static final Logger logger = LoggerFactory.getLogger(RoomMessage.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private RoomMessageType type;
    private String message;
    private Integer participantCount;
    private Boolean isReady;
    private List<String> participants;
    private List<String> restaurantSuggestions;
    private String roomCode;
    private String userId;
    private String selectedRestaurant;
    private String selectionExplanation;
    private String voterUsername;
    private Boolean approved;
    private Map<String, Boolean> votes;
    
    public static RoomMessage createUpdateMessage(int participantCount, boolean isReady, 
                                                List<String> participants,
                                                List<String> restaurantSuggestions) {
        RoomMessage message = new RoomMessage();
        message.setType(RoomMessageType.UPDATE);
        message.setParticipantCount(participantCount);
        message.setIsReady(isReady);
        message.setParticipants(participants);
        message.setRestaurantSuggestions(restaurantSuggestions);
        return message;
    }
    
    public static RoomMessage createErrorMessage(String errorMessage) {
        RoomMessage message = new RoomMessage();
        message.setType(RoomMessageType.ERROR);
        message.setMessage(errorMessage);
        return message;
    }
    
    public static RoomMessage createSelectionMessage(String restaurant, String explanation, String roomCode) {
        RoomMessage message = new RoomMessage();
        message.setType(RoomMessageType.SELECTION);
        message.setSelectedRestaurant(restaurant);
        message.setSelectionExplanation(explanation);
        message.setRoomCode(roomCode);
        return message;
    }
    
    public static RoomMessage createVoteMessage(String roomCode, String username, String restaurant, boolean approved) {
        RoomMessage message = new RoomMessage();
        message.setType(RoomMessageType.VOTE);
        message.setRoomCode(roomCode);
        message.setVoterUsername(username);
        message.setSelectedRestaurant(restaurant);
        message.setApproved(approved);
        return message;
    }
    
    public static RoomMessage createVoteUpdateMessage(String roomCode, String restaurant, Map<String, Boolean> votes) {
        RoomMessage message = new RoomMessage();
        message.setType(RoomMessageType.VOTE);
        message.setRoomCode(roomCode);
        message.setSelectedRestaurant(restaurant);
        message.setVotes(votes);
        return message;
    }
    
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing RoomMessage to JSON", e);
            return "{}";
        }
    }
    
    public static RoomMessage fromJson(String json) {
        try {
            return objectMapper.readValue(json, RoomMessage.class);
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON to RoomMessage: {}", json, e);
            return null;
        }
    }
} 