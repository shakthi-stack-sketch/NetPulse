package com.netpulse;

import com.netpulse.model.CheckResult;
import com.netpulse.model.CheckType;
import com.netpulse.model.NetworkTarget;
import com.netpulse.test.BeforeEach;
import com.netpulse.test.Test;

import static com.netpulse.test.Assert.*;

/**
 * Tests alert threshold triggering, spam prevention, and recovery transitions.
 * Requirement 8: Consecutive-failure alerts.
 */
public class AlertTriggerTest {

    private NetworkTarget target;

    @BeforeEach
    public void setup() {
        target = new NetworkTarget("Alert Target", "192.0.2.1", CheckType.ICMP_PING, 0);
        target.setConsecutiveFailureThreshold(3);
    }

    @Test
    public void testAlertTriggeringAndSpamPrevention() {
        assertFalse(target.isAlertActive(), "Target should start without active alert");

        // Failure 1 (threshold 3)
        boolean stateChanged1 = target.recordResult(CheckResult.unreachable(
                target.getId(), target.getLabel(), target.getHost(), CheckType.ICMP_PING, 0, "Timeout 1"));
        assertEquals(1, target.getConsecutiveFailures());
        assertFalse(target.isAlertActive());
        assertFalse(stateChanged1, "Threshold not reached; no state transition");

        // Failure 2
        boolean stateChanged2 = target.recordResult(CheckResult.unreachable(
                target.getId(), target.getLabel(), target.getHost(), CheckType.ICMP_PING, 0, "Timeout 2"));
        assertEquals(2, target.getConsecutiveFailures());
        assertFalse(target.isAlertActive());
        assertFalse(stateChanged2, "Threshold not reached; no state transition");

        // Failure 3 (Threshold reached!)
        boolean stateChanged3 = target.recordResult(CheckResult.unreachable(
                target.getId(), target.getLabel(), target.getHost(), CheckType.ICMP_PING, 0, "Timeout 3"));
        assertEquals(3, target.getConsecutiveFailures());
        assertTrue(target.isAlertActive(), "Alert must become active on reaching threshold");
        assertTrue(stateChanged3, "State transition occurred: alert triggered");

        // Failure 4 (Continued failure: test spam prevention!)
        boolean stateChanged4 = target.recordResult(CheckResult.unreachable(
                target.getId(), target.getLabel(), target.getHost(), CheckType.ICMP_PING, 0, "Timeout 4"));
        assertEquals(4, target.getConsecutiveFailures());
        assertTrue(target.isAlertActive(), "Alert remains active");
        assertFalse(stateChanged4, "No duplicate alert spam event should be generated");

        // Failure 5
        boolean stateChanged5 = target.recordResult(CheckResult.unreachable(
                target.getId(), target.getLabel(), target.getHost(), CheckType.ICMP_PING, 0, "Timeout 5"));
        assertEquals(5, target.getConsecutiveFailures());
        assertTrue(target.isAlertActive());
        assertFalse(stateChanged5);
    }

    @Test
    public void testAlertRecoveryTransition() {
        // Force alert state by recording 3 failures
        for (int i = 0; i < 3; i++) {
            target.recordResult(CheckResult.unreachable(
                    target.getId(), target.getLabel(), target.getHost(), CheckType.ICMP_PING, 0, "Timeout"));
        }
        assertTrue(target.isAlertActive());

        // Now target recovers with a successful check
        boolean recoveredStateChanged = target.recordResult(CheckResult.reachable(
                target.getId(), target.getLabel(), target.getHost(), CheckType.ICMP_PING, 0, 12.5, "Reply OK"));

        assertTrue(recoveredStateChanged, "State transition occurred: recovery triggered");
        assertFalse(target.isAlertActive(), "Alert must be cleared on recovery");
        assertEquals(0, target.getConsecutiveFailures(), "Consecutive failures reset to 0");

        // Subsequent success should not trigger recovery again
        boolean nextSuccessStateChanged = target.recordResult(CheckResult.reachable(
                target.getId(), target.getLabel(), target.getHost(), CheckType.ICMP_PING, 0, 14.0, "Reply OK"));
        assertFalse(nextSuccessStateChanged, "No duplicate recovery event");
    }
}
