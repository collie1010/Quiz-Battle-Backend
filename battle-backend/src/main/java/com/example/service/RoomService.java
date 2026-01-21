package com.example.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    public boolean join(Room room, String playerId) {
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
                room.setP1(p1);
                return true;
            } else if (room.getP2() == null) {
                Player p2 = new Player();
                p2.setId(playerId);
                room.setP2(p2);
                return true;
            }

            // 3. 兩個位置都滿了，拒絕加入
            return false;
        }
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
        System.out.println("房間 " + roomId + " 已銷毀，記憶體釋放。");
    }
}
