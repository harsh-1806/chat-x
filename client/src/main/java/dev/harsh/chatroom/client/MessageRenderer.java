package dev.harsh.chatroom.client;

import dev.harsh.chatroom.protocol.ChatMessage;
import dev.harsh.chatroom.protocol.MessageType;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders chat messages with ANSI colors for a beautiful terminal experience.
 */
public final class MessageRenderer {

    // ANSI color codes
    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String ITALIC = "\033[3m";

    private static final String RED = "\033[31m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String BLUE = "\033[34m";
    private static final String MAGENTA = "\033[35m";
    private static final String CYAN = "\033[36m";
    private static final String WHITE = "\033[37m";
    private static final String BRIGHT_GREEN = "\033[92m";
    private static final String BRIGHT_YELLOW = "\033[93m";
    private static final String BRIGHT_CYAN = "\033[96m";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String currentUser;

    public MessageRenderer(String currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * Render an incoming message to the terminal.
     */
    public void render(ChatMessage message) {
        switch (message.getType()) {
            case CHAT -> renderChat(message);
            case SYSTEM -> renderSystem(message);
            case AUTH_RESPONSE -> renderAuthResponse(message);
            case LIST_ROOMS_RESPONSE -> renderRoomList(message);
            case HISTORY_RESPONSE -> renderHistory(message);
            case ERROR -> renderError(message);
            case PONG -> {
            } // Silent
            case DISCONNECT -> renderDisconnect(message);
            default -> renderUnknown(message);
        }
    }

    private void renderChat(ChatMessage msg) {
        String time = formatTime(msg.getTimestamp());
        String sender = msg.getSender();
        String room = msg.getRoom();

        if (sender != null && sender.equals(currentUser)) {
            // Own message
            System.out.printf("%s[%s]%s %s[%s]%s %s(you)%s: %s%n",
                    DIM, time, RESET,
                    DIM, room, RESET,
                    BRIGHT_GREEN, RESET,
                    msg.getContent());
        } else {
            // Others' messages
            String color = getUserColor(sender);
            System.out.printf("%s[%s]%s %s[%s]%s %s%s%s%s: %s%n",
                    DIM, time, RESET,
                    DIM, room, RESET,
                    color, BOLD, sender, RESET,
                    msg.getContent());
        }
    }

    private void renderSystem(ChatMessage msg) {
        String time = formatTime(msg.getTimestamp());
        System.out.printf("%s[%s] ★ %s%s%n", YELLOW, time, msg.getContent(), RESET);
    }

    private void renderAuthResponse(ChatMessage msg) {
        if (msg.isSuccess()) {
            System.out.printf("%s%s✓ %s%s%n", BRIGHT_GREEN, BOLD, msg.getContent(), RESET);
        } else {
            System.out.printf("%s%s✗ Authentication failed: %s%s%n", RED, BOLD, msg.getContent(), RESET);
        }
    }

    private void renderRoomList(ChatMessage msg) {
        System.out.printf("%n%s%s╔══ Available Rooms ══╗%s%n", CYAN, BOLD, RESET);
        List<String> rooms = msg.getRooms();
        if (rooms.isEmpty()) {
            System.out.printf("  %s(none)%s%n", DIM, RESET);
        } else {
            for (String room : rooms) {
                System.out.printf("  %s▸ %s%s%n", BRIGHT_CYAN, room, RESET);
            }
        }
        System.out.printf("%s%s╚═════════════════════╝%s%n%n", CYAN, BOLD, RESET);
    }

    private void renderHistory(ChatMessage msg) {
        List<ChatMessage> history = msg.getHistory();
        if (history.isEmpty()) {
            System.out.printf("%s  (no recent messages in '%s')%s%n", DIM, msg.getRoom(), RESET);
            return;
        }
        System.out.printf("%n%s── Recent messages in '%s' ──%s%n", DIM, msg.getRoom(), RESET);
        for (ChatMessage h : history) {
            renderChat(h);
        }
        System.out.printf("%s── End of history ──%s%n%n", DIM, RESET);
    }

    private void renderError(ChatMessage msg) {
        System.out.printf("%s%s✗ Error [%d]: %s%s%n", RED, BOLD, msg.getErrorCode(), msg.getErrorMessage(), RESET);
    }

    private void renderDisconnect(ChatMessage msg) {
        System.out.printf("%s%s⚡ Server: %s%s%n", YELLOW, BOLD,
                msg.getContent() != null ? msg.getContent() : "Disconnected", RESET);
    }

    private void renderUnknown(ChatMessage msg) {
        System.out.printf("%s  [%s] %s%s%n", DIM, msg.getType(), msg.getContent(), RESET);
    }

    /**
     * Print the help/commands reference.
     */
    public static void printHelp() {
        System.out.printf("%n%s%s╔══ Commands ══╗%s%n", MAGENTA, BOLD, RESET);
        System.out.printf("  %s/join <room>%s    — Join or create a room%n", BRIGHT_YELLOW, RESET);
        System.out.printf("  %s/leave <room>%s   — Leave a room%n", BRIGHT_YELLOW, RESET);
        System.out.printf("  %s/rooms%s          — List all rooms%n", BRIGHT_YELLOW, RESET);
        System.out.printf("  %s/history [n]%s    — Show last n messages (default 50)%n", BRIGHT_YELLOW, RESET);
        System.out.printf("  %s/room <room>%s    — Switch active room%n", BRIGHT_YELLOW, RESET);
        System.out.printf("  %s/help%s           — Show this help%n", BRIGHT_YELLOW, RESET);
        System.out.printf("  %s/quit%s           — Disconnect and exit%n", BRIGHT_YELLOW, RESET);
        System.out.printf("%s%s╚═══════════════╝%s%n%n", MAGENTA, BOLD, RESET);
    }

    private String formatTime(long epochMillis) {
        if (epochMillis <= 0)
            return "--:--:--";
        return LocalTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(TIME_FMT);
    }

    /**
     * Assign a consistent color to a username using hash.
     */
    private String getUserColor(String username) {
        if (username == null)
            return WHITE;
        String[] colors = { CYAN, MAGENTA, BLUE, GREEN, BRIGHT_CYAN, BRIGHT_YELLOW };
        int index = Math.abs(username.hashCode()) % colors.length;
        return colors[index];
    }
}
