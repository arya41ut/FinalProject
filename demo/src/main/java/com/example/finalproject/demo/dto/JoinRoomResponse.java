package com.example.finalproject.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomResponse {
    private Long roomId;
    private String message;
    
    // Constructor with only roomId
    public JoinRoomResponse(Long roomId) {
        this.roomId = roomId;
        this.message = "Successfully joined the room!";
    }
} 