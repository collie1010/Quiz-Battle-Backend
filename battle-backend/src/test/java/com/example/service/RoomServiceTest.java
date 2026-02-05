package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.model.Room;

class RoomServiceTest {

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService();
    }

    @Test
    void join_firstPlayer_shouldJoinAsP1() {
        // Given
        Room room = roomService.getOrCreate("test-room");

        // When
        boolean result = roomService.join(room, "player1", "Player 1", "session1");

        // Then
        assertThat(result).isTrue();
        assertThat(room.getP1()).isNotNull();
        assertThat(room.getP1().getId()).isEqualTo("player1");
        assertThat(room.getP2()).isNull();
    }

    @Test
    void join_secondPlayer_shouldJoinAsP2() {
        // Given
        Room room = roomService.getOrCreate("test-room");
        roomService.join(room, "player1", "Player 1", "session1");

        // When
        boolean result = roomService.join(room, "player2", "Player 2", "session2");

        // Then
        assertThat(result).isTrue();
        assertThat(room.getP1()).isNotNull();
        assertThat(room.getP2()).isNotNull();
        assertThat(room.getP2().getId()).isEqualTo("player2");
    }

    @Test
    void join_thirdPlayer_shouldBeRejected() {
        // Given
        Room room = roomService.getOrCreate("test-room");
        roomService.join(room, "player1", "Player 1", "session1");
        roomService.join(room, "player2", "Player 2", "session2");

        // When
        boolean result = roomService.join(room, "player3", "Player 3", "session3");

        // Then
        assertThat(result).isFalse();
        assertThat(room.getP1().getId()).isEqualTo("player1");
        assertThat(room.getP2().getId()).isEqualTo("player2");
    }

    @Test
    void join_existingPlayer_shouldReturnTrue() {
        // Given
        Room room = roomService.getOrCreate("test-room");
        roomService.join(room, "player1", "Player 1", "session1");

        // When - 同一玩家重新加入 (重連)
        boolean result = roomService.join(room, "player1", "Player 1", "session1");

        // Then
        assertThat(result).isTrue();
        assertThat(room.getP1()).isNotNull();
        assertThat(room.getP1().getId()).isEqualTo("player1");
    }

    @Test
    void getOrCreate_shouldReturnExistingRoom() {
        // Given
        Room room1 = roomService.getOrCreate("test-room");

        // When
        Room room2 = roomService.getOrCreate("test-room");

        // Then
        assertThat(room1).isSameAs(room2);
        assertThat(room1.getRoomId()).isEqualTo("test-room");
    }
}