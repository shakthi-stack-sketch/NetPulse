package com.netpulse.model;

/**
 * Represents the connectivity state determined by a diagnostic check.
 */
public enum CheckStatus {
    REACHABLE("Reachable"),
    UNREACHABLE("Unreachable"),
    UNKNOWN("Unknown / Inconclusive");

    private final String displayName;

    CheckStatus(String displayName) {
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
