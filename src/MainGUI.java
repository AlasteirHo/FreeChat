import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.IOException;

public class MainGUI extends JFrame {
    private JTextField usernameField;
    private JTextField portField;
    private JTextField ipField;
    private JButton hostButton;
    private JButton joinButton;

    public MainGUI() {
        setTitle("Chat Application - Host or Join");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(5, 2));

        usernameField = new JTextField();
        portField = new JTextField();
        ipField = new JTextField();
        hostButton = new JButton("Host Server");
        joinButton = new JButton("Join Server");

        add(new JLabel("Username:"));
        add(usernameField);
        add(new JLabel("Port (for joining):"));
        add(portField);
        add(new JLabel("IP Address (for joining):"));
        add(ipField);
        add(hostButton);
        add(joinButton);

        hostButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                if (username == null || username.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(MainGUI.this, "Username cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Generate a random port between 10000 and 20000
                int port = new Random().nextInt(10000) + 10000;
                startServer(username, port);

                // Close the MainGUI after hosting the server
                dispose();
            }
        });

        joinButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                if (username == null || username.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(MainGUI.this, "Username cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String ip = ipField.getText().trim();
                String portText = portField.getText().trim();

                // Check if the IP address field is blank
                if (ip.isEmpty()) {
                    JOptionPane.showMessageDialog(MainGUI.this, "IP address cannot be blank.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Check if the port field is blank
                if (portText.isEmpty()) {
                    JOptionPane.showMessageDialog(MainGUI.this, "Port cannot be blank.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Check if the port is a valid integer
                int port;
                try {
                    port = Integer.parseInt(portText);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(MainGUI.this, "Invalid port number. Please enter a valid integer.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Check if the port is within a valid range (e.g., 1 to 65535)
                if (port < 1 || port > 65535) {
                    JOptionPane.showMessageDialog(MainGUI.this, "Invalid port number.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Attempt to join the server
                boolean success = joinServer(username, ip, port);

                // Only close the MainGUI if the connection was successful
                if (success) {
                    dispose();
                }
            }
        });
    }

    private void startServer(String username, int port) {
        // Start the server
        Server server = new Server(port);
        new Thread(server::start).start();

        // Start the client GUI
        SwingUtilities.invokeLater(() -> {
            ClientGUI clientGUI = new ClientGUI("localhost", port, username);
            clientGUI.setVisible(true);
        });

        JOptionPane.showMessageDialog(this, "Server started on port " + port);
    }

    private boolean joinServer(String username, String ip, int port) {
        try {
            // Attempt to connect to the server
            Socket testSocket = new Socket();
            testSocket.connect(new InetSocketAddress(ip, port), 3000); // 3-second timeout
            testSocket.close();

            // If the connection is successful, start the client GUI
            SwingUtilities.invokeLater(() -> {
                ClientGUI clientGUI = new ClientGUI(ip, port, username);
                clientGUI.setVisible(true);
            });

            return true; // Connection successful
        } catch (IOException e) {
            // Display an error message if the connection fails
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to connect to the server. Please check the IP and port.",
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return false; // Connection failed
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainGUI().setVisible(true);
            }
        });
    }
}