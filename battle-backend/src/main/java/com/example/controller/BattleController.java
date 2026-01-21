package com.example.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.example.dto.AnswerMessage;
import com.example.dto.ErrorMessage;
import com.example.dto.JoinMessage;
import com.example.dto.MatchMessage;
import com.example.dto.QuestionMessage;
import com.example.dto.ScoreMessage;
import com.example.model.Question;
import com.example.model.Room;
import com.example.service.GameService;
import com.example.service.MatchmakingService;
import com.example.service.RoomService;


@Controller
public class BattleController {

    private final RoomService roomService;
    private final GameService gameService;
    private final MatchmakingService matchmakingService;
    private final SimpMessagingTemplate messaging;

    public BattleController(RoomService roomService,
                            GameService gameService,
                            MatchmakingService matchmakingService,
                            SimpMessagingTemplate messaging) {
        this.roomService = roomService;
        this.gameService = gameService;
        this.matchmakingService = matchmakingService;
        this.messaging = messaging;
    }

    /**
     * 玩家請求配對
     */
    @MessageMapping("/match")
    public void match(JoinMessage msg) { // 這裡借用 JoinMessage 拿 playerId 即可
        String myId = msg.getPlayerId();
        
        // 1. 嘗試從隊列中抓一個對手
        String opponentId = matchmakingService.tryMatch();

        if (opponentId != null && opponentId.equals(myId)) {
            matchmakingService.addToQueue(myId);
            return;
        }

        if (opponentId != null) {
            // ⭐ 配對成功！ (隊列裡有人)
            
            // A. 產生隨機房間 ID
            String newRoomId = java.util.UUID.randomUUID().toString().substring(0, 8);
            
            // B. 建立房間並把兩人都加入
            Room room = roomService.getOrCreate(newRoomId);
            roomService.join(room, opponentId); // 先加入對手 (P1)
            roomService.join(room, myId);       // 再加入自己 (P2)

            // ⭐ 防護 2：嚴格檢查房間是否真的滿了
            // 如果因為任何原因 P2 沒加入成功，絕對不能開始遊戲，否則會報錯
            if (room.getP1() == null || room.getP2() == null) {
                System.err.println("配對異常：房間人數不足，取消遊戲建立。");
                roomService.removeRoom(newRoomId); // 銷毀壞掉的房間
                return;
            }


            // C. 初始化遊戲
            gameService.initGame(room);

            // D. 通知雙方：配對成功，請前往 newRoomId
            MatchMessage successMsg = new MatchMessage(newRoomId, true, "配對成功！");
            
            // 通知對手 (opponent)
            messaging.convertAndSend("/topic/player/" + opponentId, successMsg);
            // 通知自己 (me)
            messaging.convertAndSend("/topic/player/" + myId, successMsg);

            // E. 延遲一點點發送第一題 (讓前端有時間訂閱房間頻道)
            new Thread(() -> {
                try { Thread.sleep(500); } catch (InterruptedException e) {}
                pushQuestion(room);
            }).start();

        } else {
            // ⭐ 隊列沒人，把自己加入隊列等待
            matchmakingService.addToQueue(myId);
            // 通知自己：正在尋找對手...
            messaging.convertAndSend(
                "/topic/player/" + myId, 
                new MatchMessage(null, false, "正在尋找對手...")
            );
        }
    }

    @MessageMapping("/join")
    public void join(JoinMessage msg) {
        Room room = roomService.getOrCreate(msg.getRoomId());
        
        // ⭐ 呼叫修改後的 join 方法，取得結果
        boolean success = roomService.join(room, msg.getPlayerId());

        if (success) {
            // 加入成功，檢查是否開始遊戲
            if (room.getP1() != null && room.getP2() != null) {
                gameService.initGame(room);
                pushQuestion(room);
            }
        } else {
            // ⭐ 加入失敗 (房間已滿)
            // 發送錯誤訊息到該房間頻道
            messaging.convertAndSend(
                "/topic/room/" + room.getRoomId(),
                new ErrorMessage(msg.getPlayerId(), "房間已滿，無法加入！")
            );
        }
    }

    @MessageMapping("/answer")
    public void answer(AnswerMessage msg) {
        Room room = roomService.getOrCreate(msg.getRoomId());

        gameService.submit(room, msg);
        broadcastScore(room);

        // ⭐ 兩人都答完，直接換題（不用等 8 秒）
        if (room.getP1().isAnswered() && room.getP2().isAnswered()) {
            advance(room);
        }
    }

    /* 推送題目 */
    private void pushQuestion(Room room) {
        gameService.startQuestion(room, () -> {
            // ⭐ 超時觸發
            advance(room);
        });

        messaging.convertAndSend(
            "/topic/room/" + room.getRoomId(),
            new QuestionMessage() {{
                setIndex(room.getCurrentIndex());
                setQuestion(mask(room.getQuestions().get(room.getCurrentIndex())));
                setP1Id(room.getP1().getId());
                setP2Id(room.getP2().getId());
            }}
        );
    }

    /* 換題 or 結束 */
    private synchronized void advance(Room room) {
        // 1. 取消計時器
        if (room.getTimeoutTask() != null) {
            room.getTimeoutTask().cancel(false);
        }

        // 2. 判斷是否還有下一題
        if (gameService.next(room)) {
            pushQuestion(room);
        } else {
            // ⭐ 遊戲結束邏輯
            messaging.convertAndSend(
                "/topic/room/" + room.getRoomId(),
                new ScoreMessage() {{
                    setP1Score(room.getP1().getScore());
                    setP2Score(room.getP2().getScore());
                    setP1Id(room.getP1().getId());
                    setP2Id(room.getP2().getId());
                    setGameOver(true); // 告訴前端結束了
                }}
            );

            // ⭐ 關鍵修正：發送完結束訊號後，銷毀房間
            // 這樣下次再用同一個 ID 加入時，就會建立全新的房間
            roomService.removeRoom(room.getRoomId());
        }
    }

    private void broadcastScore(Room room) {
        // 取得當前題目的正確答案
        String currentAns = room.getQuestions().get(room.getCurrentIndex()).getAnswer();

        messaging.convertAndSend(
            "/topic/room/" + room.getRoomId(),
            new ScoreMessage() {{
                setP1Score(room.getP1().getScore());
                setP2Score(room.getP2().getScore());
                // ⭐ 設定 ID，讓前端能分辨誰是誰
                setP1Id(room.getP1().getId());
                setP2Id(room.getP2().getId());
                // ⭐ 設定正確答案，讓前端做 UI 變色
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
