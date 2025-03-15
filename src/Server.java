import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private final ServerSocket serverSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private String currentCoordinator = null;
    private final ExecutorService clientThreadPool = Executors.newCachedThreadPool();
    private final Timer emptyServerTimer;
    private volatile boolean isRunning = false;
    private long shutdownTime = 0; // Time when shutdown will occur if clients remain empty
    private final int SHUTDOWN_DELAY_MS = 300000; // 5 minutes

    public Server(int port) throws IOException {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));

            emptyServerTimer = new Timer();
            emptyServerTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (clients.isEmpty()) {
                        if (shutdownTime == 0) {
                            // First time entering empty state, set shutdown time
                            shutdownTime = System.currentTimeMillis() + SHUTDOWN_DELAY_MS;
                            System.out.println("No clients connected. Server will shut down in " +
                                    (SHUTDOWN_DELAY_MS / 60000) + " minutes if no clients connect");
                        } else if (System.currentTimeMillis() >= shutdownTime) {
                            // Time's up, shutdown
                            shutdown();
                            emptyServerTimer.cancel();
                            System.exit(0);
                        } else {
                            // Still waiting, broadcast time remaining
                            broadcastShutdownWarning();
                        }
                    } else {
                        // Clients exist, reset shutdown time
                        if (shutdownTime != 0) {
                            shutdownTime = 0;
                            System.out.println("Shutdown timer cancelled - clients connected");
                        }
                    }
                }
            }, 10000, 10000); // Check every 10 seconds

            isRunning = true;
            System.out.println("Server successfully started on port " + port);
            startAcceptingClients();
        } catch (BindException e) {
            throw new IOException("Port " + port + " is already in use. Please try a different port.");
        } catch (SecurityException e) {
            throw new IOException("Security manager prevented use of port " + port);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid port number: " + port);
        } catch (IOException ex) {
            throw new IOException("Could not start server on port " + port + ": " + ex.getMessage());
        }
    }

    private void broadcastShutdownWarning() {
        if (shutdownTime > 0) {
            long timeRemaining = Math.max(0, shutdownTime - System.currentTimeMillis());
            int secondsRemaining = (int) (timeRemaining / 1000);
            int minutesRemaining = secondsRemaining / 60;
            secondsRemaining %= 60;

            String message = String.format("SERVER_TIMEOUT:%d:%d", minutesRemaining, secondsRemaining);
            broadcastMessage(message);
            System.out.println("Server will shut down in " + minutesRemaining + " minutes and " +
                    secondsRemaining + " seconds if no clients connect");
        }
    }

    private void startAcceptingClients() {
        Thread acceptThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    clientThreadPool.execute(handler);
                } catch (IOException ex) {
                    if (!serverSocket.isClosed()) {
                        System.err.println("Accept failed: " + ex.getMessage());
                    }
                }
            }
        });
        acceptThread.start();
    }

    public synchronized void registerClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
        // Reset shutdown timer when a client connects
        shutdownTime = 0;

        if (currentCoordinator == null) {
            setNewCoordinator(clientId);
        } else if (!clientId.equals(currentCoordinator)) {
            handler.sendMessage("COORDINATOR_INFO:" + currentCoordinator);
        }
        broadcastMessage("Member Joined:" + clientId);
        String memberList = getMemberList();
        broadcastMessage("MEMBER_LIST:" + memberList);
    }

    public synchronized void removeClient(String clientId) {
        clients.remove(clientId);
        System.out.println("Client removed from member list: " + clientId);
        if (clientId.equals(currentCoordinator)) {
            assignNewCoordinator();
        }
        broadcastMessage("Member Left:" + clientId);
        broadcastMemberList();

        // If this was the last client, start shutdown timer
        if (clients.isEmpty()) {
            shutdownTime = System.currentTimeMillis() + SHUTDOWN_DELAY_MS;
            System.out.println("No clients connected. Server will shut down in " + (SHUTDOWN_DELAY_MS / 60000) +
                    " minutes if no clients connect");
        }
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

            // Start the 5-minute shutdown timer for no coordinator
            if (shutdownTime == 0) {
                shutdownTime = System.currentTimeMillis() + SHUTDOWN_DELAY_MS;
                System.out.println("No coordinator available. Server will shut down in " +
                        (SHUTDOWN_DELAY_MS / 60000) + " minutes if no clients connect");
                // Don't immediately shut down - let the emptyServerTimer handle it
            }
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
        System.out.println(message);
        for (ClientHandler client : new ArrayList<>(clients.values())) {
            try {
                client.sendMessage(message);
            } catch (Exception ex) {
                System.err.println("Error broadcasting to client: " + ex.getMessage());
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
            String memberName = entry.getKey();
            details.append(String.format("%s%s:%s:%d",
                    memberName,
                    memberName.equals(currentCoordinator) ? " (Coordinator)" : "",
                    socket.getInetAddress().getHostAddress(),
                    socket.getPort()));
            first = false;
        }
        ClientHandler requester = clients.get(requestingClient);
        if (requester != null && !details.isEmpty()) {
            requester.sendMessage("MEMBER_DETAILS:" + details.toString());
        }
    }

    public String getMemberList() {
        return String.join(",", clients.keySet());
    }

    public boolean isClientCoordinator(String clientId) {
        return clientId != null && clientId.equals(currentCoordinator);
    }

    public void shutdown() {
        isRunning = false;
        try {
            // Notify all clients about shutdown
            broadcastMessage("SERVER_SHUTTING_DOWN");

            // Give clients a brief moment to receive the message
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Ignore
            }

            // Close all client connections
            for (ClientHandler client : new ArrayList<>(clients.values())) {
                try {
                    client.closeConnection();
                } catch (Exception ex) {
                    System.err.println("Error closing client connection: " + ex.getMessage());
                }
            }
            clients.clear();
            clientThreadPool.shutdownNow();
            if (emptyServerTimer != null) {
                emptyServerTimer.cancel();
            }
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
            System.out.println("Server shutdown complete");
        } catch (IOException ex) {
            System.err.println("Error during server shutdown: " + ex.getMessage());
        }
    }

    public boolean isRunning() {
        return isRunning && !serverSocket.isClosed();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Server <port>");
            System.exit(1);
        }

        try {
            int port = Integer.parseInt(args[0]);

            // Check port range
            if (port < 5000 || port > 65535) {
                System.err.println("Port must be between 5000 and 65535");
                System.exit(1);
            }

            System.out.println("Starting server on port " + port);
            Server server = new Server(port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                server.shutdown();
            }));

            while (server.isRunning()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    System.out.println("Server interrupted, shutting down...");
                    server.shutdown();
                    break;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number format");
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("Server error: " + ex.getMessage());
            System.exit(1);
        }
    }
}