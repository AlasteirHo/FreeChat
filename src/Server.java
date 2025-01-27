import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private int port;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    private ClientHandler coordinator;
    private String serverIpLocal; // Localhost IP (127.0.0.1)
    private String serverIpNetwork; // Network IP (e.g., 192.168.x.x)

    public Server(int port) {
        this.port = port;
        this.clients = new ArrayList<>();
        this.coordinator = null;
        try {
            // Get localhost IP
            this.serverIpLocal = "127.0.0.1";

            // Get network IP
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue; // Skip loopback and inactive interfaces

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) { // Only consider IPv4 addresses
                        this.serverIpNetwork = addr.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
            this.serverIpLocal = "127.0.0.1";
            this.serverIpNetwork = "Unknown";
        }
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started on IPs: " + serverIpLocal + " and " + serverIpNetwork + ", Port: " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientIp = clientSocket.getInetAddress().getHostAddress();

                // Allow localhost (127.0.0.1), the server's own IP, and same network IPs
                if (clientIp.equals("127.0.0.1") || clientIp.equals(serverIpNetwork) || clientIp.startsWith("192.168.")) {
                    System.out.println("New client connected: " + clientSocket);

                    ClientHandler clientHandler = new ClientHandler(clientSocket, this, serverIpNetwork, port);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();
                } else {
                    System.out.println("Rejected connection from client with IP: " + clientIp);
                    clientSocket.close(); // Close the connection
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