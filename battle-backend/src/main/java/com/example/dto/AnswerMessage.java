package com.example.dto;

public class AnswerMessage {
    private String roomId;
    private String playerId;
    private String answer;
    private long answerTime;

    // Default constructor
    public AnswerMessage() {}

    // Constructor with all fields
    public AnswerMessage(String roomId, String playerId, String answer, long answerTime) {
        this.roomId = roomId;
        this.playerId = playerId;
        this.answer = answer;
        this.answerTime = answerTime;
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

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public long getAnswerTime() {
        return answerTime;
    }

    public void setAnswerTime(long answerTime) {
        this.answerTime = answerTime;
    }

    @Override
    public String toString() {
        return "AnswerMessage{" +
                "roomId='" + roomId + '\'' +
                ", playerId='" + playerId + '\'' +
                ", answer='" + answer + '\'' +
                ", answerTime=" + answerTime +
                '}';
    }
}
