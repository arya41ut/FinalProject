package com.example.finalproject.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatGptRequest {
    private String prompt;
    private String type = "restaurant_suggestion";
} 