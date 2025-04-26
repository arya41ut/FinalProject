package com.example.finalproject.demo.service;

import com.example.finalproject.demo.model.Room;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RestaurantSelectionService {
    private static final Logger logger = LoggerFactory.getLogger(RestaurantSelectionService.class);
    
    public enum SelectionStrategy {
        RANDOM,
        WEIGHTED_RANDOM,
        HIGHEST_VOTES,
        CONSENSUS,
        AI_RECOMMEND
    }

    private final RoomService roomService;
    private final ChatGptService chatGptService;

    public Map<String, String> selectRestaurant(Long roomId, SelectionStrategy strategy) {
        logger.info("Selecting restaurant for room {} using strategy: {}", roomId, strategy);
        
        Optional<Room> roomOpt = roomService.findById(roomId);
        if (roomOpt.isEmpty()) {
            logger.warn("Room not found: {}", roomId);
            return Collections.emptyMap();
        }
        
        Room room = roomOpt.get();
        List<String> suggestions = room.getRestaurantSuggestions();
        
        if (suggestions == null || suggestions.isEmpty()) {
            logger.warn("No restaurant suggestions found for room: {}", roomId);
            return Collections.emptyMap();
        }
        
        Map<String, String> result = new HashMap<>();
        
        switch (strategy) {
            case RANDOM:
                selectRandom(suggestions, result);
                break;
            case WEIGHTED_RANDOM:
                selectWeightedRandom(roomId, suggestions, result);
                break;
            case HIGHEST_VOTES:
                selectHighestVotes(roomId, result);
                break;
            case CONSENSUS:
                if (!selectConsensus(roomId, suggestions, result)) {
                    selectHighestVotes(roomId, result);
                }
                break;
            case AI_RECOMMEND:
                selectWithAi(roomId, suggestions, result);
                break;
            default:
                selectRandom(suggestions, result);
                break;
        }
        
        logger.info("Selected restaurant for room {}: {}", roomId, result.get("restaurant"));
        return result;
    }
    
    private void selectRandom(List<String> suggestions, Map<String, String> result) {
        int randomIndex = ThreadLocalRandom.current().nextInt(suggestions.size());
        String selectedRestaurant = suggestions.get(randomIndex);
        
        result.put("restaurant", selectedRestaurant);
        result.put("explanation", "Randomly selected from all suggestions");
    }
    
    private void selectWeightedRandom(Long roomId, List<String> suggestions, Map<String, String> result) {
        Map<String, Integer> votesMap = new HashMap<>();
        
        for (String restaurant : suggestions) {
            votesMap.put(restaurant, 1);
        }
        
        Optional<String> highestVoted = roomService.getBestRestaurant(roomId);
        if (highestVoted.isPresent()) {
            String restaurant = highestVoted.get();
            votesMap.put(restaurant, votesMap.getOrDefault(restaurant, 1) + 2);
        }
        
        int totalWeight = votesMap.values().stream().mapToInt(Integer::intValue).sum();
        
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        int cumulativeWeight = 0;
        String selectedRestaurant = suggestions.get(0);
        
        for (Map.Entry<String, Integer> entry : votesMap.entrySet()) {
            cumulativeWeight += entry.getValue();
            if (randomWeight <= cumulativeWeight) {
                selectedRestaurant = entry.getKey();
                break;
            }
        }
        
        result.put("restaurant", selectedRestaurant);
        result.put("explanation", "Selected with weighted randomization based on votes");
    }
    
    private void selectHighestVotes(Long roomId, Map<String, String> result) {
        Optional<String> highestVoted = roomService.getBestRestaurant(roomId);
        
        if (highestVoted.isPresent()) {
            String restaurant = highestVoted.get();
            result.put("restaurant", restaurant);
            result.put("explanation", "Selected based on highest number of votes");
        } else {
            List<String> suggestions = roomService.findById(roomId)
                .map(Room::getRestaurantSuggestions)
                .orElse(Collections.emptyList());
                
            if (!suggestions.isEmpty()) {
                selectRandom(suggestions, result);
            }
        }
    }
    
    private boolean selectConsensus(Long roomId, List<String> suggestions, Map<String, String> result) {
        Optional<String> highestVoted = roomService.getBestRestaurant(roomId);
        
        if (highestVoted.isPresent()) {
            String restaurant = highestVoted.get();
            result.put("restaurant", restaurant);
            result.put("explanation", "Selected based on group consensus");
            return true;
        }
        
        return false;
    }
    
    private void selectWithAi(Long roomId, List<String> suggestions, Map<String, String> result) {
        String prompt = String.format(
            "Based on these restaurant suggestions: %s, which one would you recommend and why?",
            String.join(", ", suggestions)
        );
        
        try {
            com.example.finalproject.demo.dto.ChatGptRequest request = 
                new com.example.finalproject.demo.dto.ChatGptRequest(prompt, "restaurant_selection");
            com.example.finalproject.demo.dto.ChatGptResponse response = chatGptService.getRestaurantSuggestions(request);
            
            if (response.getError() != null) {
                logger.error("Error getting AI recommendation: {}", response.getError());
                selectRandom(suggestions, result);
                return;
            }
            
            List<String> recommendations = response.getSuggestions();
            
            if (recommendations != null && !recommendations.isEmpty()) {
                String recommendation = recommendations.get(0);
                
                String matchedRestaurant = findBestMatch(recommendation, suggestions);
                
                if (matchedRestaurant != null) {
                    result.put("restaurant", matchedRestaurant);
                    
                    if (recommendations.size() > 1) {
                        result.put("explanation", "AI recommended: " + recommendations.get(1));
                    } else {
                        result.put("explanation", "AI recommended based on all suggestions");
                    }
                } else {
                    selectRandom(suggestions, result);
                }
            } else {
                selectRandom(suggestions, result);
            }
        } catch (Exception e) {
            logger.error("Error in AI restaurant selection", e);
            selectRandom(suggestions, result);
        }
    }
    
    private String findBestMatch(String input, List<String> suggestions) {
        for (String suggestion : suggestions) {
            if (input.toLowerCase().contains(suggestion.toLowerCase())) {
                return suggestion;
            }
        }
        
        return suggestions.isEmpty() ? null : suggestions.get(0);
    }
}