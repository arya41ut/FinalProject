package com.example.finalproject.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RestaurantVotingService {
    private static final Logger logger = LoggerFactory.getLogger(RestaurantVotingService.class);
    
    private final Map<String, Map<String, Map<String, Boolean>>> roomVotes = new ConcurrentHashMap<>();
    
    public Map<String, Boolean> recordVote(String roomCode, String restaurant, String username, boolean approved) {
        logger.info("Recording vote for room {}: user {} voted {} for {}", 
                    roomCode, username, approved ? "YES" : "NO", restaurant);
        
        Map<String, Map<String, Boolean>> restaurantVotes = roomVotes.computeIfAbsent(
            roomCode, k -> new ConcurrentHashMap<>()
        );
        
        Map<String, Boolean> votes = restaurantVotes.computeIfAbsent(
            restaurant, k -> new ConcurrentHashMap<>()
        );
        
        votes.put(username, approved);
        
        logger.info("Current votes for {}: {}", restaurant, votes);
        return new HashMap<>(votes);
    }
    
    public Map<String, Boolean> getVotes(String roomCode, String restaurant) {
        Map<String, Map<String, Boolean>> restaurantVotes = roomVotes.get(roomCode);
        if (restaurantVotes == null) {
            return new HashMap<>();
        }
        
        Map<String, Boolean> votes = restaurantVotes.get(restaurant);
        if (votes == null) {
            return new HashMap<>();
        }
        
        return new HashMap<>(votes);
    }
    
    public boolean allParticipantsVoted(String roomCode, String restaurant, int participantCount) {
        Map<String, Boolean> votes = getVotes(roomCode, restaurant);
        return votes.size() >= participantCount;
    }
    
    public boolean allVotesApproved(String roomCode, String restaurant) {
        Map<String, Boolean> votes = getVotes(roomCode, restaurant);
        if (votes.isEmpty()) {
            return false;
        }
        
        return votes.values().stream().allMatch(approved -> approved);
    }
    
    public void clearVotes(String roomCode) {
        roomVotes.remove(roomCode);
        logger.info("Cleared all votes for room {}", roomCode);
    }
    
    public void clearVotes(String roomCode, String restaurant) {
        Map<String, Map<String, Boolean>> restaurantVotes = roomVotes.get(roomCode);
        if (restaurantVotes != null) {
            restaurantVotes.remove(restaurant);
            logger.info("Cleared votes for {} in room {}", restaurant, roomCode);
        }
    }
}