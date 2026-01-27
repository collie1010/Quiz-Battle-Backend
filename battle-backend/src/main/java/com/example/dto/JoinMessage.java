package com.example.dto;

public class JoinMessage {
    private String roomId;
    private String playerId;
    private String playerName;

    // Default constructor
    public JoinMessage() {}

    // Constructor with all fields
    public JoinMessage(String roomId, String playerId, String playerName) {
        this.roomId = roomId;
        this.playerId = playerId;
        this.playerName = playerName;
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

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }


    @Override
    public String toString() {
        return "JoinMessage{" +
                "roomId='" + roomId + '\'' +
                ", playerId='" + playerId + '\'' +
                ", playerName='" + playerName + '\'' +
                '}';
    }
}
