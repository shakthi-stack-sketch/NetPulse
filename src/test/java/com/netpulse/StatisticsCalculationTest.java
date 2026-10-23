package com.netpulse;

import com.netpulse.model.CheckResult;
import com.netpulse.model.CheckType;
import com.netpulse.model.NetworkTarget;
import com.netpulse.model.TargetStatistics;
import com.netpulse.test.BeforeEach;
import com.netpulse.test.Test;

import static com.netpulse.test.Assert.*;

/**
 * Tests empirical statistics calculation and division-by-zero prevention.
 * Requirement 7: Statistics calculations.
 */
public class StatisticsCalculationTest {

    private NetworkTarget target;

    @BeforeEach
    public void setup() {
        target = new NetworkTarget("Test Target", "127.0.0.1", CheckType.ICMP_PING, 0);
    }

    @Test
    public void testEmptyDataSet() {
        TargetStatistics stats = target.getStatisticsSnapshot();
        assertEquals(0, stats.getTotalChecks());
        assertEquals(0, stats.getSuccessfulChecks());
        assertEquals(0, stats.getFailedChecks());
        assertEquals(0, stats.getConsecutiveFailures());
        assertTrue(stats.getMinResponseTimeMs().isEmpty(), "Min response time must be empty for 0 checks");
        assertTrue(stats.getAvgResponseTimeMs().isEmpty(), "Avg response time must be empty for 0 checks");
        assertTrue(stats.getMaxResponseTimeMs().isEmpty(), "Max response time must be empty for 0 checks");

        // Success / failure rates should be 0.0 without divide by zero
        assertEquals(0.0, stats.getSuccessRatePercent(), 0.001);
        assertEquals(0.0, stats.getFailureRatePercent(), 0.001);
        assertFalse(Double.isNaN(stats.getSuccessRatePercent()), "Success rate must not be NaN");
        assertFalse(Double.isNaN(stats.getFailureRatePercent()), "Failure rate must not be NaN");
        assertEquals("—", stats.getFormattedMinLatency());
        assertEquals("—", stats.getFormattedAvgLatency());
        assertEquals("—", stats.getFormattedMaxLatency());
    }

    @Test
    public void testSuccessfulChecksStatistics() {
        // Record 3 successful checks with latencies: 10.0ms, 30.0ms, 20.0ms
        target.recordResult(CheckResult.reachable(target.getId(), target.getLabel(), target.getHost(),
                CheckType.ICMP_PING, 0, 10.0, "OK"));
        target.recordResult(CheckResult.reachable(target.getId(), target.getLabel(), target.getHost(),
                CheckType.ICMP_PING, 0, 30.0, "OK"));
        target.recordResult(CheckResult.reachable(target.getId(), target.getLabel(), target.getHost(),
                CheckType.ICMP_PING, 0, 20.0, "OK"));

        TargetStatistics stats = target.getStatisticsSnapshot();
        assertEquals(3, stats.getTotalChecks());
        assertEquals(3, stats.getSuccessfulChecks());
        assertEquals(0, stats.getFailedChecks());
        assertEquals(0, stats.getConsecutiveFailures());

        assertTrue(stats.getMinResponseTimeMs().isPresent());
        assertEquals(10.0, stats.getMinResponseTimeMs().getAsDouble(), 0.001);

        assertTrue(stats.getMaxResponseTimeMs().isPresent());
        assertEquals(30.0, stats.getMaxResponseTimeMs().getAsDouble(), 0.001);

        assertTrue(stats.getAvgResponseTimeMs().isPresent());
        assertEquals(20.0, stats.getAvgResponseTimeMs().getAsDouble(), 0.001);

        assertEquals(100.0, stats.getSuccessRatePercent(), 0.001);
        assertEquals(0.0, stats.getFailureRatePercent(), 0.001);
    }

    @Test
    public void testMixedSuccessAndFailures() {
        // 3 successes, 1 failure = 75% success, 25% failure
        target.recordResult(CheckResult.reachable(target.getId(), target.getLabel(), target.getHost(),
                CheckType.ICMP_PING, 0, 15.0, "OK"));
        target.recordResult(CheckResult.unreachable(target.getId(), target.getLabel(), target.getHost(),
                CheckType.ICMP_PING, 0, "Timed out"));
        target.recordResult(CheckResult.reachable(target.getId(), target.getLabel(), target.getHost(),
                CheckType.ICMP_PING, 0, 25.0, "OK"));
        target.recordResult(CheckResult.reachable(target.getId(), target.getLabel(), target.getHost(),
                CheckType.ICMP_PING, 0, 20.0, "OK"));

        TargetStatistics stats = target.getStatisticsSnapshot();
        assertEquals(4, stats.getTotalChecks());
        assertEquals(3, stats.getSuccessfulChecks());
        assertEquals(1, stats.getFailedChecks());
        assertEquals(0, stats.getConsecutiveFailures(), "Consecutive failures should reset to 0 after success");

        assertEquals(75.0, stats.getSuccessRatePercent(), 0.001);
        assertEquals(25.0, stats.getFailureRatePercent(), 0.001);

        // Average should be (15 + 25 + 20) / 3 = 20.0
        assertEquals(20.0, stats.getAvgResponseTimeMs().getAsDouble(), 0.001);
    }
}
