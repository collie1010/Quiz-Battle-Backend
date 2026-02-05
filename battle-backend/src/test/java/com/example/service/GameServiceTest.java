package com.example.service;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import com.example.dto.AnswerMessage;
import com.example.model.Player;
import com.example.model.Question;
import com.example.model.Room;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private QuestionRepository questionRepo;

    @Mock
    private TaskScheduler taskScheduler;

    private GameService gameService;
    private Room room;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        gameService = new GameService(questionRepo, taskScheduler);

        // 設置測試數據
        room = new Room("test-room");
        player1 = new Player();
        player1.setId("player1");
        player1.setName("Player 1");
        player1.setScore(0);

        player2 = new Player();
        player2.setId("player2");
        player2.setName("Player 2");
        player2.setScore(0);

        room.setP1(player1);
        room.setP2(player2);
    }

    @Test
    void initGame_shouldInitializeWithRandomQuestions() {
        // Given - 提供至少10個問題
        List<Question> mockQuestions = Arrays.asList(
            new Question("1", "Question 1", "A1", "B1", "C1", "D1", "A"),
            new Question("2", "Question 2", "A2", "B2", "C2", "D2", "B"),
            new Question("3", "Question 3", "A3", "B3", "C3", "D3", "C"),
            new Question("4", "Question 4", "A4", "B4", "C4", "D4", "D"),
            new Question("5", "Question 5", "A5", "B5", "C5", "D5", "A"),
            new Question("6", "Question 6", "A6", "B6", "C6", "D6", "B"),
            new Question("7", "Question 7", "A7", "B7", "C7", "D7", "C"),
            new Question("8", "Question 8", "A8", "B8", "C8", "D8", "D"),
            new Question("9", "Question 9", "A9", "B9", "C9", "D9", "A"),
            new Question("10", "Question 10", "A10", "B10", "C10", "D10", "B")
        );
        when(questionRepo.getAll()).thenReturn(mockQuestions);

        // When
        gameService.initGame(room);

        // Then
        assertThat(room.getQuestions()).hasSize(10); // QUESTION_COUNT = 10
        assertThat(room.getCurrentIndex()).isEqualTo(0);
        verify(questionRepo).getAll();
    }

    @Test
    void submit_correctAnswer_shouldAddScore() {
        // Given
        Question question = new Question("1", "What is 2+2?", "2", "3", "4", "5", "C"); // 答案是C(4)
        room.setQuestions(Arrays.asList(question));
        room.setCurrentIndex(0);
        room.setQuestionStartTime(System.currentTimeMillis() - 1000); // 1秒前開始

        AnswerMessage answerMsg = new AnswerMessage();
        answerMsg.setPlayerId("player1");
        answerMsg.setAnswer("C");

        // When
        boolean result = gameService.submit(room, answerMsg);

        // Then
        assertThat(result).isTrue();
        assertThat(player1.isAnswered()).isTrue();
        assertThat(player1.getScore()).isGreaterThan(100); // BASE_SCORE + time bonus
    }

    @Test
    void submit_wrongAnswer_shouldNotAddScore() {
        // Given
        Question question = new Question("1", "What is 2+2?", "2", "3", "4", "5", "C"); // 答案是C(4)
        room.setQuestions(Arrays.asList(question));
        room.setCurrentIndex(0);
        room.setQuestionStartTime(System.currentTimeMillis() - 1000);

        AnswerMessage answerMsg = new AnswerMessage();
        answerMsg.setPlayerId("player1");
        answerMsg.setAnswer("A");

        // When
        boolean result = gameService.submit(room, answerMsg);

        // Then
        assertThat(result).isTrue();
        assertThat(player1.isAnswered()).isTrue();
        assertThat(player1.getScore()).isEqualTo(0);
    }

    @Test
    void submit_timeout_shouldNotAddScore() {
        // Given
        Question question = new Question("1", "What is 2+2?", "2", "3", "4", "5", "C"); // 答案是C(4)
        room.setQuestions(Arrays.asList(question));
        room.setCurrentIndex(0);
        room.setQuestionStartTime(System.currentTimeMillis() - 11000); // 11秒前開始，超過10.5秒限制

        AnswerMessage answerMsg = new AnswerMessage();
        answerMsg.setPlayerId("player1");
        answerMsg.setAnswer("C");

        // When
        boolean result = gameService.submit(room, answerMsg);

        // Then
        assertThat(result).isFalse(); // 超時時返回false，不接受答案
        assertThat(player1.isAnswered()).isTrue(); // 但玩家仍被標記為已回答
        assertThat(player1.getScore()).isEqualTo(0); // 不加分
    }

    @Test
    void submit_duplicateAnswer_shouldBeRejected() {
        // Given
        Question question = new Question("1", "What is 2+2?", "2", "3", "4", "5", "C"); // 答案是C(4)
        room.setQuestions(Arrays.asList(question));
        room.setCurrentIndex(0);
        room.setQuestionStartTime(System.currentTimeMillis() - 1000);

        AnswerMessage answerMsg = new AnswerMessage();
        answerMsg.setPlayerId("player1");
        answerMsg.setAnswer("C");

        // 第一次作答
        gameService.submit(room, answerMsg);

        // 重置答案嘗試第二次
        answerMsg.setAnswer("A");

        // When - 第二次作答
        boolean result = gameService.submit(room, answerMsg);

        // Then
        assertThat(result).isFalse(); // 重複作答被拒絕
        assertThat(player1.getScore()).isGreaterThan(100); // 分數保持第一次的
    }

    @Test
    void next_shouldIncrementIndexAndReturnTrueWhenMoreQuestions() {
        // Given
        room.setCurrentIndex(0);
        room.setQuestions(Arrays.asList(
            new Question("1", "Question 1", "A1", "B1", "C1", "D1", "A"),
            new Question("2", "Question 2", "A2", "B2", "C2", "D2", "B")
        ));

        // When
        boolean result = gameService.next(room);

        // Then
        assertThat(result).isTrue();
        assertThat(room.getCurrentIndex()).isEqualTo(1);
    }

    @Test
    void next_shouldReturnFalseWhenNoMoreQuestions() {
        // Given
        room.setCurrentIndex(1);
        room.setQuestions(Arrays.asList(
            new Question("1", "Question 1", "A1", "B1", "C1", "D1", "A"),
            new Question("2", "Question 2", "A2", "B2", "C2", "D2", "B")
        ));

        // When
        boolean result = gameService.next(room);

        // Then
        assertThat(result).isFalse();
        assertThat(room.getCurrentIndex()).isEqualTo(2);
    }
}