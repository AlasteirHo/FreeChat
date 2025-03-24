import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


import java.lang.reflect.*;

public class FreeChatLauncherTest {

    @Test
    public void testPortFinding() {
        System.out.println("Running testPortFinding: Verify that a valid port is returned during randomized ports.");
        FreeChatLauncher.faultInjection = false;
        int port = FreeChatLauncher.findRandomAvailablePort();
        System.out.println("Port returned: " + port);
        assertTrue(port >= 5000 && port <= 65535, "Port should be within the range 5000 to 65535.");
        System.out.println("testPortFindingNormal passed: A valid port was returned.\n");
    }

    @Test
    public void testPortUnableToFindPort() {
        System.out.println("Running testPortUnableToFindPort: Verify that -1 is returned when faultInjection is enabled.");
        FreeChatLauncher.faultInjection = true;
        int port = FreeChatLauncher.findRandomAvailablePort();
        System.out.println("Port returned with fault injection: " + port);
        assertEquals(-1, port, "Expected port to be -1 when fault injection is enabled.");
        FreeChatLauncher.faultInjection = false; // Reset flag for subsequent tests
        System.out.println("testPortUnableToFindPort passed: Fault injection correctly simulated.\n");
    }

    @Test
    public void testPrivateGetLocalIPAddress() throws Exception {
        System.out.println("Running testPrivateGetLocalIPAddress: Invoking private getLocalIPAddress() method via reflection.");
        FreeChatLauncher launcher = new FreeChatLauncher();
        Method getIPMethod = FreeChatLauncher.class.getDeclaredMethod("getLocalIPAddress");
        getIPMethod.setAccessible(true);
        String ipAddress = (String) getIPMethod.invoke(launcher);
        System.out.println("getLocalIPAddress() returned: " + ipAddress);
        assertNotNull(ipAddress, "IP address should not be null.");
        assertFalse(ipAddress.isEmpty(), "IP address should not be empty.");
        System.out.println("testPrivateGetLocalIPAddress passed: getLocalIPAddress() works as expected.\n");
    }

}
