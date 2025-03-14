import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.ServerSocket;

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
        pack();
        setLocationRelativeTo(null);

        hostServerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hostServerWithAutomaticPort();
            }
        });

        joinServerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                SwingUtilities.invokeLater(() -> {
                    ChatClientGUI client = new ChatClientGUI();
                    client.showLoginPanel();
                    client.setVisible(true);
                });
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            showMainDialog();
        });
    }

    public static void showMainDialog() {
        ApplicationLauncher dialog = new ApplicationLauncher();
        dialog.setVisible(true);
    }

    private void hostServerWithAutomaticPort() {
        try {
            // Find an available port automatically
            int port = findAvailablePort();

            // Display the auto assigned port
            JOptionPane.showMessageDialog(
                    this,
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
            dispose();
            SwingUtilities.invokeLater(() -> {
                ChatClientGUI client = new ChatClientGUI();
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

    private static int findAvailablePort() {
        // Starts the program with a valid port, starting from 5000.....
        int startPort = 5000;
        int maxPort = 65535;

        for (int port = startPort; port <= maxPort; port++) {
            try (ServerSocket _ = new ServerSocket(port)) {
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