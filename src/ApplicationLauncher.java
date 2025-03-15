import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.*;
import java.util.Random;
import java.util.Enumeration;

public class ApplicationLauncher extends JFrame {
    private JButton hostServerButton;
    private JButton joinServerButton;
    private JPanel mainPanel;

    public ApplicationLauncher() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ei) {
            ei.printStackTrace();
        }

        setTitle("Chat application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(mainPanel);

        // Fix for the focus border
        hostServerButton.setFocusPainted(false);
        joinServerButton.setFocusPainted(false);

        pack();
        setLocationRelativeTo(null);

        hostServerButton.addActionListener(e -> hostServerWithAutomaticPort());

        joinServerButton.addActionListener(e -> {
            dispose();
            SwingUtilities.invokeLater(() -> {
                ChatClientGUI client = new ChatClientGUI();
                client.showLoginPanel();
                client.setVisible(true);
            });
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> showMainDialog());
    }

    public static void showMainDialog() {
        ApplicationLauncher dialog = new ApplicationLauncher();
        dialog.setVisible(true);
    }

    private void hostServerWithAutomaticPort() {
        try {
            // Find an available port randomly
            int port = findRandomAvailablePort();

            if (port == -1) {
                JOptionPane.showMessageDialog(
                        this,
                        "Could not find available port after several attempts.",
                        "Port Error",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // Get the local IP address
            String localIP = getLocalIPAddress();

            // Start the server process
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + "/bin/java";
            String classpath = System.getProperty("java.class.path");
            String className = Server.class.getCanonicalName();

            ProcessBuilder builder = new ProcessBuilder(
                    javaBin,
                    "-cp",
                    classpath,
                    className,
                    String.valueOf(port)
            );

            builder.inheritIO();
            Process serverProcess = builder.start();

            // Wait a moment for the server to start
            Thread.sleep(1000);

            // Display the port assignment dialog with IP address
            JOptionPane.showMessageDialog(
                    this,
                    "Server started on:\nIP: " + localIP + "\nPort: " + port,
                    "Automatic Port Assignment",
                    JOptionPane.INFORMATION_MESSAGE
            );

            // Only after the user clicks OK, proceed to the client
            dispose();
            SwingUtilities.invokeLater(() -> {
                ChatClientGUI client = new ChatClientGUI();

                // Set the server process
                client.setServerProcess(serverProcess);

                // Pre-fill the connection fields with proper values
                try {
                    // Pre-fill the port field
                    java.lang.reflect.Field portField = ChatClientGUI.class.getDeclaredField("portField");
                    portField.setAccessible(true);
                    JTextField portTextField = (JTextField) portField.get(client);
                    portTextField.setText(String.valueOf(port));

                    // Pre-fill the IP field with localhost or actual IP
                    java.lang.reflect.Field serverIpField = ChatClientGUI.class.getDeclaredField("serverIpField");
                    serverIpField.setAccessible(true);
                    JTextField ipTextField = (JTextField) serverIpField.get(client);
                    ipTextField.setText("localhost"); // Use localhost for self-connections
                } catch (Exception ex) {
                    System.err.println("Could not set connection fields: " + ex.getMessage());
                }

                client.showLoginPanel();
                client.setVisible(true);
            });

        } catch (Exception ie) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error starting server: " + ie.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static int findRandomAvailablePort() {
        Random random = new Random();
        // Maximum number of attempts to find an available port
        int maxAttempts = 20;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Generate a random port between 5000 and 65535
            int port = 5000 + random.nextInt(60536); // 65535 - 5000 + 1 = 60536

            try (ServerSocket socket = new ServerSocket(port)) {
                // If we get here, the port is available
                return port;
            } catch (IOException e) {
                // Port is not available, try another one
                continue;
            }
        }

        // If we couldn't find a port after several attempts, return -1
        return -1;
    }

    /**
     * Gets the local IP address of the machine.
     * Prefers non-loopback addresses.
     *
     * @return The local IP address as a string
     */
    private String getLocalIPAddress() {
        try {
            // Try to get the non-loopback address
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                // Skip loopback, inactive, or virtual interfaces
                if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // We prefer IPv4 addresses
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }

            // Fallback to localhost if no other address is found
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            // In case of any errors, return a sensible default
            return "127.0.0.1";
        }
    }
}