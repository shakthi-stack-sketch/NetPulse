package com.netpulse.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Represents a monitored target (host/IP) along with its configuration
 * and observed session metrics.
 */
public class NetworkTarget {
    public static final int DEFAULT_TIMEOUT_MS = 2000;
    public static final int DEFAULT_INTERVAL_SECONDS = 5;
    public static final int DEFAULT_FAILURE_THRESHOLD = 3;
    public static final int MAX_ROLLING_POINTS = 60;

    // Validation patterns
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$");
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?$");

    private final String id;
    private volatile String label;
    private volatile String host;
    private volatile CheckType checkType;
    private volatile int port;
    private volatile int timeoutMs;
    private volatile int intervalSeconds;
    private volatile int consecutiveFailureThreshold;
    private volatile boolean enabled = true;

    // Runtime tracking
    private final AtomicBoolean checkInProgress = new AtomicBoolean(false);
    private final AtomicBoolean alertActive = new AtomicBoolean(false);
    private final AtomicInteger totalChecks = new AtomicInteger(0);
    private final AtomicInteger successfulChecks = new AtomicInteger(0);
    private final AtomicInteger failedChecks = new AtomicInteger(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    private volatile CheckResult lastCheckResult;
    private volatile Instant lastSuccessfulCheck;

    // Latency aggregation locks
    private final Object statsLock = new Object();
    private Double minResponseTimeMs = null;
    private Double maxResponseTimeMs = null;
    private double sumResponseTimeMs = 0.0;
    private int latencyCount = 0;
    private final List<Double> rollingLatencyHistory = new ArrayList<>();

    public NetworkTarget(String id, String label, String host, CheckType checkType,
                         int port, int timeoutMs, int intervalSeconds, int consecutiveFailureThreshold) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        this.label = label;
        this.host = host;
        this.checkType = checkType;
        this.port = port;
        this.timeoutMs = timeoutMs;
        this.intervalSeconds = intervalSeconds;
        this.consecutiveFailureThreshold = consecutiveFailureThreshold;
    }

    public NetworkTarget(String label, String host, CheckType checkType, int port) {
        this(UUID.randomUUID().toString(), label, host, checkType, port,
                DEFAULT_TIMEOUT_MS, DEFAULT_INTERVAL_SECONDS, DEFAULT_FAILURE_THRESHOLD);
    }

    /**
     * Validates configuration inputs.
     * @return null if valid, or a descriptive error message if invalid.
     */
    public static String validate(String label, String host, CheckType checkType,
                                  int port, int timeoutMs, int intervalSeconds, int failureThreshold) {
        if (label == null || label.trim().isEmpty()) {
            return "Target label cannot be empty.";
        }
        if (host == null || host.trim().isEmpty()) {
            return "Host or IP address cannot be empty.";
        }
        String trimmedHost = host.trim();
        // Allow localhost, IPv4, IPv6 brackets or valid hostnames
        if (!trimmedHost.equalsIgnoreCase("localhost")
                && !IPV4_PATTERN.matcher(trimmedHost).matches()
                && !HOSTNAME_PATTERN.matcher(trimmedHost).matches()
                && !trimmedHost.contains(":")) {
            return "Host must be a valid hostname, IPv4 address, or IPv6 address.";
        }

        if (checkType == CheckType.TCP_PORT) {
            if (port < 1 || port > 65535) {
                return "TCP port must be between 1 and 65535.";
            }
        }

        if (timeoutMs < 100 || timeoutMs > 30000) {
            return "Timeout must be between 100 ms and 30,000 ms.";
        }
        if (intervalSeconds < 1 || intervalSeconds > 3600) {
            return "Check interval must be between 1 second and 3600 seconds.";
        }
        if (failureThreshold < 1 || failureThreshold > 100) {
            return "Failure threshold must be between 1 and 100.";
        }

        return null; // Valid
    }

    /**
     * Records a check result and updates internal statistics thread-safely.
     * @return true if an alert state transition occurred (either triggered or recovered)
     */
    public boolean recordResult(CheckResult result) {
        Objects.requireNonNull(result, "result must not be null");
        this.lastCheckResult = result;
        totalChecks.incrementAndGet();

        boolean alertStateChanged = false;

        if (result.isSuccess()) {
            successfulChecks.incrementAndGet();
            consecutiveFailures.set(0);
            lastSuccessfulCheck = result.getTimestamp();

            result.getResponseTimeMs().ifPresent(ms -> {
                synchronized (statsLock) {
                    if (minResponseTimeMs == null || ms < minResponseTimeMs) {
                        minResponseTimeMs = ms;
                    }
                    if (maxResponseTimeMs == null || ms > maxResponseTimeMs) {
                        maxResponseTimeMs = ms;
                    }
                    sumResponseTimeMs += ms;
                    latencyCount++;

                    rollingLatencyHistory.add(ms);
                    if (rollingLatencyHistory.size() > MAX_ROLLING_POINTS) {
                        rollingLatencyHistory.remove(0);
                    }
                }
            });

            // Check if recovered from previous alert condition
            if (alertActive.compareAndSet(true, false)) {
                alertStateChanged = true;
            }
        } else {
            failedChecks.incrementAndGet();
            int currentFailures = consecutiveFailures.incrementAndGet();

            synchronized (statsLock) {
                // Record failed point as null or omit from latency curve
                // Keep history synchronized in size
                rollingLatencyHistory.add(null);
                if (rollingLatencyHistory.size() > MAX_ROLLING_POINTS) {
                    rollingLatencyHistory.remove(0);
                }
            }

            // Check if alert threshold reached
            if (currentFailures >= consecutiveFailureThreshold) {
                if (alertActive.compareAndSet(false, true)) {
                    alertStateChanged = true;
                }
            }
        }

        return alertStateChanged;
    }

    /**
     * Computes a snapshot of the current session statistics.
     */
    public TargetStatistics getStatisticsSnapshot() {
        synchronized (statsLock) {
            Double avg = latencyCount > 0 ? (sumResponseTimeMs / latencyCount) : null;
            return new TargetStatistics(
                    totalChecks.get(),
                    successfulChecks.get(),
                    failedChecks.get(),
                    consecutiveFailures.get(),
                    minResponseTimeMs,
                    avg,
                    maxResponseTimeMs
            );
        }
    }

    /**
     * Returns an unmodifiable snapshot copy of the rolling latency history for chart rendering.
     */
    public List<Double> getRollingLatencySnapshot() {
        synchronized (statsLock) {
            return Collections.unmodifiableList(new ArrayList<>(rollingLatencyHistory));
        }
    }

    public void resetStatistics() {
        totalChecks.set(0);
        successfulChecks.set(0);
        failedChecks.set(0);
        consecutiveFailures.set(0);
        alertActive.set(false);
        lastCheckResult = null;
        lastSuccessfulCheck = null;
        synchronized (statsLock) {
            minResponseTimeMs = null;
            maxResponseTimeMs = null;
            sumResponseTimeMs = 0.0;
            latencyCount = 0;
            rollingLatencyHistory.clear();
        }
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public CheckType getCheckType() {
        return checkType;
    }

    public void setCheckType(CheckType checkType) {
        this.checkType = checkType;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public int getConsecutiveFailureThreshold() {
        return consecutiveFailureThreshold;
    }

    public void setConsecutiveFailureThreshold(int consecutiveFailureThreshold) {
        this.consecutiveFailureThreshold = consecutiveFailureThreshold;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AtomicBoolean getCheckInProgress() {
        return checkInProgress;
    }

    public boolean isAlertActive() {
        return alertActive.get();
    }

    public CheckResult getLastCheckResult() {
        return lastCheckResult;
    }

    public Instant getLastSuccessfulCheck() {
        return lastSuccessfulCheck;
    }

    public int getTotalChecks() {
        return totalChecks.get();
    }

    public int getSuccessfulChecks() {
        return successfulChecks.get();
    }

    public int getFailedChecks() {
        return failedChecks.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public CheckStatus getCurrentStatus() {
        CheckResult result = lastCheckResult;
        return result != null ? result.getStatus() : CheckStatus.UNKNOWN;
    }

    @Override
    public String toString() {
        return label + " (" + host + (checkType == CheckType.TCP_PORT ? ":" + port : "") + ")";
    }
}
