import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.Random;

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
    private Timer Ticker;
    private Timer memberUpdateTimer;
    private Socket socket;
    private volatile boolean connected = false;
    private String serverIP;
    private int serverPort;

    public ChatClientGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        setTitle("Chat System");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setContentPane(mainPanel);
        setSize(600, 800);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleClosing();
            }
        });

        // Set up login panel actions
        setupLoginPanel();

        // Set up chat panel actions
        setupChatPanel();

        // Initialize timers
        Ticker = new Timer(20000, e -> {
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
        sendButton.addActionListener(e -> sendChatMessage());
        messageField.addActionListener(e -> sendChatMessage());

        getMembersButton.addActionListener(e -> {
            if (isConnected()) {
                sendMessage("REQUEST_DETAILS");
                chatArea.append("\n----- Member Details -----\n");
            }
        });

        quitButton.addActionListener(e -> handleClosing());
    }

    private void sendChatMessage() {
        if (!isConnected()) return;
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
            cleanup();
            System.exit(0);
        }
    }

    private void cleanup() {
        connected = false;
        if (Ticker != null) {
            Ticker.stop();
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
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            // Get actual IP instead of localhost
            this.serverIP = InetAddress.getLocalHost().getHostAddress();
            this.serverPort = port;
            if (serverInfoLabel != null) {
                serverInfoLabel.setText(String.format("Server: %s:%d", this.serverIP, this.serverPort));
            }

            memberUpdateTimer.start();
            new Thread(() -> receiveMessages(socket)).start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Connection error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
                    CardLayout cl = (CardLayout) mainPanel.getLayout();
                    cl.show(mainPanel, "login");
                });
            }
        }
    }

    private void handleMessage(String message) {
        if (message.startsWith("COORDINATOR_STATUS:") && !isCoordinator) {
            isCoordinator = true;
            statusLabel.setText("Status: Coordinator");
            statusLabel.setForeground(Color.RED);
            chatArea.append("You are now the coordinator\n");
            Ticker.start();
        } else if (message.startsWith("COORDINATOR_INFO:")) {
            isCoordinator = false;
            String coordinatorId = message.substring(16);
            statusLabel.setText("Status: Member");
            statusLabel.setForeground(Color.BLUE);
            chatArea.append("Current coordinator is: " + coordinatorId + "\n");
            Ticker.stop();
        } else if (message.startsWith("MEMBER_LIST:")) {
            String[] members = message.substring(12).split(",");
            String selectedRecipient = (String) recipientBox.getSelectedItem();

            recipientBox.removeAllItems();
            recipientBox.addItem("All Chat");
            for (String member : members) {
                if (!member.equals(clientId)) {
                    recipientBox.addItem(member);
                }
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
        } else if (message.startsWith("MEMBER_DETAILS:")) {
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
        } else if (message.startsWith("MSG:")) {
            String[] parts = message.substring(4).split(":", 2);
            chatArea.append(parts[0] + ": " + parts[1] + "\n");
        } else if (message.startsWith("PRIVATE_MSG:")) {
            String[] parts = message.substring(11).split(":", 2);
            chatArea.append("Private from " + parts[0] + ": " + parts[1] + "\n");
        } else if (message.startsWith("MEMBER_JOIN:")) {
            chatArea.append("Member joined: " + message.substring(12) + "\n");
        } else if (message.startsWith("MEMBER_LEAVE:")) {
            chatArea.append("Member left: " + message.substring(12) + "\n");
        }
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
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
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatClientGUI().setVisible(true);
        });
    }
}