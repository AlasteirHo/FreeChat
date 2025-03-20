import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import javax.swing.JFrame;
import java.lang.reflect.*;
import java.util.Arrays;

public class ApplicationLauncherTest {

    @Test
    public void testPortFinding() {
        System.out.println("Running testPortFinding: Verify that a valid port is returned during randomized ports.");
        ApplicationLauncher.faultInjection = false;
        int port = ApplicationLauncher.findRandomAvailablePort();
        System.out.println("Port returned: " + port);
        assertTrue(port >= 5000 && port <= 65535, "Port should be within the range 5000 to 65535.");
        System.out.println("testPortFindingNormal passed: A valid port was returned.\n");
    }

    @Test
    public void testPortFindingFaultInjected() {
        System.out.println("Running testPortFindingFaultInjected: Verify that -1 is returned when faultInjection is enabled.");
        ApplicationLauncher.faultInjection = true;
        int port = ApplicationLauncher.findRandomAvailablePort();
        System.out.println("Port returned with fault injection: " + port);
        assertEquals(-1, port, "Expected port to be -1 when fault injection is enabled.");
        ApplicationLauncher.faultInjection = false; // Reset flag for subsequent tests
        System.out.println("testPortFindingFaultInjected passed: Fault injection correctly simulated.\n");
    }

    @Test
    public void testPrivateGetLocalIPAddressInvocation() throws Exception {
        System.out.println("Running testPrivateGetLocalIPAddressInvocation: Invoking private getLocalIPAddress() method via reflection.");
        ApplicationLauncher launcher = new ApplicationLauncher();
        Method getIPMethod = ApplicationLauncher.class.getDeclaredMethod("getLocalIPAddress");
        getIPMethod.setAccessible(true);
        String ipAddress = (String) getIPMethod.invoke(launcher);
        System.out.println("getLocalIPAddress() returned: " + ipAddress);
        assertNotNull(ipAddress, "IP address should not be null.");
        assertFalse(ipAddress.isEmpty(), "IP address should not be empty.");
        System.out.println("testPrivateGetLocalIPAddressInvocation passed: getLocalIPAddress() works as expected.\n");
    }

    @Test
    public void testConstructorsPresent() {
        System.out.println("Running testConstructorsPresent: Checking that ApplicationLauncher has at least one constructor.");
        Constructor<?>[] constructors = ApplicationLauncher.class.getDeclaredConstructors();
        for (Constructor<?> c : constructors) {
            System.out.println("Constructor: " + c.getName() + " " + Arrays.toString(c.getParameterTypes()));
        }
        assertTrue(constructors.length > 0, "There should be at least one constructor in ApplicationLauncher.");
        System.out.println("testConstructorsPresent passed: Constructors are present.\n");
    }

    @Test
    public void testMainMethodExistsAndIsStatic() throws Exception {
        System.out.println("Running testMainMethodExistsAndIsStatic: Verifying that the main method exists and is static in ApplicationLauncher.");
        Method mainMethod = ApplicationLauncher.class.getMethod("main", String[].class);
        boolean isStatic = Modifier.isStatic(mainMethod.getModifiers());
        System.out.println("Main method is static: " + isStatic);
        assertNotNull(mainMethod, "Main method should exist in ApplicationLauncher.");
        assertTrue(isStatic, "Main method should be static in ApplicationLauncher.");
        System.out.println("testMainMethodExistsAndIsStatic passed: Main method is present and static.\n");
    }

}
