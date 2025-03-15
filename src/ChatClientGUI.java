import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.Random;
import javax.swing.border.EmptyBorder;

public class ChatClientGUI extends JFrame {
    // Main panels
    private JPanel mainPanel;
    private JPanel loginPanel;
    private JPanel chatPanel;

    // Login panel components
    private JTextField usernameField;
    private JTextField serverIpField;
    private JTextField portField;
    private JButton connectButton;
    private JButton cancelButton;

    // Chat panel components
    private JPanel statusPanel;
    private JPanel coordinatorPanel;
    private JPanel serverInfoPanel;
    private JLabel statusLabel;
    private JLabel serverInfoLabel;
    private JLabel serverTimeoutLabel; // Label for server timeout
    private JTextArea chatArea;
    private JPanel bottomPanel;
    private JComboBox<String> recipientBox;
    private JTextField messageField;
    private JButton sendButton;
    private JButton getMembersButton;
    private JButton quitButton;

    // Connection variables
    private PrintWriter out;
    private String clientId;
    private boolean isCoordinator = false;
    private Timer tickerTimer;
    private Timer memberUpdateTimer;
    private Socket socket;
    private volatile boolean connected = false;
    private String serverIP;
    private int serverPort;

    // Server process (only set when this client is hosting the server)
    private Process serverProcess = null;

    public ChatClientGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        setTitle("Chat System");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setContentPane(mainPanel);

        // Initially smaller size for login screen
        setSize(400, 500);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleClosing();
            }
        });

        // Apply border to chat scroll pane programmatically to avoid form loading errors
        JScrollPane scrollPane = null;
        for (Component comp : chatPanel.getComponents()) {
            if (comp instanceof JScrollPane) {
                scrollPane = (JScrollPane) comp;
                break;
            }
        }

        if (scrollPane != null) {
            scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        }

        // Add server timeout label
        serverTimeoutLabel = new JLabel();
        serverTimeoutLabel.setForeground(Color.RED);
        serverTimeoutLabel.setVisible(false);
        serverTimeoutLabel.setBorder(new EmptyBorder(0, 5, 0, 0));

        // Add it to the serverInfoPanel (assuming this panel exists in your form)
        if (serverInfoPanel != null) {
            serverInfoPanel.add(serverTimeoutLabel);
        }

        // Set up login panel actions
        setupLoginPanel();

        // Set up chat panel actions
        setupChatPanel();

        // Initialize timers
        tickerTimer = new Timer(20000, e -> {
            if (isCoordinator && isConnected()) {
                sendMessage("Ticker");
            }
        });

        memberUpdateTimer = new Timer(5000, e -> {
            if (isConnected()) {
                sendMessage("GET_MEMBERS");
            }
        });
    }

    private void setupLoginPanel() {
        connectButton.addActionListener(e -> {
            try {
                String username = usernameField.getText().trim();
                if (username.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please enter a username");
                    return;
                }

                clientId = username + "#" + String.format("%04d", new Random().nextInt(10000));
                if (!isConnected()) {
                    connectToServer(
                            serverIpField.getText(),
                            Integer.parseInt(portField.getText())
                    );
                }

                if (isConnected()) {
                    sendMessage("CONNECT:" + clientId);
                    CardLayout cl = (CardLayout) mainPanel.getLayout();
                    cl.show(mainPanel, "chat");
                    setTitle("Chat Client - " + clientId);

                    // Resize window to larger size for chat panel
                    setSize(600, 650);
                    setLocationRelativeTo(null); // Re-center with new size
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Invalid port number",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        cancelButton.addActionListener(e -> {
            dispose(); // Close current window
            // Show the main application dialog again
            SwingUtilities.invokeLater(() -> {
                ApplicationLauncher.showMainDialog();
            });
        });
    }

    private void setupChatPanel() {
        if (sendButton != null) {
            sendButton.addActionListener(e -> sendChatMessage());
        }

        if (messageField != null) {
            messageField.addActionListener(e -> sendChatMessage());
        }

        if (getMembersButton != null) {
            getMembersButton.addActionListener(e -> {
                if (isConnected() && chatArea != null) {
                    sendMessage("REQUEST_DETAILS");
                    chatArea.append("\n----- Member Details -----\n");
                }
            });
        }

        if (quitButton != null) {
            quitButton.addActionListener(e -> handleClosing());
        }
    }

    private void sendChatMessage() {
        if (!isConnected()) return;

        if (recipientBox == null || messageField == null || chatArea == null) return;

        String recipient = (String) recipientBox.getSelectedItem();
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        if (recipient.equals("All Chat")) {
            sendMessage("BROADCAST:" + message);
        } else {
            sendMessage("PRIVATE:" + recipient + ":" + message);
            chatArea.append("Private to " + recipient + ": " + message + "\n");
        }
        messageField.setText("");
    }

    private void handleClosing() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit?",
                "Exit Confirmation",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            // When the host leaves, just let the server continue running
            // The server's existing logic will assign a new coordinator

            // Perform cleanup to properly disconnect from the server
            cleanup();
            System.exit(0);
        }
    }

    private void cleanup() {
        connected = false;
        if (tickerTimer != null) {
            tickerTimer.stop();
        }
        if (memberUpdateTimer != null) {
            memberUpdateTimer.stop();
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connectToServer(String host, int port) {
        try {
            socket = new Socket();
            // Set connection timeout to 3 seconds
            socket.connect(new InetSocketAddress(host, port), 3000);
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            // Get actual IP instead of localhost
            this.serverIP = InetAddress.getLocalHost().getHostAddress();
            this.serverPort = port;
            if (serverInfoLabel != null) {
                if (host.equals("localhost") || host.equals("127.0.0.1")) {
                    // For localhost connections, try to get the actual machine IP
                    serverInfoLabel.setText(String.format("Server: %s:%d", this.serverIP, port));
                } else {
                    // For external connections, use the provided host
                    serverInfoLabel.setText(String.format("Server: %s:%d", host, port));
                }
            }

            memberUpdateTimer.start();
            new Thread(() -> receiveMessages(socket)).start();
        } catch (ConnectException ce) {
            JOptionPane.showMessageDialog(this,
                    "No server found on " + host + ":" + port + ".\nPlease check the port number and verify that the server is running.",
                    "Server Not Found",
                    JOptionPane.ERROR_MESSAGE);
        } catch (SocketTimeoutException ste) {
            JOptionPane.showMessageDialog(this,
                    "Connection to server timed out.\nPlease check the IP address and ensure the server is running.",
                    "Connection Timeout",
                    JOptionPane.ERROR_MESSAGE);
        } catch (UnknownHostException uhe) {
            JOptionPane.showMessageDialog(this,
                    "Unknown host: " + host + "\nPlease check the IP address or hostname.",
                    "Invalid Host",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            String errorMsg = ex.getMessage();
            // Provide a more user-friendly message
            JOptionPane.showMessageDialog(this,
                    "Unable to connect to the server: " + host + ":" + port + "\n" +
                            "Reason: " + (errorMsg != null ? errorMsg : "Unknown error") +
                            "\n\nPlease verify that the server is running and that you have network connectivity.",
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void receiveMessages(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );
            String message;
            while (connected && (message = in.readLine()) != null) {
                final String msg = message;
                SwingUtilities.invokeLater(() -> handleMessage(msg));
            }
        } catch (IOException ex) {
            if (connected) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            this,
                            "Lost connection to server: " + ex.getMessage(),
                            "Connection Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    cleanup();
                    if (mainPanel != null) {
                        CardLayout cl = (CardLayout) mainPanel.getLayout();
                        cl.show(mainPanel, "login");
                    }
                    // Resize back to login size
                    setSize(400, 500);
                    setLocationRelativeTo(null);
                });
            }
        }
    }

    private void handleMessage(String message) {
        if (message.startsWith("COORDINATOR_STATUS:") && !isCoordinator) {
            isCoordinator = true;
            if (statusLabel != null) {
                statusLabel.setText("Status: Coordinator");
                statusLabel.setForeground(Color.RED);
            }
            if (chatArea != null) {
                chatArea.append("You are now the coordinator\n");
            }
            tickerTimer.start();
        } else if (message.startsWith("COORDINATOR_INFO:")) {
            isCoordinator = false;
            String coordinatorId = message.substring(16);
            if (statusLabel != null) {
                statusLabel.setText("Status: Member");
                statusLabel.setForeground(Color.BLUE);
            }
            if (chatArea != null) {
                chatArea.append("Current coordinator is: " + coordinatorId + "\n");
            }
            tickerTimer.stop();
        } else if (message.startsWith("SERVER_TIMEOUT:")) {
            // Handle server timeout message format: SERVER_TIMEOUT:minutes:seconds
            String[] parts = message.substring(14).split(":");
            if (parts.length == 2) {
                try {
                    int minutes = Integer.parseInt(parts[0]);
                    int seconds = Integer.parseInt(parts[1]);
                    updateServerTimeoutLabel(minutes, seconds);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        } else if (message.startsWith("MEMBER_LIST:")) {
            String[] members = message.substring(12).split(",");
            String selectedRecipient = null;

            if (recipientBox != null) {
                selectedRecipient = (String) recipientBox.getSelectedItem();
                recipientBox.removeAllItems();
                recipientBox.addItem("All Chat");
                for (String member : members) {
                    if (!member.equals(clientId)) {
                        recipientBox.addItem(member);
                    }
                }

                // Show or hide server timeout label based on member count
                if (members.length <= 1 && serverTimeoutLabel != null) {
                    serverTimeoutLabel.setVisible(true);
                } else if (serverTimeoutLabel != null) {
                    serverTimeoutLabel.setVisible(false);
                }

                // Restore previous selection if it still exists
                if (selectedRecipient != null) {
                    for (int i = 0; i < recipientBox.getItemCount(); i++) {
                        if (selectedRecipient.equals(recipientBox.getItemAt(i))) {
                            recipientBox.setSelectedItem(selectedRecipient);
                            break;
                        }
                    }
                }
            }
        } else if (message.startsWith("MEMBER_DETAILS:")) {
            if (chatArea != null) {
                String[] details = message.substring(15).split(",");
                StringBuilder detailsMessage = new StringBuilder();
                detailsMessage.append("\nCurrent Members:\n");
                detailsMessage.append("------------------------\n");
                for (String detail : details) {
                    String[] parts = detail.split(":");
                    detailsMessage.append(String.format("Name: %s\n", parts[0]));
                    detailsMessage.append(String.format("IP Address: %s\n", parts[1]));
                    detailsMessage.append(String.format("Port: %s\n", parts[2]));
                    detailsMessage.append("------------------------\n");
                }
                chatArea.append(detailsMessage.toString());
            }
        } else if (message.startsWith("MSG:")) {
            if (chatArea != null) {
                String[] parts = message.substring(4).split(":", 2);
                chatArea.append(parts[0] + ": " + parts[1] + "\n");
            }
        } else if (message.startsWith("PRIVATE_MSG:")) {
            if (chatArea != null) {
                String[] parts = message.substring(11).split(":", 2);
                chatArea.append("Private from " + parts[0] + ": " + parts[1] + "\n");
            }
        } else if (message.startsWith("Member Joined:")) {
            if (chatArea != null) {
                chatArea.append("Member joined: " + message.substring(14) + "\n");
            }
            // Reset server timeout label when someone joins
            if (serverTimeoutLabel != null) {
                serverTimeoutLabel.setVisible(false);
            }
        } else if (message.startsWith("Member Left:")) {
            if (chatArea != null) {
                chatArea.append("Member left: " + message.substring(12) + "\n");
            }
        } else if (message.equals("SERVER_SHUTTING_DOWN")) {
            if (chatArea != null) {
                chatArea.append("*** Server is shutting down ***\n");
            }
            JOptionPane.showMessageDialog(
                    this,
                    "The server is shutting down. You will be disconnected.",
                    "Server Shutdown",
                    JOptionPane.WARNING_MESSAGE
            );
        }

        // Update caret position
        if (chatArea != null) {
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }

    private void updateServerTimeoutLabel(int minutes, int seconds) {
        if (serverTimeoutLabel != null) {
            String timeText;
            if (minutes > 0) {
                timeText = String.format("Server idle shutdown in: %d min %d sec", minutes, seconds);
            } else {
                timeText = String.format("Server idle shutdown in: %d sec", seconds);
            }
            serverTimeoutLabel.setText(timeText);
            serverTimeoutLabel.setVisible(true);
        }
    }

    private void sendMessage(String message) {
        if (isConnected() && out != null) {
            out.println(message);
        }
    }

    private boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    /**
     * Shows the login panel by setting the card layout to show the "login" card.
     * This method is called from ApplicationLauncher when starting the client.
     */
    public void showLoginPanel() {
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "login");
        // Ensure we're using the login size
        setSize(400, 500);
        setLocationRelativeTo(null);
    }

    /**
     * Sets the reference to the server process.
     * This should only be called when this client is hosting the server.
     *
     * @param process The server process
     */
    public void setServerProcess(Process process) {
        this.serverProcess = process;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatClientGUI().setVisible(true);
        });
    }
}
