package com.example.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Controller;

import com.example.dto.AnswerMessage;
import com.example.dto.ErrorMessage;
import com.example.dto.JoinMessage;
import com.example.dto.MatchMessage;
import com.example.dto.QuestionMessage;
import com.example.dto.ScoreMessage;
import com.example.model.Question;
import com.example.model.Room;
import com.example.service.DisconnectService;
import com.example.service.GameService;
import com.example.service.MatchmakingService;
import com.example.service.RoomService;

@Controller
public class BattleController {

    private final RoomService roomService;
    private final GameService gameService;
    private final MatchmakingService matchmakingService;
    private final SimpMessagingTemplate messaging;
    private final DisconnectService disconnectService;

    public BattleController(RoomService roomService,
                            GameService gameService,
                            MatchmakingService matchmakingService,
                            SimpMessagingTemplate messaging,
                            TaskScheduler taskScheduler,
                            DisconnectService disconnectService) {
        this.roomService = roomService;
        this.gameService = gameService;
        this.matchmakingService = matchmakingService;
        this.messaging = messaging;
        this.disconnectService = disconnectService;
    }

    /**
     * 玩家請求配對
     */
    @MessageMapping("/match")
    public void match(JoinMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String myId = msg.getPlayerId();
        String myName = msg.getPlayerName();
        String mySessionId = headerAccessor.getSessionId();
        
        MatchmakingService.QueuedPlayer opponent = matchmakingService.tryMatch();

        if (opponent != null && opponent.id.equals(myId)) {
            matchmakingService.addToQueue(myId, myName, mySessionId);
            return;
        }

        if (opponent != null) {       
            String newRoomId = java.util.UUID.randomUUID().toString().substring(0, 8);
            Room room = roomService.getOrCreate(newRoomId);

            roomService.join(room, opponent.id, opponent.name, opponent.sessionId);
            roomService.join(room, myId, myName, mySessionId);

            if (room.getP1() == null || room.getP2() == null) {
                System.err.println("配對異常：房間人數不足，取消遊戲建立。");
                roomService.removeRoom(newRoomId);
                return;
            }

            gameService.initGame(room);

            MatchMessage successMsg = new MatchMessage(newRoomId, true, "配對成功！");
            messaging.convertAndSend("/topic/player/" + opponent.id, successMsg);
            messaging.convertAndSend("/topic/player/" + myId, successMsg);

        } else {
            matchmakingService.addToQueue(myId, myName, mySessionId);
            messaging.convertAndSend(
                "/topic/player/" + myId, 
                new MatchMessage(null, false, "正在尋找對手...")
            );
        }
    }

    @MessageMapping("/ready")
    public void ready(JoinMessage msg) {
        Room room = roomService.getRoom(msg.getRoomId());
        if (room == null) return;

        // ⭐ 必須鎖定房間，確保並發安全
        synchronized (room) {
            if (room.getP1() != null && room.getP1().getId().equals(msg.getPlayerId())) {
                room.setP1Ready(true);
            } else if (room.getP2() != null && room.getP2().getId().equals(msg.getPlayerId())) {
                room.setP2Ready(true);
            }

            // ⭐ 關鍵邏輯：
            // 1. 雙方都準備好了 (isAllReady)
            // 2. 遊戲還沒開始 (!isGameStarted)
            if (room.isAllReady() && !room.isGameStarted()) {
                System.out.println("雙方就緒，遊戲正式開始！");
                
                // 1. 鎖定狀態，防止第二次進入
                room.setGameStarted(true);
                
                // 2. 發送第一題
                startNewRound(room);
            }
        }
    }


    @MessageMapping("/join")
    public void join(JoinMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        Room room = roomService.getOrCreate(msg.getRoomId());
        String sessionId = headerAccessor.getSessionId();
        
        boolean success = roomService.join(room, msg.getPlayerId(), msg.getPlayerName(), sessionId);

        if (success) {
            if (room.getP1() != null && room.getP2() != null) {
                gameService.initGame(room);
                startNewRound(room); // ⭐ 修改：開始新回合
            }
        } else {
            messaging.convertAndSend(
                "/topic/room/" + room.getRoomId(),
                new ErrorMessage(msg.getPlayerId(), "房間已滿，無法加入！")
            );
        }
    }

    @MessageMapping("/rejoin")
    public void rejoin(JoinMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        Room room = roomService.getRoom(msg.getRoomId());

        if (room == null) {
            System.out.println("重連失敗：房間不存在 (" + msg.getRoomId() + ")");
            messaging.convertAndSend(
                "/topic/player/" + msg.getPlayerId(),
                new ErrorMessage(msg.getPlayerId(), "遊戲已失效，請重新配對")
            );
            return;
        }

        if (room.getQuestions() == null || room.getQuestions().isEmpty()) {
            System.out.println("重連失敗：房間資料異常");
            return;
        }

        String playerId = msg.getPlayerId();
        String newSessionId = headerAccessor.getSessionId();

        boolean found = false;
        if (room.getP1() != null && room.getP1().getId().equals(playerId)) {
            room.getP1().setSessionId(newSessionId);
            found = true;
        } else if (room.getP2() != null && room.getP2().getId().equals(playerId)) {
            room.getP2().setSessionId(newSessionId);
            found = true;
        }
        
        if (!found) {
             System.out.println("重連失敗：玩家不在房間內");
             return;
        }

        boolean canceled = disconnectService.cancelTask(playerId);
        if (canceled) {
            System.out.println("玩家 " + playerId + " 重連成功，取消銷毀任務。");
        }

        broadcastQuestion(room); // ⭐ 修改：只廣播，不重置時間
        broadcastCurrentScores(room);
    }


    @MessageMapping("/answer")
    public void answer(AnswerMessage msg) {
        Room room = roomService.getOrCreate(msg.getRoomId());

        gameService.submit(room, msg);
        broadcastScore(room);

        if (room.getP1().isAnswered() && room.getP2().isAnswered()) {
            advance(room);
        }
    }

    /**
     * 啟動新的一回合 (換題時呼叫)
     */
    private void startNewRound(Room room) {
        if (room.getQuestions() == null || room.getQuestions().isEmpty()) {
            System.err.println("錯誤：房間 " + room.getRoomId() + " 沒有題目，無法推送。");
            return;
        }

        if (room.getCurrentIndex() >= room.getQuestions().size()) {
            System.err.println("錯誤：房間 " + room.getRoomId() + " 索引越界 (" + room.getCurrentIndex() + ")");
            return;
        }
        
        // 啟動倒數計時、重置 startTime
        gameService.startQuestion(room, () -> {
            try {
                // 再次檢查房間是否存在 (避免已被斷線機制銷毀)
                if (roomService.getRoom(room.getRoomId()) == null) {
                    return;
                }
                advance(room);
            } catch (Exception e) {
                System.err.println("換題排程執行異常: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // 廣播題目給所有人
        broadcastQuestion(room);
    }

    /**
     * 廣播題目訊息 (換題 OR 重連時呼叫)
     */
    private void broadcastQuestion(Room room) {
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
                setRemainingTimeMs(remaining); // 傳送正確的剩餘時間
            }}
        );
    }

    /**
     * 只廣播分數，不洩漏答案 (專用於重連)
     */
    private void broadcastCurrentScores(Room room) {
        messaging.convertAndSend(
            "/topic/room/" + room.getRoomId(),
            new ScoreMessage() {{
                setP1Score(room.getP1().getScore());
                setP2Score(room.getP2().getScore());
                setP1Id(room.getP1().getId());
                setP2Id(room.getP2().getId());
                setCorrectAnswer(null); // ⭐ 關鍵：設為 null，避免前端顯示答案
            }}
        );
    }

    /* 換題 or 結束 */
    private synchronized void advance(Room room) {
        if (room.getTimeoutTask() != null) {
            room.getTimeoutTask().cancel(false);
        }

        if (gameService.next(room)) {
            startNewRound(room); // ⭐ 修改：開始新回合
        } else {
            // 遊戲結束，判定贏家
            String winnerId = null;
            if (room.getP1().getScore() > room.getP2().getScore()) {
                winnerId = room.getP1().getId();
            } else if (room.getP2().getScore() > room.getP1().getScore()) {
                winnerId = room.getP2().getId();
            }
            // 平手則 winnerId 為 null

            final String finalWinnerId = winnerId;

            messaging.convertAndSend(
                "/topic/room/" + room.getRoomId(),
                new ScoreMessage() {{
                    setP1Score(room.getP1().getScore());
                    setP2Score(room.getP2().getScore());
                    setP1Id(room.getP1().getId());
                    setP2Id(room.getP2().getId());
                    setGameOver(true);
                    setWinnerId(finalWinnerId); // 設定贏家
                }}
            );
            roomService.removeRoom(room.getRoomId());
        }
    }

    private void broadcastScore(Room room) {
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
