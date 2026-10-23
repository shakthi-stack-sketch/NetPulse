package com.netpulse.monitoring;

import com.netpulse.model.CheckResult;
import com.netpulse.model.NetworkTarget;

/**
 * Listener interface for completed network diagnostic checks.
 */
@FunctionalInterface
public interface CheckListener {
    void onCheckCompleted(NetworkTarget target, CheckResult result);
}
