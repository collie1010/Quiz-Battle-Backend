package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.dto.QuestionMessage;
import com.example.dto.ScoreMessage;
import com.example.model.Question;
import com.example.model.Room;

@Service
public class BroadcastService {

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);

    private final SimpMessagingTemplate messaging;
    private final GameService gameService;
    private final RoomService roomService;

    public BroadcastService(SimpMessagingTemplate messaging,
                            GameService gameService,
                            RoomService roomService) {
        this.messaging = messaging;
        this.gameService = gameService;
        this.roomService = roomService;
    }

    /**
     * 非同步推播分數（含正確答案）
     * 由 Broadcast- 執行緒池處理，不阻塞 WebSocket worker
     */
    @Async("broadcastExecutor")
    public void broadcastScoreAsync(Room room) {
        doBroadcastScore(room);
    }

    /**
     * 非同步處理雙方答完後的換題/結束邏輯
     * 先廣播分數 → 再換題廣播下一題 → 遊戲結束時發送結果
     */
    @Async("broadcastExecutor")
    public void advanceAndBroadcastAsync(Room room) {
        doAdvanceAndBroadcast(room);
    }

    /**
     * 廣播目前分數（不洩漏答案，專用於重連）
     */
    @Async("broadcastExecutor")
    public void broadcastCurrentScoresAsync(Room room) {
        messaging.convertAndSend(
            "/topic/room/" + room.getRoomId(),
            new ScoreMessage() {{
                setP1Score(room.getP1().getScore());
                setP2Score(room.getP2().getScore());
                setP1Id(room.getP1().getId());
                setP2Id(room.getP2().getId());
                setCorrectAnswer(null);
            }}
        );
    }

    // ========== 實際同步實作（給 @Async 包裹 / timeout 直接呼叫） ==========

    private void doBroadcastScore(Room room) {
        String currentAns = room.getQuestions().get(room.getCurrentIndex()).getAnswer();
        messaging.convertAndSend(
            "/topic/room/" + room.getRoomId(),
            new ScoreMessage() {{
                setP1Score(room.getP1().getScore());
                setP2Score(room.getP2().getScore());
                setP1Id(room.getP1().getId());
                setP2Id(room.getP2().getId());
                setCorrectAnswer(currentAns);
            }}
        );
    }

    /* package-private */ void doAdvanceAndBroadcast(Room room) {
        // 1. 取消 timeout 任務
        if (room.getTimeoutTask() != null) {
            room.getTimeoutTask().cancel(false);
        }

        // 2. 先廣播分數（含正確答案）
        doBroadcastScore(room);

        // 3. 換題或結束遊戲
        if (gameService.next(room)) {
            // 還有下一題 → 啟動新回合（含廣播新題目 + 排程 timeout）
            doStartNewRound(room);
        } else {
            // 遊戲結束，判定贏家
            String winnerId = null;
            if (room.getP1().getScore() > room.getP2().getScore()) {
                winnerId = room.getP1().getId();
            } else if (room.getP2().getScore() > room.getP1().getScore()) {
                winnerId = room.getP2().getId();
            }

            final String finalWinnerId = winnerId;

            messaging.convertAndSend(
                "/topic/room/" + room.getRoomId(),
                new ScoreMessage() {{
                    setP1Score(room.getP1().getScore());
                    setP2Score(room.getP2().getScore());
                    setP1Id(room.getP1().getId());
                    setP2Id(room.getP2().getId());
                    setGameOver(true);
                    setWinnerId(finalWinnerId);
                }}
            );
            roomService.removeRoom(room.getRoomId());
            log.info("遊戲結束 - Room: {}, Winner: {}", room.getRoomId(), finalWinnerId);
        }
    }

    private void doStartNewRound(Room room) {
        if (room.getQuestions() == null || room.getQuestions().isEmpty()) {
            log.error("錯誤：房間 {} 沒有題目，無法推送。", room.getRoomId());
            return;
        }

        if (room.getCurrentIndex() >= room.getQuestions().size()) {
            log.error("錯誤：房間 {} 索引越界 ({})", room.getRoomId(), room.getCurrentIndex());
            return;
        }

        // 啟動 timeout 排程（timeout 觸發時直接呼叫同步版 doAdvanceAndBroadcast）
        // 注意：timeout 本身就會在 TaskScheduler 的執行緒上執行，不需要 @Async
        gameService.startQuestion(room, () -> {
            try {
                if (roomService.getRoom(room.getRoomId()) == null) {
                    return;
                }
                doAdvanceAndBroadcast(room);
            } catch (Exception e) {
                log.error("換題排程執行異常: {}", e.getMessage(), e);
            }
        });

        // 廣播題目給所有人
        doBroadcastQuestion(room);
    }

    private void doBroadcastQuestion(Room room) {
        long now = System.currentTimeMillis();
        long elapsed = (room.getQuestionStartTime() > 0) ? (now - room.getQuestionStartTime()) : 0;
        long remaining = Math.max(0, 10000 - elapsed);

        messaging.convertAndSend(
            "/topic/room/" + room.getRoomId(),
            new QuestionMessage() {{
                setIndex(room.getCurrentIndex());
                setQuestion(mask(room.getQuestions().get(room.getCurrentIndex())));
                setP1Id(room.getP1().getId());
                setP2Id(room.getP2().getId());
                setP1Name(room.getP1().getName());
                setP2Name(room.getP2().getName());
                setRemainingTimeMs(remaining);
            }}
        );
    }

    private Question mask(Question q) {
        Question copy = new Question();
        copy.setId(q.getId());
        copy.setQuestion(q.getQuestion());
        copy.setA(q.getA());
        copy.setB(q.getB());
        copy.setC(q.getC());
        copy.setD(q.getD());
        copy.setAnswer(q.getAnswer());
        return copy;
    }
}