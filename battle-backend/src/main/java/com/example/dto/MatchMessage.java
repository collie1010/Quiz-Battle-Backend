package com.example.dto;

public class MatchMessage {
    private String roomId;
    private boolean success;
    private String message;

    public MatchMessage(String roomId, boolean success, String message) {
        this.roomId = roomId;
        this.success = success;
        this.message = message;
    }
    // Getters and Setters...
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) {this.roomId = roomId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) {this.success = success;}

    public String getMessage() { return message; }
    public void setMessage(String message) {this.message = message; }
}
