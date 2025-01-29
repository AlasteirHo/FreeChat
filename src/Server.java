import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int DEFAULT_PORT = 5000;
    private final ServerSocket serverSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private String currentCoordinator = null;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean running = true;
    private final ExecutorService clientThreadPool = Executors.newCachedThreadPool();

    public Server(int port) throws IOException {
        if (port < 1024 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1024 and 65535");
        }

        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));
            System.out.println("Server successfully started on port " + port);
            startAcceptingClients();
        } catch (BindException e) {
            throw new IOException("Port " + port + " is already in use. Please try a different port.");
        } catch (SecurityException e) {
            throw new IOException("Security manager prevented use of port " + port);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid port number: " + port);
        } catch (IOException e) {
            throw new IOException("Could not start server on port " + port + ": " + e.getMessage());
        }
    }

    private void startAcceptingClients() {
        Thread acceptThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    clientThreadPool.execute(handler);
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        System.err.println("Accept failed: " + e.getMessage());
                    }
                }
            }
        });
        acceptThread.start();
    }

    public synchronized void registerClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
        // Send coordinator info only once during registration
        if (currentCoordinator == null) {
            setNewCoordinator(clientId);
        } else if (!clientId.equals(currentCoordinator)) {
            handler.sendMessage("COORDINATOR_INFO:" + currentCoordinator);
        }
        // Separate member join broadcast
        broadcastMessage("MEMBER_JOIN:" + clientId);
        // Only send member list without coordinator info
        String memberList = getMemberList();
        broadcastMessage("MEMBER_LIST:" + memberList);
    }
    public synchronized void removeClient(String clientId) {
        clients.remove(clientId);
        System.out.println("Client removed: " + clientId);
        if (clientId.equals(currentCoordinator)) {
            assignNewCoordinator();
        }
        broadcastMessage("MEMBER_LEAVE:" + clientId);
        broadcastMemberList();
    }

    private synchronized void assignNewCoordinator() {
        if (!clients.isEmpty()) {
            String newCoordinator = clients.keySet().iterator().next();
            if (!newCoordinator.equals(currentCoordinator)) {
                setNewCoordinator(newCoordinator);
                System.out.println("New coordinator assigned: " + newCoordinator);
                for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                    if (!entry.getKey().equals(newCoordinator)) {
                        entry.getValue().sendMessage("COORDINATOR_INFO:" + newCoordinator);
                    }
                }
            }
        } else {
            currentCoordinator = null;
            System.out.println("No clients available for coordinator role");
        }
    }

    private void setNewCoordinator(String clientId) {
        if (currentCoordinator != null && currentCoordinator.equals(clientId)) {
            return;
        }
        currentCoordinator = clientId;
        ClientHandler coordinator = clients.get(clientId);
        if (coordinator != null) {
            coordinator.sendMessage("COORDINATOR_STATUS:You are now the coordinator");
        }
    }

    public void broadcastMessage(String message) {
        System.out.println("Broadcasting: " + message);
        for (ClientHandler client : new ArrayList<>(clients.values())) {
            try {
                client.sendMessage(message);
            } catch (Exception e) {
                System.err.println("Error broadcasting to client: " + e.getMessage());
            }
        }
    }

    private void broadcastMemberList() {
        String memberList = getMemberList();
        broadcastMessage("MEMBER_LIST:" + memberList);
    }

    public void sendPrivateMessage(String from, String to, String message) {
        ClientHandler recipient = clients.get(to);
        if (recipient != null) {
            recipient.sendMessage("PRIVATE_MSG:" + from + ":" + message);
        }
    }

    public void sendMemberDetails(String requestingClient) {
        StringBuilder details = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!first) {
                details.append(",");
            }
            ClientHandler handler = entry.getValue();
            Socket socket = handler.getSocket();
            details.append(String.format("%s:%s:%d",
                    entry.getKey(),
                    socket.getInetAddress().getHostAddress(),
                    socket.getPort()));
            first = false;
        }
        ClientHandler requester = clients.get(requestingClient);
        if (requester != null && details.length() > 0) {
            requester.sendMessage("MEMBER_DETAILS:" + details.toString());
        }
    }

    public String getMemberList() {
        return String.join(",", clients.keySet());
    }

    public String getCurrentCoordinator() {
        return currentCoordinator;
    }

    public void shutdown() {
        try {
            running = false;
            for (ClientHandler client : new ArrayList<>(clients.values())) {
                try {
                    client.closeConnection();
                } catch (Exception e) {
                    System.err.println("Error closing client connection: " + e.getMessage());
                }
            }
            clients.clear();
            clientThreadPool.shutdownNow();
            scheduler.shutdownNow();
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
            System.out.println("Server shutdown complete");
        } catch (IOException e) {
            System.err.println("Error during server shutdown: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return !serverSocket.isClosed();
    }
}
