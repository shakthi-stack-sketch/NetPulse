package com.netpulse.monitoring;

import com.netpulse.model.NetworkTarget;

/**
 * Listener interface for alert state transitions.
 */
public interface AlertListener {
    /**
     * Fired when a target reaches or exceeds the consecutive failure threshold.
     */
    void onAlertTriggered(NetworkTarget target, int consecutiveFailures, String lastError);

    /**
     * Fired when a previously failing target recovers and reports a successful check.
     */
    void onAlertRecovered(NetworkTarget target, double latencyMs);
}
