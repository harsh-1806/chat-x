package dev.harsh.chatroom.server.room;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for room management — create, list, delete, limits.
 */
class RoomManagerTest {

    private RoomManager roomManager;

    @BeforeEach
    void setUp() {
        roomManager = new RoomManager(5, "general");
    }

    @Test
    @DisplayName("Default room is created on init")
    void defaultRoomCreated() {
        assertNotNull(roomManager.getRoom("general"));
        assertEquals(1, roomManager.getRoomCount());
    }

    @Test
    @DisplayName("Create new room")
    void createNewRoom() {
        ChatRoom room = roomManager.createRoom("dev");

        assertNotNull(room);
        assertEquals("dev", room.getName());
        assertEquals(2, roomManager.getRoomCount());
    }

    @Test
    @DisplayName("Create duplicate room returns existing")
    void createDuplicateReturnsExisting() {
        ChatRoom first = roomManager.createRoom("dev");
        ChatRoom second = roomManager.createRoom("dev");

        assertSame(first, second);
        assertEquals(2, roomManager.getRoomCount());
    }

    @Test
    @DisplayName("Max rooms limit is enforced")
    void maxRoomsEnforced() {
        // general is already created (1/5)
        roomManager.createRoom("room2");
        roomManager.createRoom("room3");
        roomManager.createRoom("room4");
        roomManager.createRoom("room5");

        // 6th room should fail
        ChatRoom overflow = roomManager.createRoom("room6");
        assertNull(overflow);
        assertEquals(5, roomManager.getRoomCount());
    }

    @Test
    @DisplayName("getOrCreateRoom creates on miss")
    void getOrCreateRoom() {
        assertNull(roomManager.getRoom("newroom"));

        ChatRoom room = roomManager.getOrCreateRoom("newroom");
        assertNotNull(room);
        assertEquals("newroom", room.getName());
    }

    @Test
    @DisplayName("listRoomNames returns all room names")
    void listRoomNames() {
        roomManager.createRoom("dev");
        roomManager.createRoom("random");

        List<String> names = roomManager.listRoomNames();
        assertTrue(names.contains("general"));
        assertTrue(names.contains("dev"));
        assertTrue(names.contains("random"));
        assertEquals(3, names.size());
    }

    @Test
    @DisplayName("Cannot delete default room")
    void cannotDeleteDefaultRoom() {
        assertFalse(roomManager.deleteRoom("general", "general"));
        assertEquals(1, roomManager.getRoomCount());
    }

    @Test
    @DisplayName("Delete empty non-default room succeeds")
    void deleteEmptyRoom() {
        roomManager.createRoom("temp");
        assertEquals(2, roomManager.getRoomCount());

        assertTrue(roomManager.deleteRoom("temp", "general"));
        assertEquals(1, roomManager.getRoomCount());
    }
}
