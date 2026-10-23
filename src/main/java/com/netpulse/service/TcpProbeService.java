package com.netpulse.service;

import com.netpulse.model.CheckResult;
import com.netpulse.model.CheckType;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Executes real TCP socket handshakes to explicitly configured service ports.
 * Accurately measures the round-trip TCP 3-way handshake time and diagnoses
 * connection state (established, timed out, RST/refused, DNS failure, unreachable).
 */
public class TcpProbeService {

    /**
     * Executes a real TCP handshake against the target host and port.
     *
     * @param targetId unique target identifier
     * @param targetLabel descriptive target name
     * @param host hostname or IP address
     * @param port TCP port number (1-65535)
     * @param timeoutMs connection timeout in milliseconds
     * @return factual CheckResult with genuine handshake latency or diagnostic failure details
     */
    public CheckResult checkTcpPort(String targetId, String targetLabel, String host, int port, int timeoutMs) {
        long startNano = System.nanoTime();
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return CheckResult.unknown(targetId, targetLabel, host, CheckType.TCP_PORT, port,
                    "DNS resolution failed: Host '" + host + "' cannot be resolved", false);
        }

        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.setReuseAddress(true);

            long connectStartNano = System.nanoTime();
            socket.connect(new InetSocketAddress(address, port), timeoutMs);
            long elapsedNanos = System.nanoTime() - connectStartNano;

            double latencyMs = elapsedNanos / 1_000_000.0;
            return CheckResult.reachable(targetId, targetLabel, host, CheckType.TCP_PORT, port,
                    latencyMs, String.format("TCP handshake established on port %d", port));

        } catch (SocketTimeoutException e) {
            return CheckResult.unreachable(targetId, targetLabel, host, CheckType.TCP_PORT, port,
                    String.format("TCP connection timed out after %d ms on port %d (SYN sent, no SYN-ACK received)",
                            timeoutMs, port));

        } catch (ConnectException e) {
            // A connection refusal (RST packet) proves the host's IP stack is active and responsive,
            // even though the specific port is closed or rejected.
            return CheckResult.unreachable(targetId, targetLabel, host, CheckType.TCP_PORT, port,
                    String.format("Connection refused on port %d (RST packet received; host is active but service is not listening)",
                            port));

        } catch (NoRouteToHostException e) {
            return CheckResult.unreachable(targetId, targetLabel, host, CheckType.TCP_PORT, port,
                    "No route to host (network path unreachable or interface down)");

        } catch (IOException e) {
            return CheckResult.unreachable(targetId, targetLabel, host, CheckType.TCP_PORT, port,
                    "TCP I/O error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }
}
