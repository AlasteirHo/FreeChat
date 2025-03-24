import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Random;
import javax.swing.border.TitledBorder;

/**
 * A GUI client for the chat system. Provides the user interface for connecting to
 * a chat server, sending and receiving messages, and monitoring member status.
 */
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

    // Member tracking components
    private JPanel memberListPanel;
    private JTextArea activeMembersArea;
    private JTextArea inactiveMembersArea;
    private JFrame memberFrame = null;

    // Connection variables
    private PrintWriter out;
    private String clientId;
    private boolean isCoordinator = false;
    private final javax.swing.Timer memberUpdateTimer;
    private Socket socket;
    private volatile boolean connected = false;

    /**
     * Creates a new ChatClientGUI.
     */
    public ChatClientGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Failed to set system look and feel: " + e.getMessage());
        }

        setTitle("Chat System");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setContentPane(mainPanel);

        // Make sure to use the preferred size from the form
        pack();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleClosing();
            }
        });

        // Server timeout label has been removed

        // Create and add the member list panel
        createMemberListPanel();

        // Set up login panel actions
        setupLoginPanel();

        // Set up chat panel actions
        setupChatPanel();

        // Initialize member update timer
        memberUpdateTimer = new javax.swing.Timer(5000, _ -> {
            if (isConnected()) {
                SendMessage("GET_MEMBERS");
            }
        });
    }

    /**
     * Creates the panel that displays active and inactive members.
     */
    private void createMemberListPanel() {
        // Create the panel that will hold the member lists
        memberListPanel = new JPanel();
        memberListPanel.setLayout(new BorderLayout());
        memberListPanel.setPreferredSize(new Dimension(200, 600));
        memberListPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Create the active members area
        JPanel activePanel = new JPanel(new BorderLayout());
        activePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Active Members",
                TitledBorder.CENTER,
                TitledBorder.TOP));

        activeMembersArea = new JTextArea();
        activeMembersArea.setEditable(false);
        activeMembersArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        activeMembersArea.setBackground(new Color(240, 255, 240)); // Light green background

        JScrollPane activeScrollPane = new JScrollPane(activeMembersArea);
        activeScrollPane.setPreferredSize(new Dimension(190, 200));
        activePanel.add(activeScrollPane, BorderLayout.CENTER);

        // Create the inactive members area
        JPanel inactivePanel = new JPanel(new BorderLayout());
        inactivePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Inactive Members",
                TitledBorder.CENTER,
                TitledBorder.TOP));

        inactiveMembersArea = new JTextArea();
        inactiveMembersArea.setEditable(false);
        inactiveMembersArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        inactiveMembersArea.setBackground(new Color(255, 240, 240)); // Light red background

        JScrollPane inactiveScrollPane = new JScrollPane(inactiveMembersArea);
        inactiveScrollPane.setPreferredSize(new Dimension(190, 200));
        inactivePanel.add(inactiveScrollPane, BorderLayout.CENTER);

        // Add both panels to the member list panel
        memberListPanel.add(activePanel, BorderLayout.NORTH);
        memberListPanel.add(inactivePanel, BorderLayout.CENTER);

        // Initially hide the member list panel (shown only for coordinators)
        memberListPanel.setVisible(false);
    }

    /**
     * Sets up the actions for the login panel.
     */
    private void setupLoginPanel() {
        connectButton.addActionListener(_ -> {
            try {
                String username = usernameField.getText().trim();
                if (username.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please enter a username");
                    return;
                }

                String serverIp = serverIpField.getText().trim();
                if (serverIp.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "Please insert an IP address",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // Sets up/binds the unique identifier (<= 9999) to the username
                clientId = username + "#" + String.format("%04d", new Random().nextInt(10000));
                if (!isConnected()) {
                    connectToServer(
                            serverIp,
                            Integer.parseInt(portField.getText())
                    );
                }

                if (isConnected()) {
                    SendMessage("CONNECT:" + clientId);
                    showChatPanel();
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

        cancelButton.addActionListener(_ -> {
            dispose(); // Close current window
            // Show the main application dialog again
            SwingUtilities.invokeLater(FreeChatLauncher::showMainDialog);
        });
    }

    /**
     * Sets up the actions for the chat panel.
     */
    private void setupChatPanel() {
        if (sendButton != null) {
            sendButton.addActionListener(_ -> sendChatMessage());
        }

        if (messageField != null) {
            messageField.addActionListener(_ -> sendChatMessage());
        }

        if (getMembersButton != null) {
            getMembersButton.addActionListener(_ -> {
                if (isConnected() && chatArea != null) {
                    SendMessage("REQUEST_DETAILS");
                    chatArea.append("\n----- Member Details -----\n");
                }
            });
        }

        if (quitButton != null) {
            quitButton.addActionListener(_ -> handleClosing());
        }
    }

    /**
     * Sends a chat message to the server.
     * The message will be broadcast to all users or sent privately
     * based on the recipient selection.
     */
    private void sendChatMessage() {
        if (!isConnected() || recipientBox == null || messageField == null || chatArea == null) return;

        String recipient = (String) recipientBox.getSelectedItem();
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        assert recipient != null;
        if (recipient.equals("All Chat")) {
            SendMessage("BROADCAST:" + message);
        } else {
            SendMessage("PRIVATE:" + recipient + ":" + message);
            chatArea.append("Private to " + recipient + ": " + message + "\n");
        }
        messageField.setText("");
    }

    /**
     * Handles the closing of the window, asking for confirmation.
     */
    private void handleClosing() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit?",
                "Exit Confirmation",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            // Perform cleanup to properly disconnect from the server
            cleanup();
            System.exit(0);
        }
    }

    /**
     * Cleans up resources when the client is closing.
     */
    private void cleanup() {
        connected = false;
        if (memberUpdateTimer != null) {
            memberUpdateTimer.stop();
        }

        // Clean up the member frame if it exists
        if (memberFrame != null) {
            memberFrame.dispose();
            memberFrame = null;
        }

        // Properly close socket and streams
        try {
            if (out != null) {
                out.close();
                out = null;
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Connects to the chat server.
     */
    private void connectToServer(String host, int port) {
        try {
            socket = new Socket();
            // Set connection timeout to 3 seconds
            socket.connect(new InetSocketAddress(host, port), 3000);
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            // Get actual IP instead of localhost
            String serverIP = InetAddress.getLocalHost().getHostAddress();
            if (serverInfoLabel != null) {
                if (host.equals("localhost") || host.equals("127.0.0.1")) {
                    // For localhost connections, try to get the actual machine IP
                    serverInfoLabel.setText(String.format("Server: %s:%d", serverIP, port));
                } else {
                    // For external connections, use the provided host
                    serverInfoLabel.setText(String.format("Server: %s:%d", host, port));
                }
            }

            memberUpdateTimer.start();
            new Thread(() -> receiveMessages(socket)).start();
        } catch (ConnectException ce) {
            showConnectionError("No server found on " + host + ":" + port +
                            ".\nPlease check the port number and verify that the server is running.",
                    "Server Not Found");
        } catch (SocketTimeoutException ste) {
            showConnectionError("Connection to server timed out.\nPlease check the IP address and ensure the server is running.",
                    "Connection Timeout");
        } catch (UnknownHostException uhe) {
            showConnectionError("Unknown host: " + host + "\nPlease check the IP address or hostname.",
                    "Invalid Host");
        } catch (IOException ex) {
            String errorMsg = ex.getMessage();
            showConnectionError("Unable to connect to the server: " + host + ":" + port + "\n" +
                            "Reason: " + (errorMsg != null ? errorMsg : "Unknown error") +
                            "\n\nPlease verify that the server is running and that you have network connectivity.",
                    "Connection Error");
        }
    }

    /**
     * Helper method to show connection errors.
     *
     */
    private void showConnectionError(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Starts a thread to receive messages from the server.
     */
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
                    showLoginPanel();
                });
            }
        }
    }


     // Handles messages received from the server.
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

            // Show the member list panel for coordinators
            updateUIForCoordinatorStatus();

            // Request member list immediately to populate the window
            SendMessage("GET_MEMBERS");

        } else if (message.startsWith("COORDINATOR_INFO:")) {
            isCoordinator = false;
            String coordinatorId = message.substring(16);
            if (statusLabel != null) {
                statusLabel.setText("Status: Member");
                statusLabel.setForeground(Color.BLUE);
            }
            if (chatArea != null) {
                chatArea.append("Current coordinator is " + coordinatorId + "\n");
            }

            // Hide the member list panel for regular members
            updateUIForCoordinatorStatus();

        } else if (message.startsWith("MEMBER_LIST:")) {
            // This is where we update from the server's data
            String[] members = message.substring(12).split(",");
            String selectedRecipient;

            if (recipientBox != null) {
                selectedRecipient = (String) recipientBox.getSelectedItem();
                recipientBox.removeAllItems();
                recipientBox.addItem("All Chat");
                for (String member : members) {
                    if (!member.isEmpty() && !member.equals(clientId)) {
                        recipientBox.addItem(member);
                    }
                }

                // Restores the selection in member drop down menu (prevents it from switching back to All during the server update every 5 seconds)
                if (selectedRecipient != null) {
                    for (int i = 0; i < recipientBox.getItemCount(); i++) {
                        if (selectedRecipient.equals(recipientBox.getItemAt(i))) {
                            recipientBox.setSelectedItem(selectedRecipient);
                            break;
                        }
                    }
                }

                // If the client is the coordinator, update the active members display
                if (isCoordinator) {
                    updateMemberListDisplay(members);
                }
            }
        } else if (message.startsWith("INACTIVE_MEMBER_LIST:")) {
            String inactiveList = message.substring(21);

            // If the client is the coordinator, update the inactive members display directly with data from server
            if (isCoordinator && inactiveMembersArea != null) {
                StringBuilder inactiveText = new StringBuilder();
                // Sort the member names for consistent display
                String[] inactiveMembers = inactiveList.split(",");
                Arrays.sort(inactiveMembers);

                for (String member : inactiveMembers) {
                    if (!member.isEmpty()) {
                        inactiveText.append(member).append("\n");
                    }
                }
                inactiveMembersArea.setText(inactiveText.toString());
            }
            // Displays the current active members in the channel if the member_details button is pressed
        } else if (message.startsWith("MEMBER_DETAILS:")) {
            if (chatArea != null) {
                String[] details = message.substring(15).split(",");
                StringBuilder detailsMessage = new StringBuilder();
                detailsMessage.append("\nCurrent Members:\n");
                detailsMessage.append("------------------------\n");
                for (String detail : details) {
                    String[] parts = detail.split(":");
                    if (parts.length >= 3) {
                        detailsMessage.append(String.format("Name: %s\n", parts[0]));
                        detailsMessage.append(String.format("IP Address: %s\n", parts[1]));
                        detailsMessage.append(String.format("Port: %s\n", parts[2]));
                        detailsMessage.append("------------------------\n");
                    }
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
                String[] parts = message.substring(12).split(":", 2);
                chatArea.append("Private from " + parts[0] + ": " + parts[1] + "\n");
            }
        } else if (message.startsWith("Member Joined:")) {
            if (chatArea != null) {
                String newMember = message.substring(14);
                chatArea.append("Member joined: " + newMember + "\n");
            }
            // Server timeout label functionality has been removed
        } else if (message.startsWith("Member Left:")) {
            if (chatArea != null) {
                String leftMember = message.substring(12);
                chatArea.append("Member left: " + leftMember + "\n");
            }
        } else if (message.equals("SERVER_SHUT_DOWN")) {
            if (chatArea != null) {
                chatArea.append("*** Server is shutting down ***\n");
            }
            // In the event the server ends connection, the users will be displayed with this message of the server shutdown
            JOptionPane.showMessageDialog(
                    this,
                    "The server is shutting down. You will be disconnected.",
                    "Server Shutdown",
                    JOptionPane.WARNING_MESSAGE
            );
        }

        // Updates the caret position
        if (chatArea != null) {
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }


     // This method is called when the coordinator status changes to display the member activity window.
    private void updateUIForCoordinatorStatus() {
        if (isCoordinator) {
            try {
                // Close any existing member frame
                if (memberFrame != null) {
                    memberFrame.dispose();
                }

                // Create a new JFrame for the member list panel
                memberFrame = new JFrame("Member Activity");
                memberFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                memberFrame.setSize(220, 600);
                memberFrame.setResizable(true);
                memberFrame.setAlwaysOnTop(false);

                // Position the frame to the right of the main window
                Point mainLoc = getLocation();
                Dimension mainSize = getSize();
                memberFrame.setLocation(mainLoc.x + mainSize.width, mainLoc.y);

                // Add the member list panel to the frame
                memberFrame.add(memberListPanel);
                memberListPanel.setVisible(true);

                // Show the member frame and bring it to front
                memberFrame.setVisible(true);
                memberFrame.toFront();

                // Title for the companion window to display active and inactive members
                memberFrame.setTitle("Member List - " + clientId);

                // Request initial member data from server
                SendMessage("GET_MEMBERS");

                // Add a listener to keep the member frame properly positioned
                this.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentMoved(ComponentEvent e) {
                        if (memberFrame != null && memberFrame.isVisible()) {
                            Point mainLoc = getLocation();
                            Dimension mainSize = getSize();
                            memberFrame.setLocation(mainLoc.x + mainSize.width, mainLoc.y);
                        }
                    }
                });
                // Error handling in case the window doesn't show
            } catch (Exception ex) {
                System.err.println("Error creating member activity window: " + ex.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Error creating member activity window: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } else {
            // Hide and dispose the member frame if it exists, preventing duplicated windows when it refreshes every 5 seconds
            if (memberFrame != null) {
                memberFrame.setVisible(false);
                memberFrame.dispose();
                memberFrame = null;
            }
        }
    }

   // Handles updating the active member list in the window
    private void updateMemberListDisplay(String[] activeMembers) {
        if (!isCoordinator) return;

        // Make sure the member frame is visible
        if (memberFrame != null && !memberFrame.isVisible()) {
            memberFrame.setVisible(true);
            memberFrame.toFront();
        }

        // Update active members text area
        if (activeMembersArea != null) {
            StringBuilder activeText = new StringBuilder();

            for (String member : activeMembers) {
                if (member.isEmpty()) continue;

                activeText.append(member);
                if (member.equals(clientId)) {
                    activeText.append(" (You)");
                }
                activeText.append("\n");
            }
            activeMembersArea.setText(activeText.toString());
        }
    }


    private void SendMessage(String message) {
        if (isConnected() && out != null) {
            out.println(message);
        }
    }


     // Checks if the client is connected to the server.

    private boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public void showLoginPanel() {
        // Hide and dispose the member frame if it exists (it will only show on coordinator's chat window)
        if (memberFrame != null) {
            memberFrame.dispose();
            memberFrame = null;
        }

        // Reset coordinator status when going back to login
        isCoordinator = false;

        // Now show the login panel
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "login");

        pack();
        setLocationRelativeTo(null);
        setTitle("Chat System");
    }


    // Shows the chat panel and ensures the member activity window is displayed if coordinator.

    private void showChatPanel() {
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "chat");

        // Set title with client ID
        setTitle("Chat Client - " + clientId);

        pack();
        setLocationRelativeTo(null);

        // If coordinator, make sure member activity window is showing
        if (isCoordinator) {
            updateUIForCoordinatorStatus();
        }
    }

     // Sets the port and IP fields to the provided values when a client hosts a server, QOL improvement
    public void prefillConnectionFields(String ip, String port) {
        if (serverIpField != null) {
            serverIpField.setText(ip);
        }
        if (portField != null) {
            portField.setText(port);
        }
    }
    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        // If becoming visible and we're the coordinator, ensure member window is visible
        if (visible && isCoordinator && memberFrame != null) {
            memberFrame.setVisible(true);
            memberFrame.toFront();
        }
    }

     // Main method for testing.
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatClientGUI().setVisible(true));
    }
}