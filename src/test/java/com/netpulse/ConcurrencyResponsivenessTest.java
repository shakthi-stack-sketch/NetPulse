package com.netpulse;

import com.netpulse.model.CheckResult;
import com.netpulse.model.CheckType;
import com.netpulse.model.NetworkTarget;
import com.netpulse.monitoring.MonitoringManager;
import com.netpulse.service.NetworkCheckService;
import com.netpulse.service.PingService;
import com.netpulse.service.TcpProbeService;
import com.netpulse.test.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.netpulse.test.Assert.*;

/**
 * Tests concurrency, asynchronous execution, and UI responsiveness guarantee.
 * Requirement 10: Responsive UI while checks run, no overlapping checks,
 * independent target execution.
 */
public class ConcurrencyResponsivenessTest {

    @Test
    public void testConcurrentExecutionAndNonOverlappingProbes() throws InterruptedException {
        // Mock slow probe service to simulate long network delay without thread block
        NetworkCheckService controlledService = new NetworkCheckService(new PingService(), new TcpProbeService()) {
            @Override
            public CheckResult executeCheck(NetworkTarget target) {
                if (target.getHost().equals("slow.local")) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ignored) {}
                    return CheckResult.reachable(target.getId(), target.getLabel(), target.getHost(),
                            CheckType.ICMP_PING, 0, 300.0, "Slow response");
                } else {
                    return CheckResult.reachable(target.getId(), target.getLabel(), target.getHost(),
                            CheckType.ICMP_PING, 0, 5.0, "Fast response");
                }
            }
        };

        MonitoringManager manager = new MonitoringManager(controlledService);
        manager.startMonitoring();

        try {
            NetworkTarget slowTarget = new NetworkTarget("Slow Target", "slow.local", CheckType.ICMP_PING, 0);
            NetworkTarget fastTarget = new NetworkTarget("Fast Target", "fast.local", CheckType.ICMP_PING, 0);

            CountDownLatch fastCompletedLatch = new CountDownLatch(1);
            AtomicInteger fastCheckCount = new AtomicInteger(0);

            manager.addCheckListener((target, result) -> {
                if (target.getHost().equals("fast.local")) {
                    fastCheckCount.incrementAndGet();
                    fastCompletedLatch.countDown();
                }
            });

            // Dispatch both targets concurrently
            manager.dispatchCheck(slowTarget, true);
            manager.dispatchCheck(fastTarget, true);

            // Verify test for non-overlapping check:
            // A second simultaneous dispatch on the same slow target must be rejected while check is in progress
            boolean duplicateDispatched = manager.dispatchCheck(slowTarget, true);
            assertFalse(duplicateDispatched, "Duplicate overlapping probe for the same target must be rejected");

            // Fast target must complete quickly and not be blocked behind the slow target
            boolean fastDone = fastCompletedLatch.await(500, TimeUnit.MILLISECONDS);
            assertTrue(fastDone, "Fast target must complete independently without being blocked by slow target");
            assertTrue(fastCheckCount.get() >= 1, "Fast target check should have completed");

        } finally {
            manager.shutdown();
        }
    }

    @Test
    public void testCleanExecutorShutdown() {
        MonitoringManager manager = new MonitoringManager();
        manager.startMonitoring();
        assertTrue(manager.isMonitoring(), "Monitoring should be active");

        manager.shutdown();
        assertFalse(manager.isMonitoring(), "Monitoring must be halted after shutdown");
    }
}
