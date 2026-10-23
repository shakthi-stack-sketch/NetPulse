package com.netpulse;

import com.netpulse.model.CheckType;
import com.netpulse.model.NetworkTarget;
import com.netpulse.test.Test;

import static com.netpulse.test.Assert.*;

/**
 * Tests target configuration validation and input boundary conditions.
 * Requirement 1: Valid target configuration.
 * Requirement 2: Invalid port rejection.
 */
public class TargetValidationTest {

    @Test
    public void testValidTargetConfigurations() {
        assertNull(NetworkTarget.validate("Google DNS", "8.8.8.8", CheckType.ICMP_PING, 0, 2000, 5, 3),
                "Valid IPv4 with ICMP should pass validation");

        assertNull(NetworkTarget.validate("Cloudflare Web", "1.1.1.1", CheckType.TCP_PORT, 443, 1000, 10, 2),
                "Valid IPv4 with TCP port 443 should pass validation");

        assertNull(NetworkTarget.validate("Localhost Ping", "localhost", CheckType.ICMP_PING, 0, 500, 1, 1),
                "Localhost should pass validation");

        assertNull(NetworkTarget.validate("Domain Name", "example.com", CheckType.TCP_PORT, 80, 2000, 5, 3),
                "Valid domain should pass validation");
    }

    @Test
    public void testEmptyAndBlankInputs() {
        assertNotNull(NetworkTarget.validate("", "8.8.8.8", CheckType.ICMP_PING, 0, 2000, 5, 3),
                "Empty label must fail");
        assertNotNull(NetworkTarget.validate("   ", "8.8.8.8", CheckType.ICMP_PING, 0, 2000, 5, 3),
                "Blank label must fail");
        assertNotNull(NetworkTarget.validate("Google DNS", "", CheckType.ICMP_PING, 0, 2000, 5, 3),
                "Empty host must fail");
        assertNotNull(NetworkTarget.validate("Google DNS", "   ", CheckType.ICMP_PING, 0, 2000, 5, 3),
                "Blank host must fail");
    }

    @Test
    public void testInvalidPortRejection() {
        assertNotNull(NetworkTarget.validate("Bad Port 0", "127.0.0.1", CheckType.TCP_PORT, 0, 2000, 5, 3),
                "Port 0 must be rejected");
        assertNotNull(NetworkTarget.validate("Bad Port Neg", "127.0.0.1", CheckType.TCP_PORT, -1, 2000, 5, 3),
                "Negative port must be rejected");
        assertNotNull(NetworkTarget.validate("Bad Port High", "127.0.0.1", CheckType.TCP_PORT, 65536, 2000, 5, 3),
                "Port 65536 must be rejected");
        assertNotNull(NetworkTarget.validate("Bad Port Huge", "127.0.0.1", CheckType.TCP_PORT, 99999, 2000, 5, 3),
                "Port 99999 must be rejected");

        // Boundary valid ports
        assertNull(NetworkTarget.validate("Boundary Port 1", "127.0.0.1", CheckType.TCP_PORT, 1, 2000, 5, 3),
                "Port 1 must be valid");
        assertNull(NetworkTarget.validate("Boundary Port 65535", "127.0.0.1", CheckType.TCP_PORT, 65535, 2000, 5, 3),
                "Port 65535 must be valid");
    }

    @Test
    public void testInvalidTimeoutAndInterval() {
        // Timeout range: 100 to 30000 ms
        assertNotNull(NetworkTarget.validate("Short Timeout", "127.0.0.1", CheckType.ICMP_PING, 0, 50, 5, 3),
                "Timeout < 100ms should be rejected");
        assertNotNull(NetworkTarget.validate("Long Timeout", "127.0.0.1", CheckType.ICMP_PING, 0, 40000, 5, 3),
                "Timeout > 30000ms should be rejected");

        // Interval range: 1 to 3600 sec
        assertNotNull(NetworkTarget.validate("Zero Interval", "127.0.0.1", CheckType.ICMP_PING, 0, 2000, 0, 3),
                "Interval 0s should be rejected");
        assertNotNull(NetworkTarget.validate("Negative Interval", "127.0.0.1", CheckType.ICMP_PING, 0, 2000, -5, 3),
                "Negative interval should be rejected");

        // Threshold range: 1 to 100
        assertNotNull(NetworkTarget.validate("Zero Threshold", "127.0.0.1", CheckType.ICMP_PING, 0, 2000, 5, 0),
                "Threshold 0 should be rejected");
        assertNotNull(NetworkTarget.validate("Negative Threshold", "127.0.0.1", CheckType.ICMP_PING, 0, 2000, 5, -2),
                "Negative threshold should be rejected");
    }
}
