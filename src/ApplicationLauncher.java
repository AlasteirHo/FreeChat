import javax.swing.*;

public class ApplicationLauncher {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        Object[] options = {"Host Server", "Join Server"};
        int choice = JOptionPane.showOptionDialog(null,
                "Choose an option",
                "Chat application",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == 0) { // Host Server
            String portStr = JOptionPane.showInputDialog(
                    null,
                    "Enter port number for the server (1024-65535):",
                    "Server Port",
                    JOptionPane.QUESTION_MESSAGE
            );

            try {
                if (portStr == null || portStr.trim().isEmpty()) {
                    return;
                }

                int port = Integer.parseInt(portStr);
                if (port < 1024 || port > 65535) {
                    JOptionPane.showMessageDialog(
                            null,
                            "Port must be between 1024 and 65535",
                            "Invalid Port",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

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
                SwingUtilities.invokeLater(() -> {
                    ChatClientGUI client = new ChatClientGUI();
                    client.showLoginPanel();
                    client.setVisible(true);
                });

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                        null,
                        "Please enter a valid port number",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        null,
                        "Error starting server: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        } else if (choice == 1) { // Join Server
            SwingUtilities.invokeLater(() -> {
                ChatClientGUI client = new ChatClientGUI();
                client.showLoginPanel();
                client.setVisible(true);
            });
        }
    }
}