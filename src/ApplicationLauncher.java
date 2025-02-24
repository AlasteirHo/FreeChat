import javax.swing.*;
import java.io.IOException;
import java.net.ServerSocket;

public class ApplicationLauncher {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ei) {
            ei.printStackTrace();
        }

        showMainDialog();
    }

    public static void showMainDialog() {
        Object[] options = {"Host Server", "Join Server"};
        int choice = JOptionPane.showOptionDialog(null,
                "Choose an option",
                "Chat application",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == 0) { // Host Server
            hostServerWithAutomaticPort();
        } else if (choice == 1) { // Join Server
            SwingUtilities.invokeLater(() -> {
                ChatClientGUI client = new ChatClientGUI();
                client.showLoginPanel();
                client.setVisible(true);
            });
        }
    }

    private static void hostServerWithAutomaticPort() {
        try {
            // Find an available port automatically
            int port = findAvailablePort();

            // Display the auto assigned port
            JOptionPane.showMessageDialog(
                    null,
                    "Server will start on port: " + port,
                    "Automatic Port Assignment",
                    JOptionPane.INFORMATION_MESSAGE
            );

            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + "/bin/java";
            String classpath = System.getProperty("java.class.path");
            String className = ServerLauncher.class.getCanonicalName();

            ProcessBuilder builder = new ProcessBuilder(
                    javaBin,
                    "-cp",
                    classpath,
                    className,
                    String.valueOf(port)
            );

            builder.inheritIO();
            builder.start();

            Thread.sleep(1000);
            SwingUtilities.invokeLater(() -> {
                ChatClientGUI client = new ChatClientGUI();
                client.showLoginPanel();
                client.setVisible(true);
            });

        } catch (Exception ie) {
            JOptionPane.showMessageDialog(
                    null,
                    "Error starting server: " + ie.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            showMainDialog();
        }
    }

    private static int findAvailablePort() {
        // Starts the program with a valid port, starting from 5000.....
        int startPort = 5000;
        int maxPort = 65535;

        for (int port = startPort; port <= maxPort; port++) {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                // If we get here, the port is available
                return port;
            } catch (IOException e) {
                // Port is not available, try the next one
                continue;
            }
        }
        return -1;
    }
}