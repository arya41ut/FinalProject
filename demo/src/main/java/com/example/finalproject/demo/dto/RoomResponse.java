package com.example.finalproject.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {
    private String inviteCode;
    private String message;
    
    // Constructor with only inviteCode
    public RoomResponse(String inviteCode) {
        this.inviteCode = inviteCode;
        this.message = "Room created successfully!";
    }
} 