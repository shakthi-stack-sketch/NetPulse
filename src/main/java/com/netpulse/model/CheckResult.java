package com.netpulse.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable value object holding the factual result of an executed network diagnostic check.
 * No fabricated values: responseTimeMs is only present when actual round-trip time is measured.
 */
public final class CheckResult {
    private final String targetId;
    private final String targetLabel;
    private final String host;
    private final CheckType checkType;
    private final int port;
    private final Instant timestamp;
    private final CheckStatus status;
    private final Double responseTimeMs; // null if unmeasured / failed
    private final String details;
    private final boolean permissionLimited;

    public CheckResult(String targetId, String targetLabel, String host, CheckType checkType,
                       int port, Instant timestamp, CheckStatus status, Double responseTimeMs,
                       String details, boolean permissionLimited) {
        this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
        this.targetLabel = Objects.requireNonNull(targetLabel, "targetLabel must not be null");
        this.host = Objects.requireNonNull(host, "host must not be null");
        this.checkType = Objects.requireNonNull(checkType, "checkType must not be null");
        this.port = port;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.responseTimeMs = responseTimeMs;
        this.details = details != null ? details : "";
        this.permissionLimited = permissionLimited;
    }

    public static CheckResult reachable(String targetId, String targetLabel, String host,
                                        CheckType checkType, int port, double responseTimeMs, String details) {
        return new CheckResult(targetId, targetLabel, host, checkType, port,
                Instant.now(), CheckStatus.REACHABLE, responseTimeMs, details, false);
    }

    public static CheckResult unreachable(String targetId, String targetLabel, String host,
                                          CheckType checkType, int port, String details) {
        return new CheckResult(targetId, targetLabel, host, checkType, port,
                Instant.now(), CheckStatus.UNREACHABLE, null, details, false);
    }

    public static CheckResult unknown(String targetId, String targetLabel, String host,
                                      CheckType checkType, int port, String details, boolean permissionLimited) {
        return new CheckResult(targetId, targetLabel, host, checkType, port,
                Instant.now(), CheckStatus.UNKNOWN, null, details, permissionLimited);
    }

    public String getTargetId() {
        return targetId;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public CheckStatus getStatus() {
        return status;
    }

    public Optional<Double> getResponseTimeMs() {
        return Optional.ofNullable(responseTimeMs);
    }

    public String getDetails() {
        return details;
    }

    public boolean isPermissionLimited() {
        return permissionLimited;
    }

    public boolean isSuccess() {
        return status == CheckStatus.REACHABLE;
    }

    public String getFormattedLatency() {
        if (responseTimeMs != null && responseTimeMs >= 0) {
            return String.format("%.1f ms", responseTimeMs);
        }
        return "—";
    }

    @Override
    public String toString() {
        return "CheckResult{" +
                "target='" + targetLabel + '\'' +
                ", host='" + host + '\'' +
                ", type=" + checkType +
                ", status=" + status +
                ", latency=" + (responseTimeMs != null ? responseTimeMs + "ms" : "N/A") +
                ", details='" + details + '\'' +
                '}';
    }
}
