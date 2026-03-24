package dev.harsh.chatroom.server.connection;

import dev.harsh.chatroom.protocol.ChatMessage;
import dev.harsh.chatroom.protocol.MessageCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a single client's TCP connection.
 * Manages per-client state: identity, rooms, I/O streams, and lifecycle.
 */
public final class ClientConnection {

    public enum State {
        CONNECTED, // TCP connected, not yet authenticated
        AUTHENTICATED, // Auth passed, ready to join rooms
        ACTIVE, // In at least one room
        DISCONNECTING, // Graceful disconnect in progress
        DISCONNECTED // Connection closed
    }

    private final String connectionId;
    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final long connectedAt;
    private final AtomicReference<State> state;
    private final Set<String> joinedRooms;
    private final AtomicLong lastActivityTime;
    private final AtomicLong messagesSent;
    private final AtomicLong messagesReceived;
    private final Object writeLock = new Object();

    private volatile String username;
    private volatile String sessionId;

    public ClientConnection(Socket socket) throws IOException {
        this.connectionId = UUID.randomUUID().toString().substring(0, 8);
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
        this.connectedAt = System.currentTimeMillis();
        this.state = new AtomicReference<>(State.CONNECTED);
        this.joinedRooms = ConcurrentHashMap.newKeySet();
        this.lastActivityTime = new AtomicLong(System.currentTimeMillis());
        this.messagesSent = new AtomicLong(0);
        this.messagesReceived = new AtomicLong(0);
    }

    // --- I/O Operations ---

    /**
     * Send a message to this client. Thread-safe.
     */
    public void send(ChatMessage message) throws IOException {
        if (state.get() == State.DISCONNECTED) {
            throw new IOException("Connection is closed");
        }
        synchronized (writeLock) {
            MessageCodec.writeTo(message, outputStream);
        }
        messagesSent.incrementAndGet();
        lastActivityTime.set(System.currentTimeMillis());
    }

    /**
     * Read a message from this client. Blocks until a message arrives.
     * Returns null if the connection is closed.
     */
    public ChatMessage receive() throws IOException {
        ChatMessage msg = MessageCodec.readFrom(inputStream);
        if (msg != null) {
            messagesReceived.incrementAndGet();
            lastActivityTime.set(System.currentTimeMillis());
        }
        return msg;
    }

    // --- State Management ---

    public boolean authenticate(String username, String sessionId) {
        this.username = username;
        this.sessionId = sessionId;
        return state.compareAndSet(State.CONNECTED, State.AUTHENTICATED);
    }

    public void joinRoom(String room) {
        joinedRooms.add(room);
        state.compareAndSet(State.AUTHENTICATED, State.ACTIVE);
    }

    public void leaveRoom(String room) {
        joinedRooms.remove(room);
        if (joinedRooms.isEmpty()) {
            state.compareAndSet(State.ACTIVE, State.AUTHENTICATED);
        }
    }

    public void disconnect() {
        state.set(State.DISCONNECTING);
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Best effort
        } finally {
            state.set(State.DISCONNECTED);
        }
    }

    // --- Getters ---

    public String getConnectionId() {
        return connectionId;
    }

    public String getUsername() {
        return username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public State getState() {
        return state.get();
    }

    public Set<String> getJoinedRooms() {
        return Set.copyOf(joinedRooms);
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public long getLastActivityTime() {
        return lastActivityTime.get();
    }

    public long getMessagesSent() {
        return messagesSent.get();
    }

    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress() != null
                ? socket.getRemoteSocketAddress().toString()
                : "unknown";
    }

    public boolean isConnected() {
        State s = state.get();
        return s != State.DISCONNECTED && s != State.DISCONNECTING && !socket.isClosed();
    }

    @Override
    public String toString() {
        return "ClientConnection{" +
                "id='" + connectionId + '\'' +
                ", user='" + username + '\'' +
                ", state=" + state.get() +
                ", rooms=" + joinedRooms +
                ", addr=" + getRemoteAddress() +
                '}';
    }
}
