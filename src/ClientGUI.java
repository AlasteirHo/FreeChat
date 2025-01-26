import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;

public class ClientGUI extends JFrame {
    private final JTextArea chatArea;
    private final JTextField inputField;
    private final JLabel serverInfoLabel; // Label to display server IP and port
    private final JLabel clientInfoLabel; // Label to display client IP
    private final JComboBox<String> recipientComboBox; // Drop-down menu for recipient selection
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final String username; // Store the username

    public ClientGUI(String serverAddress, int port, String username) {
        this.username = username;
        setTitle("Client Chat - " + username);
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Prevent default close behavior

        // Add a window listener to handle the close event
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                showExitConfirmation(); // Show confirmation dialog before quitting
            }
        });

        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Server and client info panel at the top
        JPanel infoPanel = new JPanel(new GridLayout(2, 1)); // Two rows for server and client info
        serverInfoLabel = new JLabel("Server: " + serverAddress + ":" + port);
        clientInfoLabel = new JLabel("Client IP: Fetching..."); // Placeholder for client IP
        infoPanel.add(serverInfoLabel);
        infoPanel.add(clientInfoLabel);
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        // Chat area (read-only)
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        mainPanel.add(chatScrollPane, BorderLayout.CENTER);

        // Input panel
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());

        inputField = new JTextField();
        inputPanel.add(inputField, BorderLayout.CENTER);

        JButton sendButton = new JButton("Send");
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Drop-down menu for recipient selection
        recipientComboBox = new JComboBox<>(new String[]{"All", "Private Message"});
        inputPanel.add(recipientComboBox, BorderLayout.WEST);

        JButton requestDetailsButton = new JButton("Request Details");
        inputPanel.add(requestDetailsButton, BorderLayout.NORTH);

        JButton quitButton = new JButton("Quit");
        inputPanel.add(quitButton, BorderLayout.SOUTH);

        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Connect to the server
        connectToServer(serverAddress, port);

        // Send button action
        sendButton.addActionListener(_ -> sendMessage());

        // Request Details button action
        requestDetailsButton.addActionListener(_ -> requestMemberDetails());

        // Quit button action
        quitButton.addActionListener(_ -> showExitConfirmation());

        // Enter key action
        inputField.addActionListener(e -> sendMessage());

        // Start a thread to listen for messages from the server
        new Thread(this::receiveMessages).start();
    }

    private void connectToServer(String serverAddress, int port) {
        try {
            System.out.println("Connecting to server at " + serverAddress + ":" + port + "...");
            socket = new Socket(serverAddress, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send the username to the server
            out.println(username);

            // Read the unique ID assigned by the server
            String clientId = in.readLine();
            System.out.println("Connected to server. Your ID is: " + clientId);
            chatArea.append("Connected to server. Your ID is: " + clientId + "\n");

            // Update the server info label
            serverInfoLabel.setText("Server: " + serverAddress + ":" + port);

            // Get the client's local IP address
            String clientIP = socket.getLocalAddress().getHostAddress();
            clientInfoLabel.setText("Client IP: " + clientIP); // Update the client IP label
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to connect to the server.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1); // Exit the program if connection fails
        }
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (!message.isEmpty()) {
            String recipient = (String) recipientComboBox.getSelectedItem();

            if (recipient.equals("All")) {
                // Send a broadcast message to all users
                out.println(message);
            } else if (recipient.equals("Private Message")) {
                // Prompt the user to enter the recipient's ID
                String recipientId = JOptionPane.showInputDialog(this, "Enter recipient ID:");
                if (recipientId != null && !recipientId.isEmpty()) {
                    // Send a private message to the specified recipient
                    out.println("/private " + recipientId + " " + message);
                } else {
                    JOptionPane.showMessageDialog(this, "Recipient ID cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            inputField.setText("");
        }
    }

    private void requestMemberDetails() {
        out.println("/requestDetails"); // Send a request to the server for member details
    }

    private void quit() {
        try {
            out.println("exit"); // Notify the server that the client is leaving
            socket.close(); // Close the socket
            System.exit(0); // Exit the program
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showExitConfirmation() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit?",
                "Exit",
                JOptionPane.YES_NO_OPTION
        );

        if (choice == JOptionPane.YES_OPTION) {
            try {
                out.println("exit"); // Notify the server that the client is leaving
                socket.close(); // Close the socket
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.exit(0); // Exit the program
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                // Create a final copy of the message variable for use in the lambda
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> {
                    // Display the message in the chat area
                    chatArea.append(finalMessage + "\n");
                });
            }
        } catch (SocketException e) {
            // Handle socket disconnection
            SwingUtilities.invokeLater(() -> {
                chatArea.append("Disconnected from the server.\n");
            });
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Clean up resources
            try {
                if (socket != null) socket.close();
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}