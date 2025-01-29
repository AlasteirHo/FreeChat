import java.io.IOException;

public class ServerLauncher {
    private final Server server;
    private volatile boolean isRunning = false;

    public ServerLauncher(int port) {
        try {
            server = new Server(port);
            isRunning = true;
            System.out.println("Server successfully started on port " + port);
        } catch (IOException e) {
            String errorMessage = e.getMessage();
            System.err.println("Server startup error: " + errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void shutdown() {
        if (server != null) {
            server.shutdown();
        }
        isRunning = false;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java ServerLauncher <port>");
            System.exit(1);
        }

        try {
            int port = Integer.parseInt(args[0]);

            // Check port range
            if (port < 1024 || port > 65535) {
                System.err.println("Port must be between 1024 and 65535");
                System.exit(1);
            }

            System.out.println("Starting server on port " + port);
            ServerLauncher launcher = new ServerLauncher(port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                launcher.shutdown();
            }));

            while (launcher.isRunning()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    System.out.println("Server interrupted, shutting down...");
                    launcher.shutdown();
                    break;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number format");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            System.exit(1);
        }
    }
}