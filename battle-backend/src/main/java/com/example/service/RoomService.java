package com.example.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.model.Player;
import com.example.model.Room;

@Service
public class RoomService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room getOrCreate(String roomId) {
        return rooms.computeIfAbsent(roomId, id -> {
            Room r = new Room();
            r.setRoomId(id);
            return r;
        });
    }

    /**
     * 加入房間
     * @return true 表示加入成功 (或是已經在裡面), false 表示房間已滿
     */
    public boolean join(Room room, String playerId, String playerName, String sessionId) {
        // ⭐ 關鍵防護：鎖定這個 room 物件
        // 這樣即使兩個人同時進來，也必須排隊執行這段代碼
        synchronized (room) {
            // 1. 如果玩家已經在裡面 (重連的情況)，視為成功
            if (room.getP1() != null && room.getP1().getId().equals(playerId)) return true;
            if (room.getP2() != null && room.getP2().getId().equals(playerId)) return true;

            // 2. 嘗試加入空位
            if (room.getP1() == null) {
                Player p1 = new Player();
                p1.setId(playerId);
                p1.setName(playerName);
                p1.setSessionId(sessionId);
                room.setP1(p1);
                return true;
            } else if (room.getP2() == null) {
                Player p2 = new Player();
                p2.setId(playerId);
                p2.setName(playerName);
                p2.setSessionId(sessionId);
                room.setP2(p2);
                return true;
            }

            // 3. 兩個位置都滿了，拒絕加入
            return false;
        }
    }

    // ⭐ 定時任務：每 60 秒執行一次
    // initialDelay = 60000 (啟動後 1 分鐘開始)
    // fixedRate = 60000 (每隔 1 分鐘執行)
    @Scheduled(initialDelay = 60000, fixedRate = 60000)
    public void cleanUpZombieRooms() {
        long now = System.currentTimeMillis();
        long timeout = 10 * 60 * 1000; // 設定 10 分鐘超時 (可依需求調整)

        System.out.println("執行殭屍房間清理檢查...");

        Iterator<Map.Entry<String, Room>> iterator = rooms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Room> entry = iterator.next();
            Room room = entry.getValue();

            if (now - room.getLastActiveTime() > timeout) {
                System.out.println("發現殭屍房間 " + room.getRoomId() + "，強制銷毀！");
                
                // 這裡可以做一些額外清理，例如取消該房間內的 ScheduledFuture (如果有的話)
                if (room.getTimeoutTask() != null) {
                    room.getTimeoutTask().cancel(true);
                }
                
                iterator.remove(); // 從 Map 中移除
            }
        }
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
        System.out.println("房間 " + roomId + " 已銷毀，記憶體釋放。");
    }

    public Collection<Room> getAllRooms() {
        return Collections.unmodifiableCollection(rooms.values());
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }
}
