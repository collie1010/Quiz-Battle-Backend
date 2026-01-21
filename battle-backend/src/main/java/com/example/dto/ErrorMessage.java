package com.example.dto;

public class ErrorMessage {
    private String targetPlayerId; // 指定錯誤是要給誰的
    private String message;        // 錯誤內容

    public ErrorMessage(String targetPlayerId, String message) {
        this.targetPlayerId = targetPlayerId;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setTargetPlayerId(String targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }
}
