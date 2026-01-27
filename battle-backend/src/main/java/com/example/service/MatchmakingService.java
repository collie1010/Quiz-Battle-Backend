package com.example.service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Service;

@Service
public class MatchmakingService {

    public static class QueuedPlayer {
        public String id;
        public String name;
        public QueuedPlayer(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // 執行緒安全的佇列，用來存排隊的玩家 ID
    private final Queue<QueuedPlayer> waitingQueue = new ConcurrentLinkedQueue<>();

    /**
     * 加入排隊
     * @param playerId 玩家 ID
     */
    public void addToQueue(String playerId, String name) {
        boolean exists = waitingQueue.stream().anyMatch(p -> p.id.equals(playerId));
        if (!exists) {
            waitingQueue.add(new QueuedPlayer(playerId, name));
        }
    }

    /**
     * 嘗試配對
     * @return 如果配對成功，回傳對手的 ID；如果人數不足，回傳 null
     */
    public QueuedPlayer tryMatch() {
        // 只有當隊列裡還有人的時候，才能配對
        if (!waitingQueue.isEmpty()) {
            return waitingQueue.poll(); // 取出並移除隊列頭部的人
        }
        return null;
    }
    
    // 檢查隊列大小
    public int getQueueSize() {
        return waitingQueue.size();
    }
    
    // 移除玩家 (例如取消配對)
    public void removePlayer(String playerId) {
        waitingQueue.remove(playerId);
    }
}
