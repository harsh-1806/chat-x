package dev.harsh.chatroom.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates graceful shutdown of all server components.
 */
public final class GracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);

    private final List<ShutdownTask> tasks = new ArrayList<>();
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public void addTask(String name, Runnable task) {
        tasks.add(new ShutdownTask(name, task));
    }

    /**
     * Register this as a JVM shutdown hook.
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, executing {} shutdown tasks...", tasks.size());
            executeShutdown();
        }, "shutdown-hook"));
    }

    /**
     * Execute all shutdown tasks in order.
     */
    public void executeShutdown() {
        for (ShutdownTask task : tasks) {
            try {
                log.info("Executing shutdown task: {}", task.name);
                task.runnable.run();
                log.info("Shutdown task completed: {}", task.name);
            } catch (Exception e) {
                log.error("Shutdown task failed: {}: {}", task.name, e.getMessage(), e);
            }
        }
        shutdownLatch.countDown();
        log.info("All shutdown tasks completed");
    }

    /**
     * Block until shutdown completes or timeout.
     */
    public boolean awaitShutdown(long timeout, TimeUnit unit) throws InterruptedException {
        return shutdownLatch.await(timeout, unit);
    }

    private record ShutdownTask(String name, Runnable runnable) {
    }
}
