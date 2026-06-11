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
        // ⭐ 防禦性檢查：如果 P2 不見了，不要執行後續動作以免崩潰
        if (room.getP1() == null || room.getP2() == null) {
            return;
        }
    
        room.setQuestionStartTime(System.currentTimeMillis());
        room.getP1().setAnswered(false);
        room.getP2().setAnswered(false);
    
        // ⭐ 取消上一題的 timeout (如果有)
        if (room.getTimeoutTask() != null) {
            room.getTimeoutTask().cancel(false);
        }
    
        // ⭐ 修正後的排程邏輯：使用 TaskScheduler 原生方法 + Instant
        // 計算預計執行的時間點 (現在時間 + 限制時間)
        Instant executionTime = Instant.now().plusMillis(TIME_LIMIT_MS);
        
        // 使用 Spring TaskScheduler 進行排程
        room.setTimeoutTask(
            scheduler.schedule(onTimeout, executionTime)
        );
        
        logger.info("題目已推送，超時任務已排程於: {}", executionTime);
    }

    /* 玩家作答 */
    public boolean submit(Room room, AnswerMessage msg) {

        synchronized (room) {
            Player player = null;
            if (room.getP1() != null && room.getP1().getId().equals(msg.getPlayerId())) {
                player = room.getP1();
            } else if (room.getP2() != null && room.getP2().getId().equals(msg.getPlayerId())) {
                player = room.getP2();
            }

            if (player == null) return false;

            // 1. 檢查是否已作答
            if (player.isAnswered()) return false;
            player.setAnswered(true);

            // ⭐ 將極致防護移到最前面！確保後續所有的 Log 或邏輯都能安全取用題目
            if (room.getQuestions() == null || room.getQuestions().isEmpty() ||
                room.getCurrentIndex() >= room.getQuestions().size() || room.getCurrentIndex() < 0) {
                logger.warn("房間 {} 收到作答，但題目列表異常或索引越界", room.getRoomId());
                return false;
            }

            long serverNow = System.currentTimeMillis();
            long elapsed = serverNow - room.getQuestionStartTime();

            // 現在這裡取值絕對安全了
            Question q = room.getQuestions().get(room.getCurrentIndex());

            // 寫 Log 放在防護之後
            logger.debug("玩家回答: {}", msg.getAnswer());
            logger.debug("正確答案: {}", q.getAnswer());
            logger.debug("耗時(ms): {}", elapsed);

            // 判定超時 (8000ms + 緩衝)
            if (elapsed > TIME_LIMIT_MS + 500) {
                logger.warn("判定超時，不計分");
                return false;
            }

            // 字串比對邏輯
            String dbAnswer = q.getAnswer() != null ? q.getAnswer().trim() : "";
            String playerAnswer = msg.getAnswer() != null ? msg.getAnswer().trim() : "";

            if (dbAnswer.equalsIgnoreCase(playerAnswer)) {
                // 分數計算
                int score = BASE_SCORE + (int)((TIME_LIMIT_MS - elapsed) / 100);
                score = Math.max(score, BASE_SCORE);

                player.setScore(player.getScore() + score);
                logger.info("答對！加分: {}，目前總分: {}", score, player.getScore());
            } else {
                logger.info("答錯！");
            }
            return true;
        }
    }

    /* 換題 */
    public boolean next(Room room) {
        room.setCurrentIndex(room.getCurrentIndex() + 1);
        return room.getCurrentIndex() < room.getQuestions().size();
    }
}
