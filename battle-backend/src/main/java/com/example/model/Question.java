package com.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencsv.bean.CsvBindByName;

public class Question {

    @CsvBindByName(column = "題目ID")
    @JsonProperty("id")
    private String id;

    @CsvBindByName(column = "題目")
    @JsonProperty("question")
    private String question;

    @CsvBindByName(column = "選項1")
    @JsonProperty("A") // ⭐ 強制轉為大寫 "A"
    private String A;

    @CsvBindByName(column = "選項2")
    @JsonProperty("B") // ⭐ 強制轉為大寫 "B"
    private String B;

    @CsvBindByName(column = "選項3")
    @JsonProperty("C") // ⭐ 強制轉為大寫 "C"
    private String C;

    @CsvBindByName(column = "選項4")
    @JsonProperty("D") // ⭐ 強制轉為大寫 "D"
    private String D;

    @CsvBindByName(column = "答案")
    @JsonProperty("answer")
    private String answer;

    // Default constructor
    public Question() {}

    // Constructor with all fields
    public Question(String id, String question, String a, String b, String c, String d, String answer) {
        this.id = id;
        this.question = question;
        A = a;
        B = b;
        C = c;
        D = d;
        this.answer = answer;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getA() {
        return A;
    }

    public void setA(String a) {
        A = a;
    }

    public String getB() {
        return B;
    }

    public void setB(String b) {
        B = b;
    }

    public String getC() {
        return C;
    }

    public void setC(String c) {
        C = c;
    }

    public String getD() {
        return D;
    }

    public void setD(String d) {
        D = d;
    }

    public String getAnswer() {
        if (this.answer == null) return "";
        
        String raw = this.answer.trim();
        
        // 如果是數字，轉成對應的字母
        switch (raw) {
            case "1": return "A";
            case "2": return "B";
            case "3": return "C";
            case "4": return "D";
            // 如果原本就是 A, B, C, D 則直接回傳
            default: return raw.toUpperCase(); 
        }
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    @Override
    public String toString() {
        return "Question{" +
                "id='" + id + '\'' +
                ", question='" + question + '\'' +
                ", A='" + A + '\'' +
                ", B='" + B + '\'' +
                ", C='" + C + '\'' +
                ", D='" + D + '\'' +
                ", answer='" + answer + '\'' +
                '}';
    }
}
