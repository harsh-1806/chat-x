package dev.harsh.chatroom.server;

import dev.harsh.chatroom.server.auth.AuthService;
import dev.harsh.chatroom.server.auth.SessionManager;
import dev.harsh.chatroom.server.cache.MessageHistoryCache;
import dev.harsh.chatroom.server.config.ServerConfig;
import dev.harsh.chatroom.server.connection.ClientConnection;
import dev.harsh.chatroom.server.connection.ConnectionHandler;
import dev.harsh.chatroom.server.connection.ConnectionManager;
import dev.harsh.chatroom.server.health.HealthCheckServer;
import dev.harsh.chatroom.server.metrics.MetricsRegistry;
import dev.harsh.chatroom.server.room.RoomManager;
import dev.harsh.chatroom.server.util.GracefulShutdown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main TCP Chat Server.
 * <p>
 * Uses a blocking {@link ServerSocket} acceptor loop with virtual threads
 * for handling each client connection. This gives us the simplicity of
 * one-thread-per-connection without the OS thread overhead.
 */
public final class ChatServer {

    private static final Logger log = LoggerFactory.getLogger(ChatServer.class);

    private final ServerConfig config;
    private final ConnectionManager connectionManager;
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final RoomManager roomManager;
    private final MessageHistoryCache historyCache;
    private final MetricsRegistry metrics;
    private final GracefulShutdown shutdown;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private HealthCheckServer healthCheckServer;

    public ChatServer(ServerConfig config) {
        this.config = config;
        this.connectionManager = new ConnectionManager(config.getMaxConnections());
        this.authService = new AuthService();
        this.sessionManager = new SessionManager();
        this.roomManager = new RoomManager(config.getMaxRooms(), config.getDefaultRoom());
        this.historyCache = new MessageHistoryCache(
                config.getMessageHistorySize(),
                config.getMessageHistoryTtlMinutes());
        this.metrics = new MetricsRegistry();
        this.shutdown = new GracefulShutdown();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        // Register metrics gauges
        metrics.registerGauge("chatroom.connections.active",
                "Active connections", connectionManager::getActiveCount);
        metrics.registerGauge("chatroom.rooms.active",
                "Active rooms", roomManager::getRoomCount);
        metrics.registerGauge("chatroom.sessions.active",
                "Active sessions", sessionManager::getActiveSessionCount);
        metrics.registerGauge("chatroom.cache.messages",
                "Cached messages", historyCache::getTotalCachedMessages);
    }

    /**
     * Start the server and begin accepting connections.
     */
    public void start() throws IOException {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Server is already running");
        }

        // Start health check server
        try {
            healthCheckServer = new HealthCheckServer(
                    config.getHealthPort(), connectionManager, roomManager, metrics);
            healthCheckServer.start();
        } catch (IOException e) {
            log.warn("Failed to start health check server on port {}: {}", config.getHealthPort(), e.getMessage());
        }

        // Register shutdown hooks
        shutdown.addTask("Stop accepting connections", this::stopAccepting);
        shutdown.addTask("Disconnect all clients", connectionManager::disconnectAll);
        shutdown.addTask("Clear sessions", sessionManager::clearAll);
        shutdown.addTask("Stop health server", () -> {
            if (healthCheckServer != null)
                healthCheckServer.stop();
        });
        shutdown.addTask("Shutdown executor", executor::close);
        shutdown.registerShutdownHook();

        // Open server socket
        serverSocket = new ServerSocket(config.getPort());
        log.info("========================================");
        log.info("  Chat Server started on port {}", config.getPort());
        log.info("  Health check on port {}", config.getHealthPort());
        log.info("  Max connections: {}", config.getMaxConnections());
        log.info("  Default room: '{}'", config.getDefaultRoom());
        log.info("========================================");

        // Acceptor loop
        acceptLoop();
    }

    /**
     * Main acceptor loop — blocks on accept(), spawns a virtual thread per client.
     */
    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                metrics.incrementConnections();

                ClientConnection connection = new ClientConnection(clientSocket);

                if (!connectionManager.register(connection)) {
                    // Max connections reached
                    connection.send(
                            dev.harsh.chatroom.protocol.ChatMessage.error(503, "Server at capacity. Try again later."));
                    connection.disconnect();
                    continue;
                }

                // Spawn a virtual thread to handle this client
                ConnectionHandler handler = new ConnectionHandler(
                        connection, connectionManager, authService, sessionManager,
                        roomManager, historyCache, metrics, config);
                executor.submit(handler);

            } catch (IOException e) {
                if (running.get()) {
                    log.error("Error accepting connection: {}", e.getMessage(), e);
                    metrics.incrementErrors();
                }
            }
        }
    }

    private void stopAccepting() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("Error closing server socket: {}", e.getMessage());
        }
    }

    /**
     * Stop the server programmatically.
     */
    public void stop() {
        log.info("Stopping server...");
        shutdown.executeShutdown();
    }

    // --- Main Entry Point ---

    public static void main(String[] args) {
        try {
            ServerConfig config = new ServerConfig();
            ChatServer server = new ChatServer(config);
            server.start();
        } catch (Exception e) {
            log.error("Fatal error starting server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
