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

    /**
     * 配對結果封裝
     */
    public static class MatchResult {
        public final boolean matched;
        public final QueuedPlayer opponent;

        private MatchResult(boolean matched, QueuedPlayer opponent) {
            this.matched = matched;
            this.opponent = opponent;
        }

        public static MatchResult matched(QueuedPlayer p) {
            return new MatchResult(true, p);
        }

        public static MatchResult waiting() {
            return new MatchResult(false, null);
        }
    }

    // 執行緒安全的佇列，用來存排隊的玩家 ID
    private final Queue<QueuedPlayer> waitingQueue = new ConcurrentLinkedQueue<>();

    private final Set<String> queuedPlayerIds = ConcurrentHashMap.newKeySet();

    /**
     * 原子化配對：嘗試配對，若無合適對手則加入佇列等待
     * 
     * 使用 synchronized 確保 peek → 判斷 → poll/add 的原子性，
     * 避免原本 tryMatch() 與 addToQueue() 兩步驟間的競態窗口。
     */
    public MatchResult tryMatchOrQueue(String myId, String myName, String mySessionId) {
        synchronized (this) {
            QueuedPlayer opponent = waitingQueue.peek(); // 先看不取出

            if (opponent != null && !opponent.id.equals(myId)) {
                // 有合法對手，正式取出
                waitingQueue.poll();
                queuedPlayerIds.remove(opponent.id);
                return MatchResult.matched(opponent);
            }

            // 沒有對手 或 對手是自己，加入佇列等待
            if (queuedPlayerIds.add(myId)) {
                waitingQueue.add(new QueuedPlayer(myId, myName, mySessionId));
            }
            return MatchResult.waiting();
        }
    }

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