package com.example.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.example.dto.AnswerMessage;
import com.example.model.Player;
import com.example.model.Question;
import com.example.model.Room;

@Service
public class GameService {

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

        // ⭐ 取消上一題的 timeout
        if (room.getTimeoutTask() != null) {
            room.getTimeoutTask().cancel(false);
        }

        // ⭐ 排程超時事件
        room.setTimeoutTask(
            scheduler.schedule(
                onTimeout,
                new Date(System.currentTimeMillis() + TIME_LIMIT_MS)
            )
        );
    }

    /* 玩家作答 */
    public boolean submit(Room room, AnswerMessage msg) {
        Player player = msg.getPlayerId().equals(room.getP1().getId()) ? room.getP1() : room.getP2();
        
        // 1. 檢查是否已作答
        if (player.isAnswered()) return false;
        player.setAnswered(true);

        // ⭐ 修正點 1：改用伺服器當下時間計算耗時 (解決前後端時間不同步問題)
        long serverNow = System.currentTimeMillis();
        long elapsed = serverNow - room.getQuestionStartTime();

        // ⭐ 修正點 2：加入 Log 方便除錯 (建議開發階段保留)
        System.out.println("玩家回答: " + msg.getAnswer());
        System.out.println("正確答案: " + room.getQuestions().get(room.getCurrentIndex()).getAnswer());
        System.out.println("耗時(ms): " + elapsed);

        // 判定超時 (8000ms + 緩衝)
        if (elapsed > TIME_LIMIT_MS + 500) {
            System.out.println("判定超時，不計分");
            return false;
        }

        Question q = room.getQuestions().get(room.getCurrentIndex());

        // 字串比對邏輯 (你之前改的 trim + ignoreCase)
        String dbAnswer = q.getAnswer() != null ? q.getAnswer().trim() : "";
        String playerAnswer = msg.getAnswer() != null ? msg.getAnswer().trim() : "";

        if (dbAnswer.equalsIgnoreCase(playerAnswer)) {
            // 分數計算
            int score = BASE_SCORE + (int)((TIME_LIMIT_MS - elapsed) / 100);
            score = Math.max(score, BASE_SCORE);
            
            player.setScore(player.getScore() + score);
            System.out.println("答對！加分: " + score + "，目前總分: " + player.getScore());
        } else {
            System.out.println("答錯！");
        }
        return true;
    }

    /* 換題 */
    public boolean next(Room room) {
        room.setCurrentIndex(room.getCurrentIndex() + 1);
        return room.getCurrentIndex() < room.getQuestions().size();
    }
}
