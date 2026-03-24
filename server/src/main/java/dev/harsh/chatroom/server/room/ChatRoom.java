package dev.harsh.chatroom.server.room;

import dev.harsh.chatroom.protocol.ChatMessage;
import dev.harsh.chatroom.server.connection.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single chat room. Manages its members and broadcasts messages.
 */
public final class ChatRoom {

    private static final Logger log = LoggerFactory.getLogger(ChatRoom.class);

    private final String name;
    private final Set<ClientConnection> members = ConcurrentHashMap.newKeySet();
    private final long createdAt;

    public ChatRoom(String name) {
        this.name = name;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Add a member to this room.
     */
    public boolean addMember(ClientConnection connection) {
        boolean added = members.add(connection);
        if (added) {
            connection.joinRoom(name);
            log.info("User '{}' joined room '{}'", connection.getUsername(), name);
            // Notify others
            broadcast(ChatMessage.system(name, connection.getUsername() + " has joined the room"), connection);
        }
        return added;
    }

    /**
     * Remove a member from this room.
     */
    public boolean removeMember(ClientConnection connection) {
        boolean removed = members.remove(connection);
        if (removed) {
            connection.leaveRoom(name);
            log.info("User '{}' left room '{}'", connection.getUsername(), name);
            // Notify others
            broadcast(ChatMessage.system(name, connection.getUsername() + " has left the room"), null);
        }
        return removed;
    }

    /**
     * Broadcast a message to all members except the excluded connection.
     */
    public void broadcast(ChatMessage message, ClientConnection exclude) {
        for (ClientConnection member : members) {
            if (member == exclude || !member.isConnected())
                continue;
            try {
                member.send(message);
            } catch (IOException e) {
                log.warn("Failed to broadcast to {} in room '{}': {}",
                        member.getUsername(), name, e.getMessage());
            }
        }
    }

    /**
     * Send a message to ALL members including the sender (for echoing).
     */
    public void broadcastToAll(ChatMessage message) {
        broadcast(message, null);
    }

    public String getName() {
        return name;
    }

    public int getMemberCount() {
        return members.size();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Collection<ClientConnection> getMembers() {
        return Set.copyOf(members);
    }

    public boolean hasMember(ClientConnection connection) {
        return members.contains(connection);
    }

    /**
     * Remove a member without sending notifications (for disconnect cleanup).
     */
    public void removeMemberQuietly(ClientConnection connection) {
        members.remove(connection);
        connection.leaveRoom(name);
    }
}
