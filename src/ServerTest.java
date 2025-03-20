import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.util.Random;

public class ServerTest {
    private Server server;
    private int port;

    @BeforeEach
    public void setup() throws Exception {
        // Set test mode to avoid System.exit() during tests
        Server.testMode = true;
        port = findAvailablePort();
        assertTrue(port > 0, "Failed to find an available port");
        server = new Server(port);
    }

    @AfterEach
    public void cleanup() {
        if (server != null && server.isRunning()) {
            server.shutdown();
        }
    }

    private static int findAvailablePort() {
        Random random = new Random();
        for (int i = 0; i < 20; i++) {
            int port = 5000 + random.nextInt(65536 - 5000);
            try (ServerSocket socket = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                // Try next port
            }
        }
        return -1;
    }

    @Test
    public void testServerConstructorValidPort() {
        System.out.println("Running testServerConstructorValidPort: Verifying that the server is running after construction.");
        assertTrue(server.isRunning(), "Server should be running after construction");
        System.out.println("testServerConstructorValidPort passed: Server constructed on port " + port);
    }

    @Test
    public void testGetMemberListInitially() {
        System.out.println("Running testGetMemberListInitially: Checking that the member list is empty initially.");
        assertEquals("", server.getMemberList(), "Member list should be empty initially");
        System.out.println("testGetMemberListInitially passed: Member list is empty as expected.");
    }

    @Test
    public void testRegisterClientUpdatesMemberListAndCoordinator() throws Exception {
        System.out.println("Running testRegisterClientUpdatesMemberListAndCoordinator: Registering dummy client 'Client1' and verifying member list and coordinator.");
        DummyClientHandler client1 = createAndRegisterClient("Client1");
        assertEquals("Client1", server.getMemberList(), "Member list should contain 'Client1'");
        assertTrue(server.isClientCoordinator("Client1"), "Client1 should be the coordinator");
        System.out.println("testRegisterClientUpdatesMemberListAndCoordinator passed: Client1 registered and set as coordinator.");
    }

    @Test
    public void testBroadcastMessage() throws Exception {
        System.out.println("Running testBroadcastMessage: Verifying that broadcast messages are delivered to all clients.");
        DummyClientHandler client1 = createAndRegisterClient("Client1");
        DummyClientHandler client2 = createAndRegisterClient("Client2");
        client1.lastMessage = null;
        client2.lastMessage = null;
        String message = "Test broadcast";
        server.broadcastMessage(message);
        assertEquals(message, client1.lastMessage, "Client1 should receive the message");
        assertEquals(message, client2.lastMessage, "Client2 should receive the message");
        System.out.println("testBroadcastMessage passed: Broadcast message delivered to all clients.");
    }

    @Test
    public void testCloseServerSocket() throws Exception {
        System.out.println("Running testShutdownClosesServerSocket: Checking that the serverSocket is closed after shutdown.");
        server.shutdown();
        Field serverSocketField = Server.class.getDeclaredField("serverSocket");
        serverSocketField.setAccessible(true);
        ServerSocket serverSocket = (ServerSocket) serverSocketField.get(server);
        assertTrue(serverSocket.isClosed(), "ServerSocket should be closed after shutdown");
        System.out.println("testShutdownClosesServerSocket passed: ServerSocket is closed after shutdown.");
    }

    // Helper method to register dummy client
    private DummyClientHandler createAndRegisterClient(String clientName) throws IOException {
        DummySocket socket = new DummySocket();
        DummyClientHandler client = new DummyClientHandler(socket, server);
        server.registerClient(clientName, client);
        return client;
    }

    // Dummy classes for testing
    private static class DummySocket extends Socket {
        private boolean closed = false;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream getOutputStream() { return out; }
        @Override public synchronized void close() { closed = true; }
        @Override public boolean isClosed() { return closed; }
    }

    private static class DummyClientHandler extends ClientHandler {
        public String lastMessage = null;
        public DummyClientHandler(Socket socket, Server server) throws IOException {
            super(socket, server);
        }
        @Override
        public synchronized void sendMessage(String message) {
            lastMessage = message;
        }
    }
}
