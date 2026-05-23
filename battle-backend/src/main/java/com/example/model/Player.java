package com.example.model;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Player {
    private String id;
    private String name;
    private String sessionId;
    private final AtomicInteger score = new AtomicInteger(0);
    private final AtomicBoolean answered = new AtomicBoolean(false);

    // Default constructor
    public Player() {}

    // Constructor with id
    public Player(String id) {
        this.id = id;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getScore() {
        return score.get();
    }

    public void setScore(int score) {
        this.score.set(score);
    }

    public AtomicInteger getScoreAtomic() {
        return score;
    }

    public boolean isAnswered() {
        return answered.get();
    }

    public void setAnswered(boolean answered) {
        this.answered.set(answered);
    }

    public AtomicBoolean getAnsweredAtomic() {
        return answered;
    }

    @Override
    public String toString() {
        return "Player{" +
                "id='" + id + '\'' +
                ", score=" + score.get() +
                ", answered=" + answered.get() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Player player = (Player) o;

        return id != null ? id.equals(player.id) : player.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}