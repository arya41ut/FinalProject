package com.example.finalproject.demo.controller;

import com.example.finalproject.demo.dto.ChatGptRequest;
import com.example.finalproject.demo.dto.ChatGptResponse;
import com.example.finalproject.demo.service.ChatGptService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatgpt")
@RequiredArgsConstructor
public class ChatGptController {

    private final ChatGptService chatGptService;
    private static final Logger logger = LoggerFactory.getLogger(ChatGptController.class);

    /**
     * Endpoint to get restaurant suggestions from the ChatGPT service
     * 
     * @param request The ChatGPT request containing prompt and type
     * @return ResponseEntity with ChatGptResponse containing a list of restaurant suggestions
     */
    @PostMapping("/suggest")
    public ResponseEntity<ChatGptResponse> getRestaurantSuggestions(@RequestBody ChatGptRequest request) {
        logger.info("Received request for restaurant suggestions with prompt: {}", request.getPrompt());
        
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return ResponseEntity.badRequest().body(
                new ChatGptResponse(null, "Prompt cannot be empty")
            );
        }
        
        ChatGptResponse response = chatGptService.getRestaurantSuggestions(request);
        
        if (response.getError() != null) {
            logger.error("Error generating suggestions: {}", response.getError());
            return ResponseEntity.internalServerError().body(response);
        }
        
        logger.info("Returning {} suggestions", response.getSuggestions().size());
        return ResponseEntity.ok(response);
    }
} 