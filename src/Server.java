import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private int port;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    private ClientHandler coordinator;

    public Server(int port) {
        this.port = port;
        this.clients = new ArrayList<>();
        this.coordinator = null; // Initialize coordinator to null
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);

                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clients.add(clientHandler);
                new Thread(clientHandler).start();

                // Elect a coordinator if none exists
                if (coordinator == null) {
                    electCoordinator(clientHandler);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void electCoordinator(ClientHandler clientHandler) {
        if (coordinator == null) {
            coordinator = clientHandler;
            clientHandler.setCoordinator(true);
            System.out.println("Electing new coordinator: " + clientHandler.getUsername() + clientHandler.getClientId());
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
                client.sendMessage(senderId + " (sent privately to you): " + message);
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

        // Close the client socket and clean up resources
        try {
            client.getClientSocket().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void electNewCoordinator() {
        if (!clients.isEmpty()) {
            // Elect the first client in the list as the new coordinator
            coordinator = clients.get(0);
            coordinator.setCoordinator(true);

            // Ensure that the username and clientId are not null before broadcasting
            String coordinatorUsername = coordinator.getUsername();
            String coordinatorClientId = coordinator.getClientId();

            if (coordinatorUsername != null && coordinatorClientId != null) {
                System.out.println("New coordinator elected: " + coordinatorUsername + coordinatorClientId);
                broadcast("New coordinator elected: " + coordinatorUsername + coordinatorClientId);
            } else {
                System.out.println("New coordinator elected, but username or clientId is null.");
            }
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