import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.Random;

public class ChatClientGUI extends JFrame {
    private PrintWriter out;
    private String clientId;
    private boolean isCoordinator = false;
    private JTextArea chatArea;
    private JTextField messageField;
    private JComboBox<String> recipientBox;
    private JPanel cardLayout;
    private CardLayout cards;
    private JLabel statusLabel;
    private Timer heartbeatTimer;
    private Timer memberUpdateTimer;
    private Socket socket;
    private volatile boolean connected = false;
    private String serverIP;
    private int serverPort;
    private JLabel serverInfoLabel;

    public ChatClientGUI() {
        setTitle("Chat System");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(600, 800);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleClosing();
            }
        });

        cards = new CardLayout();
        cardLayout = new JPanel(cards);
        add(cardLayout);
        cardLayout.add(createLoginPanel(), "login");
        cardLayout.add(createChatPanel(), "chat");
        cards.show(cardLayout, "login");

        heartbeatTimer = new Timer(20000, e -> {
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

    private JPanel createLoginPanel() {
        JPanel loginPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField usernameField = new JTextField(20);
        JTextField serverIpField = new JTextField("localhost", 20);
        JTextField portField = new JTextField("5000", 20);
        JButton connectButton = new JButton("Connect");

        gbc.gridx = 0; gbc.gridy = 0;
        loginPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        loginPanel.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        loginPanel.add(new JLabel("Server IP:"), gbc);
        gbc.gridx = 1;
        loginPanel.add(serverIpField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        loginPanel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1;
        loginPanel.add(portField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        loginPanel.add(connectButton, gbc);

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
                    cards.show(cardLayout, "chat");
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
        return loginPanel;
    }

    private JPanel createChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());

        // Status Panel
        JPanel statusPanel = new JPanel(new GridLayout(2, 1));
        JPanel coordinatorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Status: Member");
        statusLabel.setForeground(Color.BLUE);
        coordinatorPanel.add(statusLabel);

        JPanel serverInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        serverInfoLabel = new JLabel("Server: Connecting...");
        serverInfoPanel.add(serverInfoLabel);

        statusPanel.add(coordinatorPanel);
        statusPanel.add(serverInfoPanel);
        chatPanel.add(statusPanel, BorderLayout.NORTH);

        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        chatPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom panel
        JPanel bottomPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        recipientBox = new JComboBox<>(new String[]{"All Channel"});
        recipientBox.setPreferredSize(new Dimension(150, 25));
        messageField = new JTextField();
        messageField.setPreferredSize(new Dimension(300, 25));
        JButton sendButton = new JButton("Send");
        JButton getMembersButton = new JButton("Request Details");
        JButton quitButton = new JButton("Quit");

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        bottomPanel.add(recipientBox, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        bottomPanel.add(messageField, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        bottomPanel.add(sendButton, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 1;
        bottomPanel.add(getMembersButton, gbc);

        gbc.gridx = 2;
        bottomPanel.add(quitButton, gbc);

        chatPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Button actions
        sendButton.addActionListener(e -> sendChatMessage());
        messageField.addActionListener(e -> sendChatMessage());
        getMembersButton.addActionListener(e -> {
            if (isConnected()) {
                sendMessage("REQUEST_DETAILS");
                chatArea.append("\n----- Member Details -----\n");
            }
        });
        quitButton.addActionListener(e -> handleClosing());

        return chatPanel;
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
        if (out != null) {
            sendMessage("QUIT");
        }
        if (heartbeatTimer != null) {
            heartbeatTimer.stop();
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
        } catch (IOException e) {
            if (connected) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            this,
                            "Lost connection to server: " + e.getMessage(),
                            "Connection Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    cleanup();
                    cards.show(cardLayout, "login");
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
            heartbeatTimer.start();
        } else if (message.startsWith("COORDINATOR_INFO:")) {
            isCoordinator = false;
            String coordinatorId = message.substring(16);
            statusLabel.setText("Status: Member");
            statusLabel.setForeground(Color.BLUE);
            chatArea.append("Current coordinator is: " + coordinatorId + "\n");
            heartbeatTimer.stop();
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
            chatArea.append("Private from" + parts[0] + " " + parts[1] + "\n");
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

    public void showLoginPanel() {
        cards.show(cardLayout, "login");
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            new ChatClientGUI().setVisible(true);
        });
    }
}
