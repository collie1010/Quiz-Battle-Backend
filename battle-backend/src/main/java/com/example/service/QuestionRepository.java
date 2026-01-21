package com.example.service;


import com.example.model.Question;
import com.opencsv.bean.CsvToBeanBuilder;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.util.List;

@Service
public class QuestionRepository {

    private List<Question> allQuestions;

    @PostConstruct
    public void load() {
        allQuestions = new CsvToBeanBuilder<Question>(
                new InputStreamReader(
                        getClass().getClassLoader().getResourceAsStream("question.csv")
                ))
                .withType(Question.class)
                .withIgnoreLeadingWhiteSpace(true)
                .build()
                .parse();
    }

    public List<Question> getAll() {
        return allQuestions;
    }
}
