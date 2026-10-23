package com.netpulse.service;

import com.netpulse.model.CheckResult;
import com.netpulse.model.CheckType;
import com.netpulse.model.NetworkTarget;

import java.util.Objects;

/**
 * High-level service facade for routing network probes to PingService or TcpProbeService.
 */
public class NetworkCheckService {

    private final PingService pingService;
    private final TcpProbeService tcpProbeService;

    public NetworkCheckService() {
        this(new PingService(), new TcpProbeService());
    }

    public NetworkCheckService(PingService pingService, TcpProbeService tcpProbeService) {
        this.pingService = Objects.requireNonNull(pingService, "pingService must not be null");
        this.tcpProbeService = Objects.requireNonNull(tcpProbeService, "tcpProbeService must not be null");
    }

    /**
     * Executes a factual diagnostic check for the specified target.
     *
     * @param target the target to probe
     * @return genuine CheckResult
     */
    public CheckResult executeCheck(NetworkTarget target) {
        Objects.requireNonNull(target, "target must not be null");

        if (target.getCheckType() == CheckType.TCP_PORT) {
            return tcpProbeService.checkTcpPort(
                    target.getId(),
                    target.getLabel(),
                    target.getHost(),
                    target.getPort(),
                    target.getTimeoutMs()
            );
        } else {
            return pingService.checkPing(
                    target.getId(),
                    target.getLabel(),
                    target.getHost(),
                    target.getTimeoutMs()
            );
        }
    }
}
