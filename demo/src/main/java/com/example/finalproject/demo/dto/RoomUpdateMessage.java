package com.example.finalproject.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomUpdateMessage {
    private String inviteCode;
    private Long roomId;
    private int participantCount;
    private List<String> restaurantSuggestions;
    private String message;
    private String updateType; // JOIN, LEAVE, NEW_SUGGESTION, etc.
    
    // Constructor for participant updates
    public RoomUpdateMessage(String inviteCode, Long roomId, int participantCount, String updateType) {
        this.inviteCode = inviteCode;
        this.roomId = roomId;
        this.participantCount = participantCount;
        this.updateType = updateType;
        this.message = updateType.equals("JOIN") ? 
                "A new participant joined the room" : 
                "A participant left the room";
    }
} 