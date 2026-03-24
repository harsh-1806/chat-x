package dev.harsh.chatroom.server.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Server configuration backed by HOCON (Typesafe Config).
 * Loads from application.conf on classpath, overridable by system properties and env vars.
 */
public final class ServerConfig {

    private final int port;
    private final int healthPort;
    private final int maxConnections;
    private final int heartbeatIntervalSeconds;
    private final int heartbeatTimeoutSeconds;
    private final int rateLimitMessagesPerSecond;
    private final int rateLimitBurstSize;
    private final int messageHistorySize;
    private final int messageHistoryTtlMinutes;
    private final int maxRooms;
    private final String defaultRoom;

    public ServerConfig() {
        this(ConfigFactory.load());
    }

    public ServerConfig(Config config) {
        Config server = config.getConfig("chatroom.server");
        this.port = server.getInt("port");
        this.healthPort = server.getInt("health-port");
        this.maxConnections = server.getInt("max-connections");
        this.heartbeatIntervalSeconds = server.getInt("heartbeat.interval-seconds");
        this.heartbeatTimeoutSeconds = server.getInt("heartbeat.timeout-seconds");
        this.rateLimitMessagesPerSecond = server.getInt("rate-limit.messages-per-second");
        this.rateLimitBurstSize = server.getInt("rate-limit.burst-size");
        this.messageHistorySize = server.getInt("cache.message-history-size");
        this.messageHistoryTtlMinutes = server.getInt("cache.message-history-ttl-minutes");
        this.maxRooms = server.getInt("max-rooms");
        this.defaultRoom = server.getString("default-room");
    }

    public int getPort() { return port; }
    public int getHealthPort() { return healthPort; }
    public int getMaxConnections() { return maxConnections; }
    public int getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public int getHeartbeatTimeoutSeconds() { return heartbeatTimeoutSeconds; }
    public int getRateLimitMessagesPerSecond() { return rateLimitMessagesPerSecond; }
    public int getRateLimitBurstSize() { return rateLimitBurstSize; }
    public int getMessageHistorySize() { return messageHistorySize; }
    public int getMessageHistoryTtlMinutes() { return messageHistoryTtlMinutes; }
    public int getMaxRooms() { return maxRooms; }
    public String getDefaultRoom() { return defaultRoom; }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "port=" + port +
                ", healthPort=" + healthPort +
                ", maxConnections=" + maxConnections +
                ", defaultRoom='" + defaultRoom + '\'' +
                '}';
    }
}
