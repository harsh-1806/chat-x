package dev.harsh.chatroom.server.room;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all chat rooms — create, delete, list.
 */
public final class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<>();
    private final int maxRooms;

    public RoomManager(int maxRooms, String defaultRoom) {
        this.maxRooms = maxRooms;
        // Create the default room
        createRoom(defaultRoom);
        log.info("Default room '{}' created", defaultRoom);
    }

    /**
     * Create a new room. Returns the room, or null if max rooms reached or already
     * exists.
     */
    public ChatRoom createRoom(String name) {
        if (rooms.size() >= maxRooms && !rooms.containsKey(name)) {
            log.warn("Cannot create room '{}': max rooms ({}) reached", name, maxRooms);
            return null;
        }
        ChatRoom room = new ChatRoom(name);
        ChatRoom existing = rooms.putIfAbsent(name, room);
        if (existing != null) {
            return existing; // Room already existed
        }
        log.info("Room created: '{}'", name);
        return room;
    }

    /**
     * Get a room by name, creating it on-the-fly if it doesn't exist.
     */
    public ChatRoom getOrCreateRoom(String name) {
        ChatRoom room = rooms.get(name);
        if (room == null) {
            room = createRoom(name);
        }
        return room;
    }

    /**
     * Get a room by name.
     */
    public ChatRoom getRoom(String name) {
        return rooms.get(name);
    }

    /**
     * List all room names.
     */
    public List<String> listRoomNames() {
        return new ArrayList<>(rooms.keySet());
    }

    /**
     * All rooms.
     */
    public Collection<ChatRoom> getAllRooms() {
        return rooms.values();
    }

    /**
     * Number of active rooms.
     */
    public int getRoomCount() {
        return rooms.size();
    }

    /**
     * Delete a room if empty (never delete the default room).
     */
    public boolean deleteRoom(String name, String defaultRoom) {
        if (name.equals(defaultRoom)) {
            return false; // Cannot delete default room
        }
        ChatRoom room = rooms.get(name);
        if (room != null && room.getMemberCount() == 0) {
            rooms.remove(name);
            log.info("Empty room deleted: '{}'", name);
            return true;
        }
        return false;
    }
}
