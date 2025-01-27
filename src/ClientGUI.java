import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;

public class ClientGUI extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JComboBox<String> chatModeComboBox; // Dropdown for chat mode
    private JButton requestDetailsButton;
    private JButton quitButton;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientId;
    private String username;
    private JLabel serverInfoLabel; // Label to display server info

    public ClientGUI() {
        setTitle("Chat Client");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Add a window listener to handle the close event
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                showExitConfirmation();
            }
        });

        // Prompt the user to enter a username
        username = JOptionPane.showInputDialog(this, "Enter your username:");
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty. Exiting...", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Prompt the user to enter the server's IP address and port
        String serverAddress = JOptionPane.showInputDialog(this, "Enter the server's IP address:");
        if (serverAddress == null || serverAddress.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Server IP address cannot be empty. Exiting...", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        String portString = JOptionPane.showInputDialog(this, "Enter the server's port:");
        if (portString == null || portString.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Server port cannot be empty. Exiting...", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(portString);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number. Exiting...", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return; // This line is unreachable, but required for compilation
        }

        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());

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

        sendButton = new JButton("Send");
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Dropdown for chat mode (All/Private)
        chatModeComboBox = new JComboBox<>(new String[]{"All", "Private"});
        inputPanel.add(chatModeComboBox, BorderLayout.WEST);

        requestDetailsButton = new JButton("Request Details");
        inputPanel.add(requestDetailsButton, BorderLayout.NORTH);

        quitButton = new JButton("Quit");
        inputPanel.add(quitButton, BorderLayout.SOUTH);

        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        // Server info panel
        JPanel serverInfoPanel = new JPanel();
        serverInfoLabel = new JLabel("Server IP: " + serverAddress + ", Server Port: " + port); // Display server info
        serverInfoPanel.add(serverInfoLabel);
        mainPanel.add(serverInfoPanel, BorderLayout.NORTH); // Add server info to the top of the UI

        add(mainPanel);

        // Connect to the server
        connectToServer(serverAddress, port);

        // Send button action
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Request Details button action
        requestDetailsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                requestMemberDetails();
            }
        });

        // Quit button action
        quitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExitConfirmation();
            }
        });

        // Enter key action
        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

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
            clientId = in.readLine();
            System.out.println("Connected to server. Your ID is: " + clientId);
            chatArea.append("Connected to server. Your ID is: " + clientId + "\n");

            // Update the window title to include the username and ID
            setTitle(username + clientId + " Chat Client");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to connect to the server.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1); // Exit the program if connection fails
        }
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (!message.isEmpty()) {
            String chatMode = (String) chatModeComboBox.getSelectedItem();
            if (chatMode.equals("Private")) {
                String recipientId = JOptionPane.showInputDialog(this, "Enter recipient ID:");
                if (recipientId != null && !recipientId.isEmpty()) {
                    out.println("/private " + recipientId + " " + message);
                    inputField.setText(""); // Clear the input field only after sending the message
                } else {
                    // Do not clear the input field if the recipient ID is not provided
                    JOptionPane.showMessageDialog(this, "Recipient ID cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                out.println(message); // Send to all
                inputField.setText(""); // Clear the input field after sending the message
            }
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
            quit(); // Quit the application if the user confirms
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Received message: " + message); // Debug statement

                // Create a final variable for use in the lambda
                String finalMessage = message;
                SwingUtilities.invokeLater(() -> {
                    // Display the message in the chat area
                    chatArea.append(finalMessage + "\n");
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientGUI clientGUI = new ClientGUI();
            clientGUI.setVisible(true);
        });
    }
}