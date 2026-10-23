package com.netpulse.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an entry in the event audit log.
 */
public final class EventLogEntry {
    private static final DateTimeFormatter FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Instant timestamp;
    private final String targetLabel;
    private final String host;
    private final CheckType checkType;
    private final int port;
    private final CheckStatus status;
    private final Double responseTimeMs;
    private final String details;
    private final boolean isAlertEvent;
    private final boolean isRecoveryEvent;

    public EventLogEntry(Instant timestamp, String targetLabel, String host, CheckType checkType,
                         int port, CheckStatus status, Double responseTimeMs, String details,
                         boolean isAlertEvent, boolean isRecoveryEvent) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.targetLabel = Objects.requireNonNull(targetLabel, "targetLabel must not be null");
        this.host = Objects.requireNonNull(host, "host must not be null");
        this.checkType = Objects.requireNonNull(checkType, "checkType must not be null");
        this.port = port;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.responseTimeMs = responseTimeMs;
        this.details = details != null ? details : "";
        this.isAlertEvent = isAlertEvent;
        this.isRecoveryEvent = isRecoveryEvent;
    }

    public static EventLogEntry fromCheckResult(CheckResult result) {
        return new EventLogEntry(
                result.getTimestamp(),
                result.getTargetLabel(),
                result.getHost(),
                result.getCheckType(),
                result.getPort(),
                result.getStatus(),
                result.getResponseTimeMs().orElse(null),
                result.getDetails(),
                false,
                false
        );
    }

    public static EventLogEntry alert(String targetLabel, String host, CheckType checkType, int port,
                                      int consecutiveFailures, String lastError) {
        String msg = String.format("ALERT: %d consecutive failures reached. Last error: %s",
                consecutiveFailures, lastError);
        return new EventLogEntry(
                Instant.now(), targetLabel, host, checkType, port,
                CheckStatus.UNREACHABLE, null, msg, true, false
        );
    }

    public static EventLogEntry recovery(String targetLabel, String host, CheckType checkType, int port,
                                         double latencyMs) {
        String msg = String.format("RECOVERY: Target is reachable again. Latency: %.1f ms", latencyMs);
        return new EventLogEntry(
                Instant.now(), targetLabel, host, checkType, port,
                CheckStatus.REACHABLE, latencyMs, msg, false, true
        );
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        return FORMATTER.format(timestamp);
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public String getHost() {
        return host;
    }

    public CheckType getCheckType() {
        return checkType;
    }

    public int getPort() {
        return port;
    }

    public CheckStatus getStatus() {
        return status;
    }

    public Optional<Double> getResponseTimeMs() {
        return Optional.ofNullable(responseTimeMs);
    }

    public String getFormattedLatency() {
        if (responseTimeMs != null && responseTimeMs >= 0) {
            return String.format("%.1f ms", responseTimeMs);
        }
        return "—";
    }

    public String getDetails() {
        return details;
    }

    public boolean isAlertEvent() {
        return isAlertEvent;
    }

    public boolean isRecoveryEvent() {
        return isRecoveryEvent;
    }
}
