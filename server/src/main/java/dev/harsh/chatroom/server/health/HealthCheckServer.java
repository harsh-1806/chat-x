package dev.harsh.chatroom.server.health;

import com.sun.net.httpserver.HttpServer;
import dev.harsh.chatroom.server.connection.ConnectionManager;
import dev.harsh.chatroom.server.metrics.MetricsRegistry;
import dev.harsh.chatroom.server.room.RoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Lightweight HTTP health check server on a separate port.
 * Exposes /health and /metrics endpoints.
 */
public final class HealthCheckServer {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckServer.class);

    private final HttpServer httpServer;

    public HealthCheckServer(int port, ConnectionManager connectionManager,
            RoomManager roomManager, MetricsRegistry metrics) throws IOException {
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);

        // Health endpoint
        httpServer.createContext("/health", exchange -> {
            String response = String.format(
                    "{\"status\":\"UP\",\"connections\":%d,\"rooms\":%d}",
                    connectionManager.getActiveCount(),
                    roomManager.getRoomCount());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Metrics endpoint (Prometheus scrape)
        httpServer.createContext("/metrics", exchange -> {
            String metricsOutput = metrics.scrape();
            byte[] bytes = metricsOutput.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        httpServer.setExecutor(null); // Default executor
    }

    public void start() {
        httpServer.start();
        log.info("Health check server started on port {}", httpServer.getAddress().getPort());
    }

    public void stop() {
        httpServer.stop(1);
        log.info("Health check server stopped");
    }
}
