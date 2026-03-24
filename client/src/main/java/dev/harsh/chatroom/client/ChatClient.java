package dev.harsh.chatroom.client;

import dev.harsh.chatroom.protocol.ChatMessage;
import dev.harsh.chatroom.protocol.MessageType;
import dev.harsh.chatroom.protocol.ProtocolConstants;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Interactive CLI Chat Client.
 * <p>
 * Connects to the chat server, authenticates, and provides an interactive
 * terminal interface for chatting.
 */
public final class ChatClient {

    private static final String BANNER = """

            \033[36m\033[1m╔════════════════════════════════════════╗
            ║                                        ║
            ║       ☁  TCP Chat Room Client  ☁       ║
            ║                                        ║
            ╚════════════════════════════════════════╝\033[0m
            """;

    private final ClientConnectionManager connection;
    private MessageRenderer renderer;
    private InputHandler inputHandler;
    private volatile boolean running = true;

    public ChatClient(String host, int port) {
        this.connection = new ClientConnectionManager(host, port);
    }

    /**
     * Start the client — connect, authenticate, and enter interactive mode.
     */
    public void start() {
        System.out.println(BANNER);

        // Connect
        if (!connection.connect()) {
            System.exit(1);
        }

        try {
            // Authenticate
            authenticate();

            // Start receiver thread
            Thread receiverThread = Thread.ofVirtual()
                    .name("message-receiver")
                    .start(this::receiveLoop);

            // Interactive input loop (main thread)
            inputLoop();

            // Cleanup
            running = false;
            connection.disconnect();
            receiverThread.interrupt();

        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    /**
     * Interactive authentication flow.
     */
    private void authenticate() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("\033[33mUsername: \033[0m");
        String username = reader.readLine();
        if (username == null || username.isBlank()) {
            System.out.println("Username cannot be empty.");
            System.exit(1);
        }

        System.out.print("\033[33mPassword: \033[0m");
        String password = reader.readLine();
        if (password == null || password.isBlank()) {
            System.out.println("Password cannot be empty.");
            System.exit(1);
        }

        // Initialize renderer with username
        this.renderer = new MessageRenderer(username.trim());
        this.inputHandler = new InputHandler(connection);

        // Send auth request
        connection.send(ChatMessage.authRequest(username.trim(), password.trim()));

        // Wait for auth response
        ChatMessage response = connection.receive();
        if (response == null) {
            System.out.println("Server closed connection.");
            System.exit(1);
        }

        renderer.render(response);

        if (response.getType() == MessageType.AUTH_RESPONSE && !response.isSuccess()) {
            System.exit(1);
        }

        // Handle any immediate follow-up messages (history, system)
        System.out.printf("%n\033[2mType /help for commands. Active room: %s\033[0m%n%n",
                inputHandler.getActiveRoom());
    }

    /**
     * Background thread: continuously reads messages from server and renders them.
     */
    private void receiveLoop() {
        while (running && connection.isConnected()) {
            try {
                ChatMessage message = connection.receive();
                if (message == null) {
                    if (running) {
                        System.out.println("\n\033[31m\033[1m⚡ Server disconnected.\033[0m");
                        running = false;
                    }
                    break;
                }

                // Handle heartbeat silently
                if (message.getType() == MessageType.PING) {
                    connection.send(ChatMessage.pong());
                    continue;
                }

                // Render the message, then reprint the prompt
                System.out.print("\r\033[K"); // Clear current line
                renderer.render(message);
                printPrompt();

            } catch (IOException e) {
                if (running) {
                    System.out.println("\n\033[31mConnection lost: " + e.getMessage() + "\033[0m");
                    running = false;
                }
                break;
            }
        }
    }

    /**
     * Main thread: reads user input and dispatches commands.
     */
    private void inputLoop() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (running && connection.isConnected()) {
            printPrompt();
            try {
                String line = reader.readLine();
                if (line == null)
                    break; // EOF (Ctrl+D)

                boolean continueRunning = inputHandler.handleInput(line);
                if (!continueRunning) {
                    running = false;
                    break;
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Input error: " + e.getMessage());
                }
                break;
            }
        }

        System.out.println("\n\033[33mGoodbye! 👋\033[0m");
    }

    private void printPrompt() {
        System.out.printf("\033[32m[%s]\033[0m > ", inputHandler != null ? inputHandler.getActiveRoom() : "chat");
        System.out.flush();
    }

    // --- Main Entry Point ---

    public static void main(String[] args) {
        String host = "localhost";
        int port = ProtocolConstants.DEFAULT_PORT;

        // Parse command-line args
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host", "-h" -> {
                    if (i + 1 < args.length)
                        host = args[++i];
                }
                case "--port", "-p" -> {
                    if (i + 1 < args.length)
                        port = Integer.parseInt(args[++i]);
                }
                case "--help" -> {
                    System.out.println("Usage: chat-client [--host HOST] [--port PORT]");
                    System.out.println("  --host, -h    Server hostname (default: localhost)");
                    System.out.println("  --port, -p    Server port (default: " + ProtocolConstants.DEFAULT_PORT + ")");
                    System.exit(0);
                }
            }
        }

        ChatClient client = new ChatClient(host, port);
        client.start();
    }
}
