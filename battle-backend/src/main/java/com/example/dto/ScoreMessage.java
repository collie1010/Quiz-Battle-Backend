package com.example.dto;

public class ScoreMessage {
    private int p1Score;
    private int p2Score;
    private String p1Id; // 新增：玩家1的ID
    private String p2Id; // 新增：玩家2的ID
    private String correctAnswer; // ⭐ 新增：告訴前端這題答案是什麼
    private boolean gameOver; // ⭐ 新增：遊戲結束旗標

    // Default constructor
    public ScoreMessage() {}

    // Constructor with all fields
    public ScoreMessage(int p1Score, int p2Score) {
        this.p1Score = p1Score;
        this.p2Score = p2Score;
    }

    // Getters and Setters
    public int getP1Score() {
        return p1Score;
    }

    public void setP1Score(int p1Score) {
        this.p1Score = p1Score;
    }

    public int getP2Score() {
        return p2Score;
    }

    public void setP2Score(int p2Score) {
        this.p2Score = p2Score;
    }

    public String getP1Id() {
        return p1Id;
    }

    public void setP1Id(String p1Id) {
        this.p1Id = p1Id;
    }

    public String getP2Id() {
        return p2Id;
    }

    public void setP2Id(String p2Id) {
        this.p2Id = p2Id;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    @Override
    public String toString() {
        return "ScoreMessage{" +
                "p1Score=" + p1Score +
                ", p2Score=" + p2Score +
                ", p1Id='" + p1Id + '\'' +
                ", p2Id='" + p2Id + '\'' +
                ", correctAnswer='" + correctAnswer + '\'' +
                ", gameOver=" + gameOver +
                '}';
    }
}
