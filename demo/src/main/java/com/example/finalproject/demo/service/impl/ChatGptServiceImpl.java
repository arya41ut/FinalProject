package com.example.finalproject.demo.service.impl;

import com.example.finalproject.demo.dto.ChatGptRequest;
import com.example.finalproject.demo.dto.ChatGptResponse;
import com.example.finalproject.demo.service.ChatGptService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatGptServiceImpl implements ChatGptService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatGptServiceImpl.class);
    private final Random random = new Random();
    private final RestTemplate restTemplate;
    
    @Value("${chatgpt.api.key}")
    private String apiKey;
    
    @Value("${chatgpt.api.url}")
    private String apiUrl;
    
    @Value("${chatgpt.api.model}")
    private String model;
    
    // Fallback mock data for restaurant suggestions
    private static final List<String> FALLBACK_RESTAURANTS = Arrays.asList(
        "McDonald's", "Burger King", "Wendy's", "Chipotle", 
        "Olive Garden", "Red Lobster", "Outback Steakhouse",
        "The Cheesecake Factory", "Applebee's", "Chili's", 
        "TGI Fridays", "P.F. Chang's", "California Pizza Kitchen",
        "Texas Roadhouse", "Ruth's Chris Steak House", "Maggiano's",
        "Panera Bread", "Panda Express", "KFC", "Taco Bell",
        "Subway", "Domino's Pizza", "Pizza Hut", "Papa John's",
        "Five Guys", "In-N-Out Burger", "Shake Shack", "Dairy Queen"
    );
    
    @Override
    public ChatGptResponse getRestaurantSuggestions(ChatGptRequest request) {
        try {
            logger.info("Generating restaurant suggestions for prompt: {}", request.getPrompt());
            
            // Create OpenAI API request headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            // Create the request body for OpenAI API
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            
            List<Map<String, String>> messages = new ArrayList<>();
            
            // System message to instruct the model
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a helpful assistant that provides restaurant suggestions. " +
                    "Provide exactly 5 restaurant suggestions based on the prompt. " +
                    "Return only the restaurant names, separated by newlines. Don't include numbering, explanations, or any other text.");
            
            // User message with the actual prompt
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", "Suggest restaurants for this request: " + request.getPrompt());
            
            messages.add(systemMessage);
            messages.add(userMessage);
            requestBody.put("messages", messages);
            
            // Set parameters to control response
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 100);
            
            // Make the API call to OpenAI
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);
            
            // Parse the OpenAI response
            Map responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, String> message = (Map<String, String>) choice.get("message");
                    String content = message.get("content");
                    
                    // Parse restaurant names from the response
                    List<String> suggestions = Arrays.stream(content.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                    
                    logger.info("Generated {} suggestions using OpenAI API", suggestions.size());
                    return new ChatGptResponse(suggestions, null);
                }
            }
            
            // If something went wrong with parsing the API response, use fallback
            logger.warn("Could not parse OpenAI API response, using fallback");
            return getFallbackSuggestions(request.getPrompt());
            
        } catch (Exception e) {
            logger.error("Error calling OpenAI API", e);
            logger.info("Using fallback method for restaurant suggestions");
            return getFallbackSuggestions(request.getPrompt());
        }
    }
    
    /**
     * Generates fallback restaurant suggestions if the OpenAI API call fails
     */
    private ChatGptResponse getFallbackSuggestions(String prompt) {
        try {
            // Get keywords from the prompt
            String[] keywords = prompt.toLowerCase().split("\\s+");
            
            // Simple logic to filter restaurants based on keywords in the prompt
            List<String> filteredRestaurants = FALLBACK_RESTAURANTS.stream()
                .filter(restaurant -> {
                    String lowerRestaurant = restaurant.toLowerCase();
                    for (String keyword : keywords) {
                        if (keyword.length() > 3 && lowerRestaurant.contains(keyword)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
            
            // If no matches, return random selections
            if (filteredRestaurants.isEmpty()) {
                List<String> shuffled = new java.util.ArrayList<>(FALLBACK_RESTAURANTS);
                Collections.shuffle(shuffled);
                filteredRestaurants = shuffled.subList(0, Math.min(6, shuffled.size()));
            }
            
            // Limit to at most 6 suggestions
            if (filteredRestaurants.size() > 6) {
                Collections.shuffle(filteredRestaurants);
                filteredRestaurants = filteredRestaurants.subList(0, 6);
            }
            
            logger.info("Generated {} fallback suggestions for prompt: {}", filteredRestaurants.size(), prompt);
            return new ChatGptResponse(filteredRestaurants, null);
            
        } catch (Exception e) {
            logger.error("Error generating fallback restaurant suggestions", e);
            return new ChatGptResponse(Collections.emptyList(), "Error generating suggestions: " + e.getMessage());
        }
    }
} 