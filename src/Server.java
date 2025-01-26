import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private int port;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    private ClientHandler coordinator;
    private String serverIp; // Store the server's IP address

    public Server(int port) {
        this.port = port;
        this.clients = new ArrayList<>();
        this.coordinator = null;
        try {
            this.serverIp = InetAddress.getLocalHost().getHostAddress(); // Get the server's IP address
        } catch (UnknownHostException e) {
            e.printStackTrace();
            this.serverIp = "Unknown";
        }
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started on IP: " + serverIp + ", Port: " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientIp = clientSocket.getInetAddress().getHostAddress();

                // Check if the client's IP matches the server's IP
                if (!clientIp.equals(serverIp)) {
                    System.out.println("Rejected connection from client with IP: " + clientIp);
                    clientSocket.close(); // Close the connection
                    continue; // Skip this client
                }

                System.out.println("New client connected: " + clientSocket);

                // Create a new ClientHandler for the connected client
                ClientHandler clientHandler = new ClientHandler(clientSocket, this, serverIp, port);
                clients.add(clientHandler); // Add the client to the list
                new Thread(clientHandler).start(); // Start the client handler in a new thread
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void electCoordinator(ClientHandler clientHandler) {
        if (coordinator == null) {
            coordinator = clientHandler;
            clientHandler.setCoordinator(true);
            System.out.println("Electing new coordinator: " + clientHandler.getUsername() + clientHandler.getClientId()); // Debug
            broadcast("New coordinator elected: " + clientHandler.getUsername() + clientHandler.getClientId());
        }
    }

    public void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public void sendPrivateMessage(String senderId, String recipientId, String message) {
        for (ClientHandler client : clients) {
            if (client.getClientId().equals(recipientId)) {
                client.sendMessage(senderId + " (private to " + recipientId + "): " + message);
                break;
            }
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        if (client.isCoordinator()) {
            electNewCoordinator();
        }
        broadcast("Client " + client.getUsername() + client.getClientId() + " has left the group.");
    }

    private void electNewCoordinator() {
        if (!clients.isEmpty()) {
            coordinator = clients.get(0); // Elect the first client as the new coordinator
            coordinator.setCoordinator(true);
            broadcast("New coordinator elected: " + coordinator.getUsername() + coordinator.getClientId());
        } else {
            coordinator = null;
            System.out.println("No clients left. Coordinator is null.");
        }
    }

    public void requestMemberDetails(ClientHandler requester) {
        if (coordinator != null) {
            coordinator.sendMemberDetails(requester);
        } else {
            requester.sendMessage("No coordinator available.");
        }
    }

    public List<ClientHandler> getClients() {
        return clients;
    }

    public ClientHandler getCoordinator() {
        return coordinator;
    }

    public static void main(String[] args) {
        Server server = new Server(5000);
        server.start();
    }
}