package com.example.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.stereotype.Service;

@Service
public class DisconnectService {

    // 唯一的任務儲存庫
    private final Map<String, ScheduledFuture<?>> disconnectTasks = new ConcurrentHashMap<>();

    /**
     * 儲存斷線任務
     */
    public void addTask(String playerId, ScheduledFuture<?> task) {
        disconnectTasks.put(playerId, task);
    }

    /**
     * 取消並移除斷線任務 (用於重連)
     * @return true 表示成功取消，false 表示任務不存在或已執行
     */
    public boolean cancelTask(String playerId) {
        ScheduledFuture<?> task = disconnectTasks.remove(playerId);
        if (task != null) {
            // false 表示如果任務正在執行中，不要強制中斷 (通常這時候已經來不及了)
            // true 表示即使正在執行也要中斷
            // 這裡用 false 比較安全，避免狀態不一致
            task.cancel(false); 
            return true;
        }
        return false;
    }
}
