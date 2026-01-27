package com.example.dto;

import com.example.model.Question;

public class QuestionMessage {
    private int index;
    private Question question;
    private String p1Id;
    private String p2Id;
    private String p1Name;
    private String p2Name;

    // Default constructor
    public QuestionMessage() {}

    // Constructor with all fields
    public QuestionMessage(int index, Question question) {
        this.index = index;
        this.question = question;
    }

    // Getters and Setters
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }
    
    public String getP1Id() { return p1Id; }
    public void setP1Id(String p1Id) { this.p1Id = p1Id; }

    public String getP2Id() { return p2Id; }
    public void setP2Id(String p2Id) { this.p2Id = p2Id; }

    public String getP1Name() { return p1Name; }
    public void setP1Name(String p1Name) { this.p1Name = p1Name; }

    public String getP2Name() { return p2Name; }
    public void setP2Name(String p2Name) { this.p2Name = p2Name; }

    @Override
    public String toString() {
        return "QuestionMessage{" +
                "index=" + index +
                ", question=" + question +
                '}';
    }
}
