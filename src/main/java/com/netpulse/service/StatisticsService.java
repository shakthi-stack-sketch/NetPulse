package com.netpulse.service;

import com.netpulse.model.CheckStatus;
import com.netpulse.model.NetworkTarget;
import com.netpulse.model.TargetStatistics;

import java.util.Collection;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Service for calculating individual and aggregate network statistics.
 * Ensures strict empirical calculations with no zero-division or fabricated numbers.
 */
public class StatisticsService {

    /**
     * Aggregate snapshot across all monitored targets.
     */
    public record DashboardSummary(
            int totalTargets,
            int reachableTargets,
            int failingTargets,
            int unknownTargets,
            int activeAlerts,
            int totalChecksExecuted,
            int totalSuccessfulChecks,
            int totalFailedChecks,
            OptionalDouble globalAverageLatencyMs
    ) {
        public double getGlobalSuccessRatePercent() {
            if (totalChecksExecuted == 0) {
                return 0.0;
            }
            return (totalSuccessfulChecks * 100.0) / totalChecksExecuted;
        }

        public double getGlobalFailureRatePercent() {
            if (totalChecksExecuted == 0) {
                return 0.0;
            }
            return (totalFailedChecks * 100.0) / totalChecksExecuted;
        }
    }

    /**
     * Computes the aggregated summary across all targets in the session.
     */
    public DashboardSummary computeSummary(Collection<NetworkTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return new DashboardSummary(0, 0, 0, 0, 0, 0, 0, 0, OptionalDouble.empty());
        }

        int total = targets.size();
        int reachable = 0;
        int failing = 0;
        int unknown = 0;
        int activeAlerts = 0;

        int totalChecks = 0;
        int totalSuccess = 0;
        int totalFailed = 0;
        double sumLatency = 0.0;
        int latencyMeasurements = 0;

        for (NetworkTarget target : targets) {
            CheckStatus status = target.getCurrentStatus();
            switch (status) {
                case REACHABLE -> reachable++;
                case UNREACHABLE -> failing++;
                case UNKNOWN -> unknown++;
            }

            if (target.isAlertActive()) {
                activeAlerts++;
            }

            TargetStatistics stats = target.getStatisticsSnapshot();
            totalChecks += stats.getTotalChecks();
            totalSuccess += stats.getSuccessfulChecks();
            totalFailed += stats.getFailedChecks();

            if (stats.getAvgResponseTimeMs().isPresent()) {
                sumLatency += (stats.getAvgResponseTimeMs().getAsDouble() * stats.getSuccessfulChecks());
                latencyMeasurements += stats.getSuccessfulChecks();
            }
        }

        OptionalDouble avgLatency = latencyMeasurements > 0
                ? OptionalDouble.of(sumLatency / latencyMeasurements)
                : OptionalDouble.empty();

        return new DashboardSummary(
                total,
                reachable,
                failing,
                unknown,
                activeAlerts,
                totalChecks,
                totalSuccess,
                totalFailed,
                avgLatency
        );
    }

    /**
     * Calculates rolling average of response times from raw latency samples.
     * Skips null/unreachable values.
     */
    public OptionalDouble calculateAverage(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            return OptionalDouble.empty();
        }

        double sum = 0.0;
        int count = 0;
        for (Double sample : samples) {
            if (sample != null && sample >= 0) {
                sum += sample;
                count++;
            }
        }

        return count > 0 ? OptionalDouble.of(sum / count) : OptionalDouble.empty();
    }
}
