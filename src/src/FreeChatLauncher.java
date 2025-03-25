import javax.swing.*;
import java.io.IOException;
import java.net.*;
import java.util.Random;
import java.util.Enumeration;

public class FreeChatLauncher extends JFrame {

    // Flag for simulating faults (can be toggled by tests)
    public static boolean faultInjection = false;

    private JButton hostServerButton;
    private JButton joinServerButton;
    private JPanel mainPanel;

    public FreeChatLauncher() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Failed to set system look and feel: " + e.getMessage());
        }

        setTitle("FreeChat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(mainPanel);

        pack();
        setLocationRelativeTo(null);

        hostServerButton.addActionListener(_ -> hostServerWithAutomaticPort());

        joinServerButton.addActionListener(_ -> {
            dispose();
            SwingUtilities.invokeLater(() -> {
                ChatClientGUI client = new ChatClientGUI();
                client.showLoginPanel();
                client.setVisible(true);
            });
        });
        try {
            ImageIcon icon = new ImageIcon("freechat_icon.png");
            setIconImage(icon.getImage());
        } catch (Exception e) {
            System.err.println("Failed to load icon: " + e.getMessage());
        }
    }

    public static void showMainDialog() {
        FreeChatLauncher dialog = new FreeChatLauncher();
        dialog.setVisible(true);
    }

    private void hostServerWithAutomaticPort() {
        try {
            // Simulate a fault if faultInjection is enabled
            if (faultInjection) {
                throw new IOException("Injected fault: simulated server start failure");
            }

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

            // Get the local IPv4 address
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
            builder.start();

            // Display the port assignment dialog with IP address
            JOptionPane.showMessageDialog(
                    this,
                    "Server started on:\nIP: " + localIP + "\nPort: " + port,
                    "Automatic Port Assignment",
                    JOptionPane.INFORMATION_MESSAGE
            );

            // Proceed to the client
            dispose();
            SwingUtilities.invokeLater(() -> {
                ChatClientGUI client = new ChatClientGUI();

                // Set the pre-filled values directly
                client.prefillConnectionFields("localhost", String.valueOf(port));
                client.showLoginPanel();
                client.setVisible(true);
            });

        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Error starting server: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // Modified port finding to support fault injection
    public static int findRandomAvailablePort() {
        if (faultInjection) return -1; // Simulate fault condition

        Random random = new Random();
        int maxAttempts = 20;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int port = random.nextInt(5000, 65535); // Port between 5000 and 65535
            try (ServerSocket _ = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                // Port is not available, try another
                System.out.println("Could not find available port after several attempts, try again later");
            }
        }
        return -1;
    }


     // Gets the local IP address of the machine on the network instead of Localhost/127.0.0.1
    private String getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            System.err.println("Error getting local IP address: " + e.getMessage());
            return "127.0.0.1";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FreeChatLauncher::showMainDialog);
    }
}
