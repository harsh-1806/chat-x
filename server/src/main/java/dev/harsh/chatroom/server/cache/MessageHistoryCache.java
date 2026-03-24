package dev.harsh.chatroom.server.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.harsh.chatroom.protocol.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Per-room message history cache using Caffeine.
 * Stores the last N messages for each room, served on HISTORY requests.
 */
public final class MessageHistoryCache {

    private static final Logger log = LoggerFactory.getLogger(MessageHistoryCache.class);

    private final Cache<String, List<ChatMessage>> historyByRoom;
    private final int maxMessagesPerRoom;

    public MessageHistoryCache(int maxMessagesPerRoom, int ttlMinutes) {
        this.maxMessagesPerRoom = maxMessagesPerRoom;
        this.historyByRoom = Caffeine.newBuilder()
                .maximumSize(10_000) // max rooms tracked
                .expireAfterAccess(ttlMinutes, TimeUnit.MINUTES)
                .build();
        log.info("Message history cache initialized: maxPerRoom={}, ttl={}min", maxMessagesPerRoom, ttlMinutes);
    }

    /**
     * Record a message in the room's history.
     */
    public void addMessage(String room, ChatMessage message) {
        historyByRoom.asMap().compute(room, (key, existing) -> {
            List<ChatMessage> list = existing != null ? existing : new ArrayList<>();
            list.add(message);
            // Evict oldest if over capacity
            if (list.size() > maxMessagesPerRoom) {
                list.remove(0);
            }
            return list;
        });
    }

    /**
     * Retrieve the last N messages from a room.
     */
    public List<ChatMessage> getHistory(String room, int count) {
        List<ChatMessage> history = historyByRoom.getIfPresent(room);
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        synchronized (history) {
            int size = history.size();
            int from = Math.max(0, size - count);
            return List.copyOf(history.subList(from, size));
        }
    }

    /**
     * Clear history for a room.
     */
    public void clearRoom(String room) {
        historyByRoom.invalidate(room);
    }

    /**
     * Total cached messages across all rooms.
     */
    public long getTotalCachedMessages() {
        return historyByRoom.asMap().values().stream()
                .mapToLong(List::size)
                .sum();
    }
}
