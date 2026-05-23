package com.example.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

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
        // ⭐ 取得題庫快取，避免迴圈中重複呼叫 getAll()
        List<Question> allQuestions = repo.getAll();
        int totalQuestions = allQuestions.size();
        // 用 ThreadLocalRandom 生成 10 個不重複的隨機數字即可
        Set<Integer> randomIndices = new HashSet<>();
        while (randomIndices.size() < QUESTION_COUNT) {
            randomIndices.add(ThreadLocalRandom.current().nextInt(totalQuestions));
        }
        
        List<Question> selected = new ArrayList<>();
        for (int idx : randomIndices) {
            selected.add(allQuestions.get(idx));
        }
        room.setQuestions(selected);
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
        
        // Log 方便觀察 (可選)
        log.info("題目已推送，超時任務已排程於: {}", executionTime);
    }

    /* 玩家作答 — Lock-Free 版本，使用 CAS 保證原子性 */
    public boolean submit(Room room, AnswerMessage msg) {

        // 1. 查找玩家（純讀取，不需同步）
        Player player = null;
        if (room.getP1() != null && room.getP1().getId().equals(msg.getPlayerId())) {
            player = room.getP1();
        } else if (room.getP2() != null && room.getP2().getId().equals(msg.getPlayerId())) {
            player = room.getP2();
        }

        if (player == null) return false;
    
        // 2. 使用 CAS 原子性搶答：只有第一次呼叫能成功將 false→true
        if (!player.getAnsweredAtomic().compareAndSet(false, true)) {
            return false; // 已作答過，拒絕
        }

        // ⭐ 使用伺服器當下時間計算耗時 (解決前後端時間不同步問題)
        long serverNow = System.currentTimeMillis();
        long elapsed = serverNow - room.getQuestionStartTime();

        // Log 方便除錯
        log.info("玩家回答: {}", msg.getAnswer());
        log.info("正確答案: {}", room.getQuestions().get(room.getCurrentIndex()).getAnswer());
        log.info("耗時(ms): {}", elapsed);

        // 判定超時 (10000ms + 500ms 緩衝)
        if (elapsed > TIME_LIMIT_MS + 500) {
            log.warn("判定超時，不計分");
            return false;
        }

        // 確保題目列表存在且索引有效
        if (room.getQuestions() == null || 
            room.getCurrentIndex() >= room.getQuestions().size()) {
            return false;
        }

        Question q = room.getQuestions().get(room.getCurrentIndex());

        // 字串比對邏輯 (trim + ignoreCase)
        String dbAnswer = q.getAnswer() != null ? q.getAnswer().trim() : "";
        String playerAnswer = msg.getAnswer() != null ? msg.getAnswer().trim() : "";

        if (dbAnswer.equalsIgnoreCase(playerAnswer)) {
            // 分數計算
            int scoreGain = BASE_SCORE + (int)((TIME_LIMIT_MS - elapsed) / 100);
            scoreGain = Math.max(scoreGain, BASE_SCORE);
            
            // 使用 AtomicInteger.addAndGet() 原子加分（無鎖）
            int newScore = player.getScoreAtomic().addAndGet(scoreGain);
            log.info("答對！加分: {}，目前總分: {}", scoreGain, newScore);
        } else {
            log.info("答錯！");
        }
        return true;
        
    }

    /* 換題 */
    public boolean next(Room room) {
        room.setCurrentIndex(room.getCurrentIndex() + 1);
        return room.getCurrentIndex() < room.getQuestions().size();
    }
}