import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    // Flag to indicate test mode; set to true during testing to avoid System.exit(0)
    public static boolean testMode = false;

    private final ServerSocket serverSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private String currentCoordinator = null;
    private final ExecutorService clientThreadPool = Executors.newCachedThreadPool();
    private volatile boolean isRunning;

    // Simplified inactive members tracking
    private final Set<String> inactiveMembers = ConcurrentHashMap.newKeySet();

    // Shutdown countdown thread
    private Thread shutdownThread = null;

    public Server(int port) throws IOException {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));

            isRunning = true;
            System.out.println("Server successfully started on port " + port);

            // Start the shutdown countdown on startup
            startShutdownCountdown();

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

    private synchronized void startShutdownCountdown() {
        if (shutdownThread != null && shutdownThread.isAlive()) {
            return;
        }

        shutdownThread = new Thread(() -> {
            try {
                System.out.println("Server will shut down in 5 minutes if no clients connect");
                broadcastMessage("SERVER_TIMEOUT:5:0");
                for (int remainingSeconds = 270; remainingSeconds > 0; remainingSeconds -= 30) {
                    Thread.sleep(30000);
                    if (!clients.isEmpty()) {
                        System.out.println("Clients connected. Cancelling shutdown timer.");
                        return;
                    }
                    int minutes = remainingSeconds / 60;
                    int seconds = remainingSeconds % 60;
                    System.out.println("Server will shut down in " + minutes + " minutes and " + seconds + " seconds if no clients connect");
                    broadcastMessage(String.format("SERVER_TIMEOUT:%d:%d", minutes, seconds));
                }
                System.out.println("Shutdown time reached with no clients. Shutting down server.");
                shutdown();
            } catch (InterruptedException e) {
                System.out.println("Shutdown countdown interrupted.");
            }
        });
        shutdownThread.setDaemon(true);
        shutdownThread.setName("ShutdownThread");
        shutdownThread.start();
    }

    private synchronized void cancelShutdownCountdown() {
        if (shutdownThread != null && shutdownThread.isAlive()) {
            shutdownThread.interrupt();
            shutdownThread = null;
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
        // Clean the client ID and remove from inactive members if rejoining
        String cleanId = cleanMemberName(clientId);
        inactiveMembers.remove(cleanId);

        // Cancel shutdown countdown when a client connects
        cancelShutdownCountdown();

        if (currentCoordinator == null) {
            setNewCoordinator(clientId);
        } else if (!clientId.equals(currentCoordinator)) {
            handler.sendMessage("COORDINATOR_INFO:" + currentCoordinator);
        }
        broadcastMessage("Member Joined:" + clientId);
        updateMemberLists();
    }

    public synchronized void removeClient(String clientId) {
        clients.remove(clientId);
        System.out.println("Client removed from member list: " + clientId);

        String cleanId = cleanMemberName(clientId);
        if (!cleanId.isEmpty()) {
            inactiveMembers.add(cleanId);
            System.out.println("Added to inactive members list: " + cleanId);
        }

        if (clientId.equals(currentCoordinator)) {
            assignNewCoordinator();
        } else {
            broadcastMessage("Member Left:" + clientId);
            updateMemberLists();
            if (clients.isEmpty()) {
                startShutdownCountdown();
            }
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
            broadcastMessage("Member Left:" + currentCoordinator);
            updateMemberLists();
            startShutdownCountdown();
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
        boolean suppressConsoleOutput = message.startsWith("SERVER_TIMEOUT:") ||
                message.startsWith("MEMBER_LIST:") ||
                message.startsWith("INACTIVE_MEMBER_LIST:");
        if (!suppressConsoleOutput) {
            System.out.println(message);
        }
        for (ClientHandler client : new ArrayList<>(clients.values())) {
            try {
                client.sendMessage(message);
            } catch (Exception ex) {
                System.err.println("Error broadcasting to client: " + ex.getMessage());
            }
        }
    }

    private void updateMemberLists() {
        String memberList = getMemberList();
        broadcastMessage("MEMBER_LIST:" + memberList);
        String inactiveMemberList = getInactiveMemberList();
        broadcastMessage("INACTIVE_MEMBER_LIST:" + inactiveMemberList);
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
        if (requester != null && details.length() > 0) {
            requester.sendMessage("MEMBER_DETAILS:" + details.toString());
        }
    }

    public String getMemberList() {
        return String.join(",", clients.keySet());
    }

    public String getInactiveMemberList() {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        List<String> sortedMembers = new ArrayList<>(inactiveMembers);
        Collections.sort(sortedMembers);
        for (String member : sortedMembers) {
            if (clients.containsKey(member)) continue;
            if (!member.isEmpty()) {
                if (!first) {
                    builder.append(",");
                } else {
                    first = false;
                }
                builder.append(member);
            }
        }
        return builder.toString();
    }

    public boolean isClientCoordinator(String clientId) {
        return clientId != null && clientId.equals(currentCoordinator);
    }

    public synchronized void shutdown() {
        if (!isRunning) return;
        isRunning = false;
        try {
            broadcastMessage("SERVER_SHUTTING_DOWN");
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {}
            for (ClientHandler client : new ArrayList<>(clients.values())) {
                try {
                    client.closeConnection();
                } catch (Exception ex) {
                    System.err.println("Error closing client connection: " + ex.getMessage());
                }
            }
            clients.clear();
            clientThreadPool.shutdownNow();
            cancelShutdownCountdown();
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
            System.out.println("Server shutdown complete");
            if (!testMode && !(Thread.currentThread().getName().contains("Shutdown") ||
                    Thread.currentThread().getName().equals("ShutdownThread"))) {
                System.exit(0);
            }
        } catch (IOException ex) {
            System.err.println("Error during server shutdown: " + ex.getMessage());
        }
    }

    public boolean isRunning() {
        return isRunning && !serverSocket.isClosed();
    }

    private String cleanMemberName(String memberName) {
        if (memberName == null || memberName.trim().isEmpty()) {
            return "";
        }
        return memberName.trim().replace(":", "");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Server <port>");
            System.exit(1);
        }
        try {
            int port = Integer.parseInt(args[0]);
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
