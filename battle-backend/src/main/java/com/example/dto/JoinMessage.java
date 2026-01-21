package com.example.dto;

public class JoinMessage {
    private String roomId;
    private String playerId;

    // Default constructor
    public JoinMessage() {}

    // Constructor with all fields
    public JoinMessage(String roomId, String playerId) {
        this.roomId = roomId;
        this.playerId = playerId;
    }

    // Getters and Setters
    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    @Override
    public String toString() {
        return "JoinMessage{" +
                "roomId='" + roomId + '\'' +
                ", playerId='" + playerId + '\'' +
                '}';
    }
}
