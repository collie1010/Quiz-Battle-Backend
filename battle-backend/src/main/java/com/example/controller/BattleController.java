package com.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.example.model.Question;
import com.example.model.Room;
import com.example.service.BroadcastService;
import com.example.service.DisconnectService;
import com.example.service.GameService;
import com.example.service.MatchmakingService;
import com.example.service.RoomService;

@Controller
public class BattleController {

    private static final Logger log = LoggerFactory.getLogger(BattleController.class);

    private final RoomService roomService;
    private final GameService gameService;
    private final MatchmakingService matchmakingService;
    private final SimpMessagingTemplate messaging;
    private final DisconnectService disconnectService;
    private final BroadcastService broadcastService;

    public BattleController(RoomService roomService,
                            GameService gameService,
                            MatchmakingService matchmakingService,
                            SimpMessagingTemplate messaging,
                            TaskScheduler taskScheduler,
                            DisconnectService disconnectService,
                            BroadcastService broadcastService) {
        this.roomService = roomService;
        this.gameService = gameService;
        this.matchmakingService = matchmakingService;
        this.messaging = messaging;
        this.disconnectService = disconnectService;
        this.broadcastService = broadcastService;
    }

    /**
     * 玩家請求配對
     */
    @MessageMapping("/match")
    public void match(JoinMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String myId = msg.getPlayerId();
        String myName = msg.getPlayerName();
        String mySessionId = headerAccessor.getSessionId();

        log.info("收到配對請求 - PlayerName: {}, PlayerID: {}, SessionID: {}", myName, myId, mySessionId);
        
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
                log.error("配對異常：房間人數不足，取消遊戲建立。");
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
        else room.updateActivity();

        synchronized (room) {
            if (room.getP1() != null && room.getP1().getId().equals(msg.getPlayerId())) {
                room.setP1Ready(true);
            } else if (room.getP2() != null && room.getP2().getId().equals(msg.getPlayerId())) {
                room.setP2Ready(true);
            }

            if (room.isAllReady() && !room.isGameStarted()) {
                log.info("雙方就緒，遊戲正式開始！");
                room.setGameStarted(true);
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
                startNewRound(room);
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
            log.info("重連失敗：房間不存在 ({})", msg.getRoomId());
            messaging.convertAndSend(
                "/topic/player/" + msg.getPlayerId(),
                new ErrorMessage(msg.getPlayerId(), "遊戲已失效，請重新配對")
            );
            return;
        }

        if (room.getQuestions() == null || room.getQuestions().isEmpty()) {
            log.info("重連失敗：房間資料異常");
            return;
        }

        room.updateActivity();

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
            log.info("重連失敗：玩家不在房間內");
            return;
        }

        boolean canceled = disconnectService.cancelTask(playerId);
        if (canceled) {
            log.info("玩家 {} 重連成功，取消銷毀任務。", playerId);
        }

        // 使用非同步廣播，不阻塞 WebSocket worker
        broadcastQuestion(room);
        broadcastService.broadcastCurrentScoresAsync(room);
    }


    @MessageMapping("/answer")
    public void answer(AnswerMessage msg) {
        Room room = roomService.getOrCreate(msg.getRoomId());
        if (room != null) room.updateActivity();

        // 1. CAS 無鎖算分（如果已經答過，回傳 false，此處可用 boolean 接住）
        boolean accepted = gameService.submit(room, msg);
        if (!accepted) return; // 已經答過或超時，直接丟棄

        // 2. 判斷推進邏輯
        if (room.getP1().isAnswered() && room.getP2().isAnswered()) {
            // 雙方皆完成：整包丟給背景執行緒推進
            broadcastService.advanceAndBroadcastAsync(room);
        } else {
            // 只有單方完成時，僅廣播當下分數進度 (不換題)
            broadcastService.broadcastScoreAsync(room);
        }
    }


    /**
     * 啟動新的一回合 (換題時呼叫)
     * 僅在遊戲起始流程（ready、join）同步呼叫，非高頻路徑
     */
    private void startNewRound(Room room) {
        if (room.getQuestions() == null || room.getQuestions().isEmpty()) {
            log.error("錯誤：房間 {} 沒有題目，無法推送。", room.getRoomId());
            return;
        }

        if (room.getCurrentIndex() >= room.getQuestions().size()) {
            log.error("錯誤：房間 {} 索引越界 ({})", room.getRoomId(), room.getCurrentIndex());
            return;
        }
        
        // 啟動倒數計時、重置 startTime
        gameService.startQuestion(room, () -> {
            try {
                if (roomService.getRoom(room.getRoomId()) == null) {
                    return;
                }
                // timeout 觸發 → 呼叫 BroadcastService 的非同步 advance
                broadcastService.advanceAndBroadcastAsync(room);
            } catch (Exception e) {
                log.error("換題排程執行異常: {}", e.getMessage(), e);
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