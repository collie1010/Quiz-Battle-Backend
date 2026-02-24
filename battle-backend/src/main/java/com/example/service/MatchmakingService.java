package com.example.service;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Service;

@Service
public class MatchmakingService {

    public static class QueuedPlayer {
        public String id;
        public String name;
        public String sessionId;
        public QueuedPlayer(String id, String name, String sessionId) {
            this.id = id;
            this.name = name;
            this.sessionId = sessionId;
        }
    }

    // 執行緒安全的佇列，用來存排隊的玩家 ID
    private final Queue<QueuedPlayer> waitingQueue = new ConcurrentLinkedQueue<>();

    private final Set<String> queuedPlayerIds = ConcurrentHashMap.newKeySet();

    /**
     * 加入排隊
     * @param playerId 玩家 ID
     */
    public void addToQueue(String playerId, String name, String sessionId) {
         // O(1) 檢查
        if (queuedPlayerIds.add(playerId)) { // add 回傳 true 表示原本不存在
            waitingQueue.add(new QueuedPlayer(playerId, name, sessionId));
        }
    }

    /**
     * 嘗試配對
     * @return 如果配對成功，回傳對手的 ID；如果人數不足，回傳 null
     */
    public QueuedPlayer tryMatch() {
        QueuedPlayer player = waitingQueue.poll();
        if (player != null) {
            queuedPlayerIds.remove(player.id); // 記得移除
        }
        return player;
    }
    
    // 檢查隊列大小
    public int getQueueSize() {
        return waitingQueue.size();
    }
    
    public void removePlayerBySessionId(String sessionId) {
        waitingQueue.removeIf(p -> p.sessionId.equals(sessionId));
    }
}
