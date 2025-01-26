import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Server server;
    private PrintWriter out;
    private BufferedReader in;
    private String clientId;
    private boolean isCoordinator;
    private static final Map<String, Integer> usernameToIdMap = new HashMap<>(); // Map usernames to their IDs
    private String username; // Store the username

    public ClientHandler(Socket socket, Server server) {
        this.clientSocket = socket;
        this.server = server;
        this.isCoordinator = false;

        try {
            // Initialize the output stream
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            // Initialize the input stream
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to initialize streams for client.");
        }
    }

    // Generate a random 4-digit ID that is unique
    private int generateRandom4DigitId() {
        Random random = new Random();
        int id;
        do {
            id = 1000 + random.nextInt(9000); // Random number between 1000 and 9999
        } while (usernameToIdMap.containsValue(id)); // Ensure the ID is unique
        return id;
    }

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

            // Check if the username already has an assigned ID
            if (usernameToIdMap.containsKey(username)) {
                // Reuse the existing ID for the username
                clientId = "#" + usernameToIdMap.get(username);
            } else {
                // Generate a new random 4-digit ID for the username
                int newId = generateRandom4DigitId();
                usernameToIdMap.put(username, newId);
                clientId = "#" + newId;
            }

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
                        // Pass the sender's ID, recipient's ID, and message to the server
                        server.sendPrivateMessage(username + clientId, recipientId, message);
                    }
                } else if (inputLine.equalsIgnoreCase("/requestDetails")) {
                    // Handle request for member details
                    server.requestMemberDetails(this);
                } else if (inputLine.equalsIgnoreCase("exit")) {
                    break;
                } else {
                    // Handle broadcast message in the format Username#ID: message
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
            // Skip clients with null IDs or usernames
            if (client.getClientId() != null && client.getUsername() != null) {
                details.append("ID: ").append(client.getClientId())
                        .append(", IP: ").append(client.getClientSocket().getInetAddress().getHostAddress())
                        .append(", Port: ").append(client.getClientSocket().getPort())
                        .append("\n");
            }
        }
        details.append("Current Coordinator: ").append(server.getCoordinator().getUsername()).append(server.getCoordinator().getClientId());
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
    }
}