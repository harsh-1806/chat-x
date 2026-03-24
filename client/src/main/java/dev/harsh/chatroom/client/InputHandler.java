package dev.harsh.chatroom.client;

import dev.harsh.chatroom.protocol.ChatMessage;
import dev.harsh.chatroom.protocol.ProtocolConstants;

import java.io.IOException;

/**
 * Parses user input from the terminal and dispatches commands.
 */
public final class InputHandler {

    private final ClientConnectionManager connection;
    private String activeRoom;

    public InputHandler(ClientConnectionManager connection) {
        this.connection = connection;
        this.activeRoom = ProtocolConstants.DEFAULT_ROOM;
    }

    /**
     * Process a line of user input. Returns false if the user wants to quit.
     */
    public boolean handleInput(String line) throws IOException {
        if (line == null || line.isBlank())
            return true;

        String trimmed = line.trim();

        if (trimmed.startsWith("/")) {
            return handleCommand(trimmed);
        } else {
            // Regular chat message to the active room
            connection.send(ChatMessage.chat(activeRoom, null, trimmed));
            return true;
        }
    }

    private boolean handleCommand(String input) throws IOException {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : null;

        switch (command) {
            case "/join" -> {
                if (arg == null || arg.isBlank()) {
                    System.out.println("Usage: /join <room>");
                } else {
                    connection.send(ChatMessage.join(arg));
                    activeRoom = arg;
                    System.out.println("Active room set to: " + arg);
                }
            }
            case "/leave" -> {
                if (arg == null || arg.isBlank()) {
                    System.out.println("Usage: /leave <room>");
                } else {
                    connection.send(ChatMessage.leave(arg));
                    if (activeRoom.equals(arg)) {
                        activeRoom = ProtocolConstants.DEFAULT_ROOM;
                        System.out.println("Active room reset to: " + activeRoom);
                    }
                }
            }
            case "/rooms" -> {
                connection.send(ChatMessage.listRooms());
            }
            case "/history" -> {
                int count = 50;
                if (arg != null) {
                    try {
                        count = Integer.parseInt(arg);
                    } catch (NumberFormatException e) {
                        System.out.println("Usage: /history [count]");
                        return true;
                    }
                }
                connection.send(ChatMessage.historyRequest(activeRoom, count));
            }
            case "/room" -> {
                if (arg == null || arg.isBlank()) {
                    System.out.println("Active room: " + activeRoom);
                } else {
                    activeRoom = arg;
                    System.out.println("Active room set to: " + activeRoom);
                }
            }
            case "/help" -> {
                MessageRenderer.printHelp();
            }
            case "/quit", "/exit" -> {
                connection.send(ChatMessage.disconnect("User quit"));
                return false;
            }
            default -> {
                System.out.println("Unknown command: " + command + ". Type /help for commands.");
            }
        }
        return true;
    }

    public String getActiveRoom() {
        return activeRoom;
    }
}
