package com.example.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

public class Room {
    private String roomId;
    private Player p1;
    private Player p2;
    private boolean p1Ready = false;
    private boolean p2Ready = false;
    private boolean gameStarted = false;
    private int currentIndex = 0;
    private long questionStartTime;
    private long lastActiveTime = System.currentTimeMillis();
    private List<Question> questions = new ArrayList<>();

    // ⭐ 新增：超時任務管理
    private ScheduledFuture<?> timeoutTask;

    // Default constructor
    public Room() {}

    // Constructor with roomId
    public Room(String roomId) {
        this.roomId = roomId;
    }

    // Getters and Setters
    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Player getP1() {
        return p1;
    }

    public void setP1(Player p1) {
        this.p1 = p1;
    }

    public Player getP2() {
        return p2;
    }

    public void setP2(Player p2) {
        this.p2 = p2;
    }

    public boolean isP1Ready() {
        return p1Ready;
    }

    public void setP1Ready(boolean p1Ready) {
        this.p1Ready = p1Ready;
    }
    
    public boolean isP2Ready() {
        return p2Ready;
    }
    
    public void setP2Ready(boolean p2Ready) {
        this.p2Ready = p2Ready;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public void setGameStarted(boolean gameStarted) {
        this.gameStarted = gameStarted;
    }

    public boolean isAllReady() {
        return p1Ready && p2Ready;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    public long getQuestionStartTime() {
        return questionStartTime;
    }

    public void setQuestionStartTime(long questionStartTime) {
        this.questionStartTime = questionStartTime;
    }

    public long getLastActiveTime() { 
        return lastActiveTime; 
    }

    public void updateActivity() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }

    public ScheduledFuture<?> getTimeoutTask() {
        return timeoutTask;
    }

    public void setTimeoutTask(ScheduledFuture<?> timeoutTask) {
        this.timeoutTask = timeoutTask;
    }

    @Override
    public String toString() {
        return "Room{" +
                "roomId='" + roomId + '\'' +
                ", p1=" + p1 +
                ", p2=" + p2 +
                ", currentIndex=" + currentIndex +
                ", questionStartTime=" + questionStartTime +
                ", questions=" + questions +
                ", timeoutTask=" + timeoutTask +
                '}';
    }
}
