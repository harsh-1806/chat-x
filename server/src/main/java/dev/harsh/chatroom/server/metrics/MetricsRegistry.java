package dev.harsh.chatroom.server.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Application metrics using Micrometer with Prometheus registry.
 */
public final class MetricsRegistry {

    private static final Logger log = LoggerFactory.getLogger(MetricsRegistry.class);

    private final PrometheusMeterRegistry registry;

    // Counters
    private final Counter connectionsTotal;
    private final Counter messagesReceived;
    private final Counter messagesSent;
    private final Counter authSuccessTotal;
    private final Counter authFailureTotal;
    private final Counter rateLimitHits;
    private final Counter errors;

    // Timers
    private final Timer messageProcessingTime;

    public MetricsRegistry() {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        this.connectionsTotal = Counter.builder("chatroom.connections.total")
                .description("Total connections received")
                .register(registry);

        this.messagesReceived = Counter.builder("chatroom.messages.received")
                .description("Total messages received from clients")
                .register(registry);

        this.messagesSent = Counter.builder("chatroom.messages.sent")
                .description("Total messages sent to clients")
                .register(registry);

        this.authSuccessTotal = Counter.builder("chatroom.auth.success")
                .description("Total successful authentications")
                .register(registry);

        this.authFailureTotal = Counter.builder("chatroom.auth.failure")
                .description("Total failed authentications")
                .register(registry);

        this.rateLimitHits = Counter.builder("chatroom.ratelimit.hits")
                .description("Total rate limit rejections")
                .register(registry);

        this.errors = Counter.builder("chatroom.errors")
                .description("Total errors")
                .register(registry);

        this.messageProcessingTime = Timer.builder("chatroom.message.processing.time")
                .description("Message processing duration")
                .register(registry);

        log.info("Metrics registry initialized");
    }

    /**
     * Register a gauge for dynamic values (e.g. active connections count).
     */
    public void registerGauge(String name, String description, Supplier<Number> supplier) {
        Gauge.builder(name, supplier)
                .description(description)
                .register(registry);
    }

    // Increment methods
    public void incrementConnections() {
        connectionsTotal.increment();
    }

    public void incrementMessagesReceived() {
        messagesReceived.increment();
    }

    public void incrementMessagesSent() {
        messagesSent.increment();
    }

    public void incrementAuthSuccess() {
        authSuccessTotal.increment();
    }

    public void incrementAuthFailure() {
        authFailureTotal.increment();
    }

    public void incrementRateLimitHits() {
        rateLimitHits.increment();
    }

    public void incrementErrors() {
        errors.increment();
    }

    public Timer getMessageProcessingTimer() {
        return messageProcessingTime;
    }

    /**
     * Get Prometheus-formatted metrics output.
     */
    public String scrape() {
        return registry.scrape();
    }
}
