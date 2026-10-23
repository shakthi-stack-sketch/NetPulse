package com.netpulse.model;

import java.util.OptionalDouble;

/**
 * Encapsulates observed statistical metrics for a network target during the active session.
 * 
 * NOTE: These are strictly empirical observations from this application's periodic checks
 * over the local system's network stack, not universal network statistics.
 */
public final class TargetStatistics {
    private final int totalChecks;
    private final int successfulChecks;
    private final int failedChecks;
    private final int consecutiveFailures;
    private final Double minResponseTimeMs;
    private final Double avgResponseTimeMs;
    private final Double maxResponseTimeMs;

    public TargetStatistics(int totalChecks, int successfulChecks, int failedChecks,
                            int consecutiveFailures,
                            Double minResponseTimeMs, Double avgResponseTimeMs, Double maxResponseTimeMs) {
        this.totalChecks = totalChecks;
        this.successfulChecks = successfulChecks;
        this.failedChecks = failedChecks;
        this.consecutiveFailures = consecutiveFailures;
        this.minResponseTimeMs = minResponseTimeMs;
        this.avgResponseTimeMs = avgResponseTimeMs;
        this.maxResponseTimeMs = maxResponseTimeMs;
    }

    public static TargetStatistics empty() {
        return new TargetStatistics(0, 0, 0, 0, null, null, null);
    }

    public int getTotalChecks() {
        return totalChecks;
    }

    public int getSuccessfulChecks() {
        return successfulChecks;
    }

    public int getFailedChecks() {
        return failedChecks;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public OptionalDouble getMinResponseTimeMs() {
        return minResponseTimeMs != null ? OptionalDouble.of(minResponseTimeMs) : OptionalDouble.empty();
    }

    public OptionalDouble getAvgResponseTimeMs() {
        return avgResponseTimeMs != null ? OptionalDouble.of(avgResponseTimeMs) : OptionalDouble.empty();
    }

    public OptionalDouble getMaxResponseTimeMs() {
        return maxResponseTimeMs != null ? OptionalDouble.of(maxResponseTimeMs) : OptionalDouble.empty();
    }

    /**
     * Calculates the observed success rate as a percentage [0.0 - 100.0].
     * Returns 0.0 when no checks have been executed (avoids division by zero).
     */
    public double getSuccessRatePercent() {
        if (totalChecks == 0) {
            return 0.0;
        }
        return (successfulChecks * 100.0) / totalChecks;
    }

    /**
     * Calculates the observed failure rate as a percentage [0.0 - 100.0].
     * Returns 0.0 when no checks have been executed (avoids division by zero).
     */
    public double getFailureRatePercent() {
        if (totalChecks == 0) {
            return 0.0;
        }
        return (failedChecks * 100.0) / totalChecks;
    }

    public String getFormattedMinLatency() {
        return minResponseTimeMs != null ? String.format("%.1f ms", minResponseTimeMs) : "—";
    }

    public String getFormattedAvgLatency() {
        return avgResponseTimeMs != null ? String.format("%.1f ms", avgResponseTimeMs) : "—";
    }

    public String getFormattedMaxLatency() {
        return maxResponseTimeMs != null ? String.format("%.1f ms", maxResponseTimeMs) : "—";
    }

    public String getFormattedSuccessRate() {
        if (totalChecks == 0) {
            return "N/A (0 checks)";
        }
        return String.format("%.1f%%", getSuccessRatePercent());
    }

    public String getFormattedFailureRate() {
        if (totalChecks == 0) {
            return "N/A (0 checks)";
        }
        return String.format("%.1f%%", getFailureRatePercent());
    }

    @Override
    public String toString() {
        return "TargetStatistics{" +
                "total=" + totalChecks +
                ", success=" + successfulChecks +
                ", failed=" + failedChecks +
                ", successRate=" + getFormattedSuccessRate() +
                ", min=" + getFormattedMinLatency() +
                ", avg=" + getFormattedAvgLatency() +
                ", max=" + getFormattedMaxLatency() +
                '}';
    }
}
