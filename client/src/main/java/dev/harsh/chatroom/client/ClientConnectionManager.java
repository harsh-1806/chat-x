package dev.harsh.chatroom.client;

import dev.harsh.chatroom.protocol.ChatMessage;
import dev.harsh.chatroom.protocol.MessageCodec;
import dev.harsh.chatroom.protocol.ProtocolConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the client's TCP connection to the server with retry and reconnection
 * logic.
 */
public final class ClientConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(ClientConnectionManager.class);

    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final long MAX_RETRY_DELAY_MS = 30_000;
    private static final int MAX_RETRIES = 5;

    private final String host;
    private final int port;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger retryCount = new AtomicInteger(0);

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    public ClientConnectionManager(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Connect to the server with retry logic.
     */
    public boolean connect() {
        while (retryCount.get() < MAX_RETRIES) {
            try {
                System.out.printf("Connecting to %s:%d...", host, port);
                socket = new Socket(host, port);
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                connected.set(true);
                retryCount.set(0);
                System.out.println(" connected!");
                return true;
            } catch (IOException e) {
                int attempt = retryCount.incrementAndGet();
                long delay = calculateRetryDelay(attempt);
                System.out.printf(" failed (%s). Retry %d/%d in %.1fs%n",
                        e.getMessage(), attempt, MAX_RETRIES, delay / 1000.0);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        System.out.println("Max retries reached. Could not connect to server.");
        return false;
    }

    /**
     * Send a message to the server.
     */
    public void send(ChatMessage message) throws IOException {
        if (!connected.get())
            throw new IOException("Not connected");
        MessageCodec.writeTo(message, outputStream);
    }

    /**
     * Read a message from the server. Blocks until available.
     */
    public ChatMessage receive() throws IOException {
        if (!connected.get())
            throw new IOException("Not connected");
        return MessageCodec.readFrom(inputStream);
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        connected.set(false);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public boolean isConnected() {
        return connected.get() && socket != null && !socket.isClosed();
    }

    /**
     * Exponential backoff with jitter.
     */
    private long calculateRetryDelay(int attempt) {
        long delay = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1));
        delay = Math.min(delay, MAX_RETRY_DELAY_MS);
        // Add jitter (±20%)
        double jitter = 0.8 + Math.random() * 0.4;
        return (long) (delay * jitter);
    }
}
