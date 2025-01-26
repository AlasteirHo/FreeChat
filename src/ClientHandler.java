import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private Server server;
    private PrintWriter out;
    private BufferedReader in;
    private String clientId;
    private boolean isCoordinator;
    private static Map<String, String> usernameToIdMap = new HashMap<>(); // Map usernames to their IDs
    private String username; // Store the username
    private boolean isActive = true; // Track if the client is active

    public ClientHandler(Socket socket, Server server, String serverIp, int serverPort) {
        this.clientSocket = socket;
        this.server = server;
        this.isCoordinator = false;

        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to initialize streams for client.");
        }
    }

    // Generate a random 4-digit ID that is unique
    private String generateUniqueId() {
        Random random = new Random();
        String id;
        do {
            id = "#" + (1000 + random.nextInt(9000)); // Random number between 1000 and 9999
        } while (usernameToIdMap.containsValue(id)); // Ensure the ID is unique
        return id;
    }

    @Override
    public void run() {
        if (out == null || in == null) {
            System.err.println("Streams are not initialized for client.");
            return; // Exit the thread if streams are not initialized
        }

        try {
            // Read the username sent by the client
            username = in.readLine();
            if (username == null || username.trim().isEmpty()) {
                System.err.println("Username is empty. Disconnecting client.");
                return;
            }

            // Generate a new unique ID for the client
            clientId = generateUniqueId();
            usernameToIdMap.put(username, clientId); // Map the username to the unique ID

            System.out.println("Client " + username + clientId + " connected.");

            // Send the unique ID to the client
            out.println(clientId);

            // Notify the server to elect a coordinator (if none exists)
            server.electCoordinator(this);

            // Broadcast a message that the user has joined
            server.broadcast(username + clientId + " has joined the chat.");

            // Handle incoming messages
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.startsWith("/private")) {
                    // Handle private message
                    String[] parts = inputLine.split(" ", 3);
                    if (parts.length == 3) {
                        String recipientId = parts[1];
                        String message = parts[2];
                        server.sendPrivateMessage(username + clientId, recipientId, message);
                    }
                } else if (inputLine.equalsIgnoreCase("/requestDetails")) {
                    // Handle request for member details
                    server.requestMemberDetails(this);
                } else if (inputLine.equalsIgnoreCase("exit")) {
                    break;
                } else if (inputLine.equalsIgnoreCase("ping")) {
                    // Respond to ping from coordinator
                    out.println("pong");
                } else {
                    // Handle broadcast message
                    server.broadcast(username + clientId + ": " + inputLine);
                }
            }

            // Broadcast a message that the user has left
            server.broadcast(username + clientId + " has left the chat.");

            // Remove the client from the server's list
            server.removeClient(this);
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        } else {
            System.err.println("Output stream is null for client: " + username + clientId);
        }
    }

    public void sendMemberDetails(ClientHandler requester) {
        StringBuilder details = new StringBuilder("Member Details:\n");
        for (ClientHandler client : server.getClients()) {
            details.append("ID: ").append(client.getClientId())
                    .append(", IP: ").append(client.getClientSocket().getInetAddress().getHostAddress())
                    .append(", Port: ").append(client.getClientSocket().getPort())
                    .append("\n");
        }
        details.append("Current Coordinator: ").append(server.getCoordinator().getUsername() + server.getCoordinator().getClientId());
        requester.sendMessage(details.toString());
    }

    public String getClientId() {
        return clientId;
    }

    public String getUsername() {
        return username;
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public boolean isCoordinator() {
        return isCoordinator;
    }

    public void setCoordinator(boolean coordinator) {
        isCoordinator = coordinator;
        if (coordinator) {
            startCoordinatorChecks(); // Start periodic checks if this client is the coordinator
        }
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    private void startCoordinatorChecks() {
        new Thread(() -> {
            while (isCoordinator) {
                try {
                    Thread.sleep(20000); // Check every 20 seconds
                    System.out.println("Coordinator checking active members...");
                    for (ClientHandler client : server.getClients()) {
                        if (client != this) {
                            client.sendMessage("ping");
                            String response = client.in.readLine();
                            if (response == null || !response.equalsIgnoreCase("pong")) {
                                System.out.println("Client " + client.getUsername() + client.getClientId() + " is inactive.");
                                client.setActive(false);
                                server.removeClient(client);
                            }
                        }
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}