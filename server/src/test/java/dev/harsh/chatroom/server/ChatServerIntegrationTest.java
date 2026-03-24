package dev.harsh.chatroom.server;

import dev.harsh.chatroom.protocol.ChatMessage;
import dev.harsh.chatroom.protocol.MessageCodec;
import dev.harsh.chatroom.protocol.MessageType;
import dev.harsh.chatroom.server.config.ServerConfig;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: starts a real server, connects a client, and
 * runs through the auth → join → chat → leave flow.
 */
class ChatServerIntegrationTest {

    private static final int TEST_PORT = 19090;
    private static final int TEST_HEALTH_PORT = 18081;

    private ChatServer server;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws Exception {
        // Create config with test ports
        var config = ConfigFactory.parseString(
                "chatroom.server.port=" + TEST_PORT + "\n" +
                        "chatroom.server.health-port=" + TEST_HEALTH_PORT)
                .withFallback(ConfigFactory.load());

        ServerConfig serverConfig = new ServerConfig(config);
        server = new ChatServer(serverConfig);

        // Start server in background thread
        serverThread = Thread.ofVirtual().name("test-server").start(() -> {
            try {
                server.start();
            } catch (IOException e) {
                // Expected on shutdown
            }
        });

        // Wait for server to be ready
        Thread.sleep(1000);
    }

    @AfterEach
    void tearDown() {
        server.stop();
        serverThread.interrupt();
    }

    @Test
    @DisplayName("Client can connect, authenticate, and receive welcome")
    void connectAndAuth() throws Exception {
        try (Socket socket = new Socket("localhost", TEST_PORT)) {
            var in = socket.getInputStream();
            var out = socket.getOutputStream();

            // Send auth
            MessageCodec.writeTo(ChatMessage.authRequest("testuser", "testpass"), out);

            // Read auth response
            ChatMessage response = MessageCodec.readFrom(in);
            assertNotNull(response);
            assertEquals(MessageType.AUTH_RESPONSE, response.getType());
            assertTrue(response.isSuccess());
            assertNotNull(response.getSessionId());
        }
    }

    @Test
    @DisplayName("Duplicate username is rejected")
    void duplicateUsernameRejected() throws Exception {
        // First client
        Socket client1 = new Socket("localhost", TEST_PORT);
        MessageCodec.writeTo(ChatMessage.authRequest("uniqueuser", "pass"), client1.getOutputStream());
        ChatMessage resp1 = MessageCodec.readFrom(client1.getInputStream());
        assertTrue(resp1.isSuccess());

        // Read follow-up messages (history response from auto-join)
        // The server may send history after auth

        // Second client with same username
        try (Socket client2 = new Socket("localhost", TEST_PORT)) {
            MessageCodec.writeTo(ChatMessage.authRequest("uniqueuser", "pass"), client2.getOutputStream());
            ChatMessage resp2 = MessageCodec.readFrom(client2.getInputStream());
            assertFalse(resp2.isSuccess(), "Duplicate username should be rejected");
        }

        client1.close();
    }

    @Test
    @DisplayName("Two clients can chat in the same room")
    void twoChatClients() throws Exception {
        // Client 1
        Socket c1 = new Socket("localhost", TEST_PORT);
        MessageCodec.writeTo(ChatMessage.authRequest("alice", "pass1"), c1.getOutputStream());
        ChatMessage auth1 = MessageCodec.readFrom(c1.getInputStream());
        assertTrue(auth1.isSuccess());

        // Client 2
        Socket c2 = new Socket("localhost", TEST_PORT);
        MessageCodec.writeTo(ChatMessage.authRequest("bob", "pass2"), c2.getOutputStream());
        ChatMessage auth2 = MessageCodec.readFrom(c2.getInputStream());
        assertTrue(auth2.isSuccess());

        // Client 1 will receive a SYSTEM message about bob joining — drain it
        // (both are auto-joined to "general")
        Thread.sleep(200);

        // Bob sends a message
        MessageCodec.writeTo(ChatMessage.chat("general", null, "Hello from Bob!"), c2.getOutputStream());

        // Alice should receive it
        Thread.sleep(200);
        // Set a read timeout so we don't block forever
        c1.setSoTimeout(2000);
        try {
            // Drain any interim messages (system notifications) and find the chat
            boolean foundChat = false;
            for (int i = 0; i < 5; i++) {
                ChatMessage msg = MessageCodec.readFrom(c1.getInputStream());
                if (msg != null && msg.getType() == MessageType.CHAT
                        && "Hello from Bob!".equals(msg.getContent())) {
                    foundChat = true;
                    assertEquals("bob", msg.getSender());
                    break;
                }
            }
            assertTrue(foundChat, "Alice should receive Bob's chat message");
        } finally {
            c1.close();
            c2.close();
        }
    }

    @Test
    @DisplayName("Unauthenticated client gets error for non-auth messages")
    void unauthenticatedRejection() throws Exception {
        try (Socket socket = new Socket("localhost", TEST_PORT)) {
            // Send chat without auth
            MessageCodec.writeTo(ChatMessage.chat("general", "user", "hi"), socket.getOutputStream());

            ChatMessage response = MessageCodec.readFrom(socket.getInputStream());
            assertNotNull(response);
            assertEquals(MessageType.ERROR, response.getType());
            assertEquals(401, response.getErrorCode());
        }
    }

    @Test
    @DisplayName("List rooms returns at least the default room")
    void listRooms() throws Exception {
        try (Socket socket = new Socket("localhost", TEST_PORT)) {
            var in = socket.getInputStream();
            var out = socket.getOutputStream();

            // Auth first
            MessageCodec.writeTo(ChatMessage.authRequest("lister", "pass"), out);
            MessageCodec.readFrom(in); // auth response

            // Drain auto-join messages
            Thread.sleep(200);
            socket.setSoTimeout(500);
            try {
                while (true)
                    MessageCodec.readFrom(in);
            } catch (Exception ignored) {
            }
            socket.setSoTimeout(2000);

            // List rooms
            MessageCodec.writeTo(ChatMessage.listRooms(), out);
            ChatMessage response = MessageCodec.readFrom(in);

            assertNotNull(response);
            assertEquals(MessageType.LIST_ROOMS_RESPONSE, response.getType());
            assertTrue(response.getRooms().contains("general"));
        }
    }
}
