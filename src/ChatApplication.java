import javax.swing.*;

public class ChatApplication {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Ask the user if they want to host or join a server
            String[] options = {"Host", "Join"};
            int choice = JOptionPane.showOptionDialog(
                    null,
                    "Do you want to host or join a server?",
                    "Chat Application",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == 0) {
                // Host a server
                int port = Integer.parseInt(JOptionPane.showInputDialog("Enter the port to host the server:"));
                startServer(port); // Start the server
                JOptionPane.showMessageDialog(null, "Server is running on port " + port);

                // Automatically launch a client GUI for the host and connect to the server
                launchClientGUI("localhost", port);
            } else if (choice == 1) {
                // Join a server
                String serverAddress = JOptionPane.showInputDialog("Enter the server's IP address:");
                int port = Integer.parseInt(JOptionPane.showInputDialog("Enter the server's port:"));
                launchClientGUI(serverAddress, port); // Launch the client GUI to join the server
            }
        });
    }

    private static void startServer(int port) {
        new Thread(() -> {
            Server server = new Server(port);
            server.start(); // Start the server in a new thread
        }).start();
    }

    private static void launchClientGUI(String serverAddress, int port) {
        SwingUtilities.invokeLater(() -> {
            ClientGUI clientGUI = new ClientGUI(serverAddress, port);
            clientGUI.setVisible(true);
        });
    }
}