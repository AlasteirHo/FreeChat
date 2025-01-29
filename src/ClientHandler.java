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
        } catch (IOException e) {
            if (running) {
                System.err.println("Error handling client " + clientId + ": " + e.getMessage());
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
            }
        } catch (Exception e) {
            System.err.println("Error processing message from " + clientId + ": " + e.getMessage());
        }
    }

    public synchronized void sendMessage(String message) {
        if (!running || socket.isClosed()) return;
        try {
            out.println(message);
            if (out.checkError()) {
                throw new IOException("Failed to send message");
            }
        } catch (IOException e) {
            System.err.println("Error sending message to " + clientId + ": " + e.getMessage());
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
        } catch (IOException e) {
            System.err.println("Error closing connection for " + clientId + ": " + e.getMessage());
        }
    }

    public String getClientId() {
        return clientId;
    }

    public Socket getSocket() {
        return socket;
    }
}
