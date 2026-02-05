package com.example.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.model.Question;

class QuestionRepositoryTest {

    private QuestionRepository questionRepository;

    @BeforeEach
    void setUp() {
        questionRepository = new QuestionRepository();
        // 注意：實際測試中需要mock CSV文件或使用test resources
        // 這裡先呼叫load()方法
        questionRepository.load();
    }

    @Test
    void load_shouldLoadQuestionsFromCsv() {
        // When
        List<Question> questions = questionRepository.getAll();

        // Then
        assertThat(questions).isNotNull();
        assertThat(questions.size()).isGreaterThan(0);
        // 驗證第一個題目結構
        Question firstQuestion = questions.get(0);
        assertThat(firstQuestion.getQuestion()).isNotNull();
        assertThat(firstQuestion.getAnswer()).isNotNull();
    }

    @Test
    void getAll_shouldReturnAllQuestions() {
        // Given
        List<Question> questions = questionRepository.getAll();

        // When - 多次呼叫
        List<Question> questions2 = questionRepository.getAll();

        // Then
        assertThat(questions).isSameAs(questions2); // 應該返回同一個實例
        assertThat(questions.size()).isEqualTo(questions2.size());
    }
}