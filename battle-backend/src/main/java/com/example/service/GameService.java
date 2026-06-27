package com.example.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.example.dto.AnswerMessage;
import com.example.model.Player;
import com.example.model.Question;
import com.example.model.Room;

@Service
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    private static final int QUESTION_COUNT = 10;
    private static final int TIME_LIMIT_MS = 10000;
    private static final int BASE_SCORE = 100;

    private final QuestionRepository repo;
    private final TaskScheduler scheduler;

    public GameService(QuestionRepository repo, TaskScheduler scheduler) {
        this.repo = repo;
        this.scheduler = scheduler;
    }

    /* 初始化一場比賽 */
    public void initGame(Room room) {
        List<Question> pool = new ArrayList<>(repo.getAll());
        Collections.shuffle(pool);
        room.setQuestions(pool.subList(0, QUESTION_COUNT));
        room.setCurrentIndex(0);
    }

    /* 推送題目前呼叫 */
    public void startQuestion(Room room, Runnable onTimeout) {
        if (room.getP1() == null || room.getP2() == null) {
            return;
        }

        room.setQuestionStartTime(System.currentTimeMillis());
        room.getP1().setAnswered(false);
        room.getP2().setAnswered(false);

        if (room.getTimeoutTask() != null) {
            room.getTimeoutTask().cancel(false);
        }

        Instant executionTime = Instant.now().plusMillis(TIME_LIMIT_MS);

        room.setTimeoutTask(
            scheduler.schedule(onTimeout, executionTime)
        );
    }

    /* 玩家作答 (優化版：極小化鎖粒度 + 區域快取防競態) */
    public boolean submit(Room room, AnswerMessage msg) {

        // ⭐ 1. 快取當前狀態到 Thread Stack，避免被其他執行緒 (如 advance) 覆蓋
        int currentIndex = room.getCurrentIndex();
        long startTime = room.getQuestionStartTime();
        List<Question> questions = room.getQuestions();

        if (questions == null || questions.isEmpty() || currentIndex >= questions.size() || currentIndex < 0) {
            return false;
        }

        Player player = null;
        if (room.getP1() != null && room.getP1().getId().equals(msg.getPlayerId())) {
            player = room.getP1();
        } else if (room.getP2() != null && room.getP2().getId().equals(msg.getPlayerId())) {
            player = room.getP2();
        }

        if (player == null) return false;

        // ⭐ 2. 已經被外部 synchronized(room) 保護，直接操作即可
        if (player.isAnswered()) return false;
        player.setAnswered(true);

        long elapsed = System.currentTimeMillis() - startTime;
        Question q = questions.get(currentIndex); // 安全取用區域快取的 index

        if (elapsed > TIME_LIMIT_MS + 500) {
            return false;
        }

        String dbAnswer = q.getAnswer() != null ? q.getAnswer().trim() : "";
        String playerAnswer = msg.getAnswer() != null ? msg.getAnswer().trim() : "";

        if (dbAnswer.equalsIgnoreCase(playerAnswer)) {
            int score = BASE_SCORE + (int)((TIME_LIMIT_MS - elapsed) / 100);
            score = Math.max(score, BASE_SCORE);

            player.setScore(player.getScore() + score);
        }
        return true;
    }

    /* 換題 */
    public boolean next(Room room) {
        room.setCurrentIndex(room.getCurrentIndex() + 1);
        return room.getCurrentIndex() < room.getQuestions().size();
    }
}