package dev.harsh.chatroom.server.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory authentication service with bcrypt password hashing.
 * <p>
 * Supports self-registration: first auth attempt with a new username creates
 * the account.
 * Subsequent attempts must match the stored password.
 */
public final class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** username -> bcrypt hash */
    private final Map<String, String> userStore = new ConcurrentHashMap<>();

    /**
     * Authenticate a user. If the username doesn't exist, registers it
     * automatically.
     *
     * @return session ID on success, null on failure
     */
    public String authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Auth attempt with empty username or password");
            return null;
        }

        String existingHash = userStore.get(username);

        if (existingHash == null) {
            // New user — register
            String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
            // Use putIfAbsent for thread safety — another thread might register same
            // username
            String raceHash = userStore.putIfAbsent(username, hash);
            if (raceHash != null) {
                // Someone else registered this username between our check and put
                return verifyPassword(username, password, raceHash);
            }
            log.info("New user registered: {}", username);
            return generateSessionId();
        } else {
            // Existing user — verify password
            return verifyPassword(username, password, existingHash);
        }
    }

    private String verifyPassword(String username, String password, String hash) {
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);
        if (result.verified) {
            log.info("User authenticated: {}", username);
            return generateSessionId();
        } else {
            log.warn("Failed auth attempt for user: {}", username);
            return null;
        }
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Check if a user is registered.
     */
    public boolean isRegistered(String username) {
        return userStore.containsKey(username);
    }

    /**
     * Number of registered users.
     */
    public int getUserCount() {
        return userStore.size();
    }
}
