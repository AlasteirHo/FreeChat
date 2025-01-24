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
    private JButton privateButton;
    private JButton requestDetailsButton;
    private JButton quitButton;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientId;
    private String username; // Store the username

    public ClientGUI(String serverAddress, int port) {
        setTitle("Chat Client");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Prevent default close behavior

        // Add a window listener to handle the close event
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit(); // Call the quit method to leave the group
            }
        });

        // Prompt the user to enter a username
        username = JOptionPane.showInputDialog(this, "Enter your username:");
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty. Exiting...", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1); // Exit if the username is empty
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

        privateButton = new JButton("Private");
        inputPanel.add(privateButton, BorderLayout.WEST);

        requestDetailsButton = new JButton("Request Details");
        inputPanel.add(requestDetailsButton, BorderLayout.NORTH);

        quitButton = new JButton("Quit");
        inputPanel.add(quitButton, BorderLayout.SOUTH);

        mainPanel.add(inputPanel, BorderLayout.SOUTH);

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

        // Private button action
        privateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendPrivateMessage();
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
                quit();
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
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to connect to the server.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1); // Exit the program if connection fails
        }
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (!message.isEmpty()) {
            out.println(message);
            inputField.setText("");
        }
    }

    private void sendPrivateMessage() {
        String recipientId = JOptionPane.showInputDialog(this, "Enter recipient ID:");
        if (recipientId != null && !recipientId.isEmpty()) {
            String message = inputField.getText();
            if (!message.isEmpty()) {
                System.out.println("Sending private message to " + recipientId + ": " + message); // Debug statement
                out.println("/private " + recipientId + " " + message);
                inputField.setText("");
            } else {
                System.out.println("Message is empty."); // Debug statement
            }
        } else {
            System.out.println("Recipient ID is empty or invalid."); // Debug statement
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
            ClientGUI clientGUI = new ClientGUI("localhost", 5000);
            clientGUI.setVisible(true);
        });
    }
}