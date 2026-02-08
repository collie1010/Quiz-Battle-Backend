package com.example.config;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.example.dto.ScoreMessage;
import com.example.model.Player;
import com.example.model.Room;
import com.example.service.DisconnectService;
import com.example.service.MatchmakingService;
import com.example.service.RoomService;

@Component
public class WebSocketEventListener {

    private final RoomService roomService;
    private final SimpMessagingTemplate messaging;
    private final TaskScheduler taskScheduler;
    private final DisconnectService disconnectService;
    private final MatchmakingService matchmakingService;

    public WebSocketEventListener(RoomService roomService,
                                  SimpMessagingTemplate messaging,
                                  TaskScheduler taskScheduler,
                                  DisconnectService disconnectService,
                                  MatchmakingService matchmakingService) {
        this.roomService = roomService;
        this.messaging = messaging;
        this.taskScheduler = taskScheduler;
        this.disconnectService = disconnectService;
        this.matchmakingService = matchmakingService;
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        // System.out.println("偵測到斷線，Session ID: " + sessionId);

        matchmakingService.removePlayerBySessionId(sessionId);

        for (Room room : roomService.getAllRooms()) {
            Player disconnectedPlayer = null;
            Player winner = null;

            if (room.getP1() != null && sessionId.equals(room.getP1().getSessionId())) {
                disconnectedPlayer = room.getP1();
                winner = room.getP2();
            } else if (room.getP2() != null && sessionId.equals(room.getP2().getSessionId())) {
                disconnectedPlayer = room.getP2();
                winner = room.getP1();
            }

            if (disconnectedPlayer != null) {
                final String playerId = disconnectedPlayer.getId();
                final Room targetRoom = room;
                final Player targetWinner = winner;

                System.out.println("玩家 " + disconnectedPlayer.getName() + " 斷線，啟動 3 秒緩衝...");

                // ❌ 刪除這段代碼！不要取消遊戲計時器！
                // if (targetRoom.getTimeoutTask() != null) {
                //    targetRoom.getTimeoutTask().cancel(false);
                // }

                // 啟動斷線判輸的倒數任務 (3秒後如果沒回來才執行)
                ScheduledFuture<?> task = taskScheduler.schedule(() -> {
                    System.out.println("玩家 " + playerId + " 未在時間內重連，判輸。");

                    disconnectService.cancelTask(playerId);

                    // 這裡才需要取消遊戲計時器，因為房間都要被銷毀了
                    if (targetRoom.getTimeoutTask() != null) {
                        targetRoom.getTimeoutTask().cancel(false);
                    }

                    if (targetWinner != null) {
                        messaging.convertAndSend(
                            "/topic/room/" + targetRoom.getRoomId(),
                            new ScoreMessage() {{
                                setP1Score(targetRoom.getP1().getScore());
                                setP2Score(targetRoom.getP2().getScore());
                                setP1Id(targetRoom.getP1().getId());
                                setP2Id(targetRoom.getP2().getId());
                                setGameOver(true);
                                setMessage("對手斷線，你贏了！");
                                setWinnerId(targetWinner.getId());
                            }}
                        );
                    }
                    roomService.removeRoom(targetRoom.getRoomId());

                }, Instant.now().plusSeconds(3));

                disconnectService.addTask(playerId, task);
                break;
            }
        }
    }
}
