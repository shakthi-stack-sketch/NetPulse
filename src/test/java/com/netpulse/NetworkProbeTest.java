package com.netpulse;

import com.netpulse.model.CheckResult;
import com.netpulse.model.CheckStatus;
import com.netpulse.model.CheckType;
import com.netpulse.service.PingService;
import com.netpulse.service.TcpProbeService;
import com.netpulse.test.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static com.netpulse.test.Assert.*;

/**
 * Tests real network connectivity checks against active services, closed ports,
 * non-existent DNS domains, and timed-out probes.
 * Requirement 3: Localhost connectivity.
 * Requirement 4: Genuinely unavailable target.
 * Requirement 5: TCP connection success and failure.
 * Requirement 6: Timeout handling.
 */
public class NetworkProbeTest {

    private final PingService pingService = new PingService();
    private final TcpProbeService tcpProbeService = new TcpProbeService();

    @Test
    public void testLocalhostIcmpPing() {
        CheckResult result = pingService.checkPing("t1", "Localhost Ping", "127.0.0.1", 2000);
        assertNotNull(result);
        assertEquals(CheckStatus.REACHABLE, result.getStatus(),
                "Localhost 127.0.0.1 must be reachable via ping/fallback");
        assertTrue(result.getResponseTimeMs().isPresent(), "Latency must be measured");
        assertTrue(result.getResponseTimeMs().get() >= 0.0, "Latency must be non-negative");
    }

    @Test
    public void testTcpConnectionSuccessOnRealServer() throws IOException {
        // Spin up an actual temporary ServerSocket on loopback
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            // Run listener accept in background thread to complete handshake
            Thread serverThread = new Thread(() -> {
                try {
                    Socket client = serverSocket.accept();
                    client.close();
                } catch (IOException ignored) {}
            });
            serverThread.setDaemon(true);
            serverThread.start();

            CheckResult result = tcpProbeService.checkTcpPort("t2", "Local Server", "127.0.0.1", port, 2000);

            assertNotNull(result);
            assertEquals(CheckStatus.REACHABLE, result.getStatus(), "TCP probe to live ServerSocket must succeed");
            assertTrue(result.getResponseTimeMs().isPresent(), "Response time should be recorded");
            assertTrue(result.getResponseTimeMs().get() >= 0.0);
            assertTrue(result.getDetails().contains("TCP handshake established"));
        }
    }

    @Test
    public void testTcpConnectionRefusedOnClosedPort() {
        // Find an unassigned closed port
        int closedPort = 59871;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        } catch (IOException ignored) {}

        CheckResult result = tcpProbeService.checkTcpPort("t3", "Closed Port", "127.0.0.1", closedPort, 1000);

        assertNotNull(result);
        assertEquals(CheckStatus.UNREACHABLE, result.getStatus(), "Probe to closed port must report UNREACHABLE");
        assertTrue(result.getResponseTimeMs().isEmpty(), "No latency recorded for failed connection");
        assertTrue(result.getDetails().contains("Connection refused") || result.getDetails().contains("timed out"));
    }

    @Test
    public void testTimeoutHandlingOnNonRoutableTarget() {
        // TEST-NET-1 (RFC 5737) 192.0.2.1 is reserved for documentation and non-routable
        long start = System.currentTimeMillis();
        int timeoutMs = 400;

        CheckResult result = tcpProbeService.checkTcpPort("t4", "Unreachable Net", "192.0.2.1", 80, timeoutMs);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(result);
        assertEquals(CheckStatus.UNREACHABLE, result.getStatus());
        // Ensure timeout actually enforced and bounded
        assertTrue(elapsed < 2500, "Timeout probe should not hang indefinitely; took " + elapsed + "ms");
    }

    @Test
    public void testDnsFailureHandling() {
        String badDomain = "invalid-domain-xyz-" + System.currentTimeMillis() + ".test";
        CheckResult result = tcpProbeService.checkTcpPort("t5", "Bad Domain", badDomain, 80, 1000);

        assertNotNull(result);
        assertEquals(CheckStatus.UNKNOWN, result.getStatus(),
                "Non-resolvable domain must be categorized as UNKNOWN, not confirmed host down");
        assertTrue(result.getDetails().contains("DNS resolution failed"));
    }
}
