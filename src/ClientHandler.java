import java.io.*;
import java.net.*;

// Utilize the runnable interface to create a thread when run, enters a state of constant "listening" and await for a request
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

    // Handles a client connection to the server or quitting, assumes a constant listening state
    public void run() {
        try {
            String initialMessage = in.readLine();
            if (initialMessage != null && initialMessage.toLowerCase().startsWith("/connect:")) {
                clientId = initialMessage.substring(9); // Ignore the first 9 characters of /connect: for message categorization
                server.registerClient(clientId, this);
                while (running && !socket.isClosed()) {
                    String input = in.readLine();
                    if (input == null) break;
                    if (input.equalsIgnoreCase("/quit")) {
                        System.out.println("Client " + clientId + " is requesting to quit");
                        break;
                    }
                    handleMessage(input);
                }
            }
        } catch (IOException ex) {
            if (running) {
                System.err.println("Error handling client message " + clientId + ": " + ex.getMessage());
            }
        } finally {
            closeConnection();
        }
    }

    // Handles the formating of messages based on the substring that begins each message
    private void handleMessage(String message) {
        if (!running) return;
        try {
            String lowerMessage = message.toLowerCase();

            if (lowerMessage.startsWith("/broadcast")) {
                server.broadcastMessage("/broadcast" + clientId + ":" + message.substring(10));
            } else if (lowerMessage.startsWith("/private")) {
                String[] parts = message.substring(8).split(":", 2);
                server.sendPrivateMessage(clientId, parts[0], parts[1]);
            } else if (lowerMessage.equals("/get_members")) {
                sendMessage("MEMBER_LIST:" + server.getMemberList());
                sendMessage("INACTIVE_MEMBER_LIST:" + server.getInactiveMemberList());
            } else if (lowerMessage.equals("/request_details")) {
                server.sendMemberDetails(clientId);
            } else if (lowerMessage.equals("/server_shutdown")) {
                if (server.isClientCoordinator(clientId)) {
                    System.out.println("Server shutdown requested by coordinator: " + clientId);
                    server.broadcastMessage("The Server is shutting down");
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        server.shutdown();
                        System.exit(0);
                    }).start();
                } else {
                    // Informs the host in the console that a non coordinator tried to shut the server down (Highly unlikely through chat commands)
                    System.out.println("Unauthorized server shutdown attempt by: " + clientId);
                }
            }
        } catch (Exception ex) {
            // Error handling in the instance a message cannot be received from a client
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
    // Handles a client leaving the server/chat, and closes their connection to prevent zombie thread
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
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException ex) {
            System.err.println("Error closing connection for " + clientId + ": " + ex.getMessage());
        }
    }

    public Socket getSocket() {
        return socket;
    }
}