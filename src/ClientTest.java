import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.util.Random;

public class ClientTest {

    private static int findRandomPortInRange() {
        Random random = new Random();
        final int maxAttempts = 20;
        for (int i = 0; i < maxAttempts; i++) {
            int candidate = 5000 + random.nextInt(65536 - 5000);
            try (ServerSocket tmp = new ServerSocket(candidate)) {
                return candidate;
            } catch (IOException e) {
                // Try next candidate
            }
        }
        return -1;
    }

    private static class DummySocket extends Socket {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private boolean closed = false;
        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }
        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }
        @Override
        public synchronized void close() {
            closed = true;
        }
        @Override
        public boolean isClosed() {
            return closed;
        }
        public String getOutputString() {
            return outputStream.toString();
        }
    }

    private static class TestServer extends Server {
        public TestServer() throws IOException {
            super(pickRandomServerPort());
        }
        private static int pickRandomServerPort() throws IOException {
            int port = findRandomPortInRange();
            if (port == -1) {
                throw new IOException("Could not find a free port between 5000 to 65535.");
            }
            return port;
        }
        @Override public synchronized void registerClient(String clientId, ClientHandler handler) {}
        @Override public void broadcastMessage(String message) {}
        @Override public void sendPrivateMessage(String from, String to, String message) {}
        @Override public String getMemberList() { return "dummy"; }
        @Override public void sendMemberDetails(String clientId) {}
        @Override public boolean isClientCoordinator(String clientId) { return false; }
        @Override public void shutdown() {}
        @Override public boolean isRunning() {
            return false;
        }
        @Override public void removeClient(String clientId) {}
    }

    @Test
    public void testGetSocket() throws Exception {
        System.out.println("=== testGetSocket() ===");
        DummySocket socket = new DummySocket();
        TestServer server = new TestServer();
        ClientHandler handler = new ClientHandler(socket, server);
        assertEquals(socket, handler.getSocket(), "getSocket() should return the provided socket.");
        System.out.println("testGetSocket() passed: getSocket() returned the correct socket.\n");
    }

    @Test
    public void testSendMessage() throws Exception {
        System.out.println("=== testSendMessage() ===");
        DummySocket socket = new DummySocket();
        TestServer server = new TestServer();
        ClientHandler handler = new ClientHandler(socket, server);
        handler.sendMessage("Hello, world!");
        String written = socket.getOutputString().trim();
        assertEquals("Hello, world!", written, "sendMessage() should write the exact message to the socket output.");
        System.out.println("testSendMessage() passed: message was sent successfully.\n");
    }

    @Test
    public void testCloseConnection() throws Exception {
        System.out.println("=== testCloseConnection() ===");
        DummySocket socket = new DummySocket();
        TestServer server = new TestServer();
        ClientHandler handler = new ClientHandler(socket, server);
        handler.closeConnection();
        assertTrue(socket.isClosed(), "Socket should be marked closed after closeConnection().");
        System.out.println("testCloseConnection() passed: socket was closed successfully.\n");
    }
}
