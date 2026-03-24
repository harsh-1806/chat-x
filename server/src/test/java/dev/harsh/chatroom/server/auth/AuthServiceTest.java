package dev.harsh.chatroom.server.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for authentication — self-registration, login, and rejection.
 */
class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService();
    }

    @Test
    @DisplayName("First auth with new username auto-registers and returns sessionId")
    void autoRegistration() {
        String sessionId = authService.authenticate("newuser", "password123");

        assertNotNull(sessionId);
        assertFalse(sessionId.isBlank());
        assertTrue(authService.isRegistered("newuser"));
        assertEquals(1, authService.getUserCount());
    }

    @Test
    @DisplayName("Subsequent auth with correct password succeeds")
    void loginSuccess() {
        authService.authenticate("alice", "secret");

        String sessionId = authService.authenticate("alice", "secret");
        assertNotNull(sessionId);
    }

    @Test
    @DisplayName("Auth with wrong password fails")
    void loginFailure() {
        authService.authenticate("bob", "correct-password");

        String sessionId = authService.authenticate("bob", "wrong-password");
        assertNull(sessionId);
    }

    @Test
    @DisplayName("Auth with empty username returns null")
    void emptyUsernameReturnsNull() {
        assertNull(authService.authenticate("", "password"));
        assertNull(authService.authenticate("  ", "password"));
    }

    @Test
    @DisplayName("Auth with empty password returns null")
    void emptyPasswordReturnsNull() {
        assertNull(authService.authenticate("user", ""));
        assertNull(authService.authenticate("user", "  "));
    }

    @Test
    @DisplayName("Each successful auth returns unique sessionId")
    void uniqueSessionIds() {
        String s1 = authService.authenticate("user1", "pass1");
        String s2 = authService.authenticate("user2", "pass2");
        String s3 = authService.authenticate("user1", "pass1");

        assertNotNull(s1);
        assertNotNull(s2);
        assertNotNull(s3);
        assertNotEquals(s1, s2);
        assertNotEquals(s1, s3); // Different login => different session
    }
}
