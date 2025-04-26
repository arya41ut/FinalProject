package com.example.finalproject.demo.service;

import com.example.finalproject.demo.dto.ChatGptRequest;
import com.example.finalproject.demo.dto.ChatGptResponse;

public interface ChatGptService {
    
    /**
     * Get restaurant suggestions based on the provided prompt
     * 
     * @param request The ChatGPT request containing prompt and type
     * @return ChatGptResponse containing a list of restaurant suggestions or an error
     */
    ChatGptResponse getRestaurantSuggestions(ChatGptRequest request);
} 