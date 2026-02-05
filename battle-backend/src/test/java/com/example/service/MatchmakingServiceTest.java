package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.service.MatchmakingService.QueuedPlayer;

class MatchmakingServiceTest {

    private MatchmakingService matchmakingService;

    @BeforeEach
    void setUp() {
        matchmakingService = new MatchmakingService();
    }

    @Test
    void addToQueue_shouldAddNewPlayer() {
        // When
        matchmakingService.addToQueue("player1", "Player 1", "session1");

        // Then
        assertThat(matchmakingService.getQueueSize()).isEqualTo(1);
    }

    @Test
    void addToQueue_shouldPreventDuplicatePlayers() {
        // Given
        matchmakingService.addToQueue("player1", "Player 1", "session1");

        // When - 嘗試重複加入
        matchmakingService.addToQueue("player1", "Player 1", "session1-new");

        // Then
        assertThat(matchmakingService.getQueueSize()).isEqualTo(1);
    }

    @Test
    void tryMatch_shouldReturnPlayerWhenQueueNotEmpty() {
        // Given
        matchmakingService.addToQueue("player1", "Player 1", "session1");

        // When
        QueuedPlayer matched = matchmakingService.tryMatch();

        // Then
        assertThat(matched).isNotNull();
        assertThat(matched.id).isEqualTo("player1");
        assertThat(matched.name).isEqualTo("Player 1");
        assertThat(matchmakingService.getQueueSize()).isEqualTo(0);
    }

    @Test
    void tryMatch_shouldReturnNullWhenQueueEmpty() {
        // When
        QueuedPlayer matched = matchmakingService.tryMatch();

        // Then
        assertThat(matched).isNull();
        assertThat(matchmakingService.getQueueSize()).isEqualTo(0);
    }

    @Test
    void removePlayerBySessionId_shouldRemoveCorrectPlayer() {
        // Given
        matchmakingService.addToQueue("player1", "Player 1", "session1");
        matchmakingService.addToQueue("player2", "Player 2", "session2");

        // When
        matchmakingService.removePlayerBySessionId("session1");

        // Then
        assertThat(matchmakingService.getQueueSize()).isEqualTo(1);
        // 驗證剩下的玩家是player2
        QueuedPlayer remaining = matchmakingService.tryMatch();
        assertThat(remaining.id).isEqualTo("player2");
    }
}