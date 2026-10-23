package com.netpulse.model;

/**
 * Enumeration of supported network probe mechanisms.
 */
public enum CheckType {
    ICMP_PING("ICMP Ping"),
    TCP_PORT("TCP Port Probe");

    private final String displayName;

    CheckType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
