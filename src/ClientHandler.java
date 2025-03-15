import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Server server;
    private final BufferedReader in;
    private final PrintWriter out;
    private String clientId;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, Server server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void run() {
        try {
            String initialMessage = in.readLine();
            if (initialMessage != null && initialMessage.startsWith("CONNECT:")) {
                clientId = initialMessage.substring(8);
                server.registerClient(clientId, this);
                while (running && !socket.isClosed()) {
                    String input = in.readLine();
                    if (input == null) {
                        break;
                    }
                    if (input.equals("QUIT")) {
                        System.out.println("Client " + clientId + " requesting quit");
                        break;
                    }
                    handleMessage(input);
                }
            }
        } catch (IOException ex) {
            if (running) {
                System.err.println("Error handling client " + clientId + ": " + ex.getMessage());
            }
        } finally {
            closeConnection();
        }
    }

    private void handleMessage(String message) {
        if (!running) return;
        try {
            if (message.startsWith("BROADCAST:")) {
                server.broadcastMessage("MSG:" + clientId + ":" + message.substring(10));
            } else if (message.startsWith("PRIVATE:")) {
                String[] parts = message.substring(8).split(":", 2);
                server.sendPrivateMessage(clientId, parts[0], parts[1]);
            } else if (message.equals("GET_MEMBERS")) {
                sendMessage("MEMBER_LIST:" + server.getMemberList());
            } else if (message.equals("REQUEST_DETAILS")) {
                server.sendMemberDetails(clientId);
            } else if (message.equals("SERVER_SHUTDOWN")) {
                // Check if this client is the coordinator before allowing shutdown
                if (server.isClientCoordinator(clientId)) {
                    System.out.println("Server shutdown requested by coordinator: " + clientId);
                    // Notify all clients that server is shutting down
                    server.broadcastMessage("SERVER_SHUTTING_DOWN");
                    // Use a separate thread to avoid deadlock during shutdown
                    new Thread(() -> {
                        try {
                            // Give clients a moment to receive the shutdown message
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        server.shutdown();
                        System.exit(0);
                    }).start();
                } else {
                    System.out.println("Unauthorized server shutdown attempt by: " + clientId);
                }
            }
        } catch (Exception ex) {
            System.err.println("Error processing message from " + clientId + ": " + ex.getMessage());
        }
    }

    public synchronized void sendMessage(String message) {
        if (!running || socket.isClosed()) return;
        try {
            out.println(message);
            if (out.checkError()) {
                throw new IOException("Failed to send message");
            }
        } catch (IOException ex) {
            System.err.println("Error sending message to " + clientId + ": " + ex.getMessage());
            closeConnection();
        }
    }

    public synchronized void closeConnection() {
        if (!running) return;
        running = false;
        try {
            if (clientId != null && server.isRunning()) {
                server.removeClient(clientId);
            }
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ex) {
            System.err.println("Error closing connection for " + clientId + ": " + ex.getMessage());
        }
    }

    public Socket getSocket() {
        return socket;
    }
}