package com.netpulse.service;

import com.netpulse.model.CheckResult;
import com.netpulse.model.CheckType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes real ICMP reachability checks.
 * Attempts Java's InetAddress.isReachable first. If unsupported, restricted, or failing on
 * operating systems where raw ICMP sockets require elevated privileges (e.g., Windows port 7 fallback),
 * it performs an OS ping fallback with precise millisecond response-time extraction.
 */
public class PingService {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT).contains("win");

    // Regex to match "time=12ms", "time=12.4 ms", "time<1ms", "time < 1 ms"
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "time[<=]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*ms", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_LESS_THAN_ONE_PATTERN = Pattern.compile(
            "time\\s*<\\s*1\\s*ms", Pattern.CASE_INSENSITIVE);

    /**
     * Executes an ICMP check against the target host.
     *
     * @param targetId unique target identifier
     * @param targetLabel descriptive target name
     * @param host hostname or IP address
     * @param timeoutMs connection timeout in milliseconds
     * @return genuine CheckResult with empirical response time or clear diagnostic explanation
     */
    public CheckResult checkPing(String targetId, String targetLabel, String host, int timeoutMs) {
        long startNano = System.nanoTime();
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return CheckResult.unknown(targetId, targetLabel, host, CheckType.ICMP_PING, 0,
                    "DNS resolution failed: Host '" + host + "' could not be resolved", false);
        }

        // Try standard Java API first
        try {
            if (address.isReachable(timeoutMs)) {
                long elapsedNanos = System.nanoTime() - startNano;
                double latencyMs = elapsedNanos / 1_000_000.0;
                return CheckResult.reachable(targetId, targetLabel, host, CheckType.ICMP_PING, 0,
                        latencyMs, "InetAddress.isReachable confirmed host is alive");
            }
        } catch (Exception ignored) {
            // Permission limitation or network interface issue; proceed to OS fallback
        }

        // Java isReachable returned false or encountered permission limitation.
        // On Windows and unprivileged Linux, isReachable tries TCP echo on port 7, which is almost
        // universally closed, giving false negatives. Execute OS-level ICMP ping fallback.
        return executeOsPing(targetId, targetLabel, host, timeoutMs);
    }

    private CheckResult executeOsPing(String targetId, String targetLabel, String host, int timeoutMs) {
        long startNano = System.nanoTime();
        ProcessBuilder processBuilder;

        if (IS_WINDOWS) {
            // Windows: ping -n 1 -w <timeout_ms> <host>
            processBuilder = new ProcessBuilder("ping", "-n", "1", "-w", String.valueOf(timeoutMs), host);
        } else {
            // Linux/macOS: ping -c 1 -W <timeout_sec> <host>
            int timeoutSec = Math.max(1, (int) Math.ceil(timeoutMs / 1000.0));
            processBuilder = new ProcessBuilder("ping", "-c", "1", "-W", String.valueOf(timeoutSec), host);
        }

        processBuilder.redirectErrorStream(true);

        StringBuilder output = new StringBuilder();
        int exitCode = -1;

        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            exitCode = process.waitFor();
        } catch (Exception e) {
            // OS ping could not be executed (e.g. security manager or binary missing)
            return CheckResult.unknown(targetId, targetLabel, host, CheckType.ICMP_PING, 0,
                    "ICMP check unsupported: Java isReachable failed (TCP port 7 closed) and OS ping execution failed: "
                            + e.getMessage(), true);
        }

        long elapsedNanos = System.nanoTime() - startNano;
        double wallClockLatencyMs = elapsedNanos / 1_000_000.0;
        String rawOutput = output.toString();

        if (exitCode == 0) {
            // Successful reply; extract reported latency
            Double parsedLatency = parseLatencyFromPingOutput(rawOutput);
            double finalLatency = (parsedLatency != null) ? parsedLatency : Math.min(wallClockLatencyMs, timeoutMs);
            return CheckResult.reachable(targetId, targetLabel, host, CheckType.ICMP_PING, 0,
                    finalLatency, "ICMP Echo reply received");
        } else {
            // Ping exited with non-zero
            if (rawOutput.contains("could not find host") || rawOutput.contains("Unknown host")
                    || rawOutput.contains("Name or service not known")) {
                return CheckResult.unknown(targetId, targetLabel, host, CheckType.ICMP_PING, 0,
                        "DNS lookup failed in OS ping: " + extractRelevantLine(rawOutput), false);
            }

            if (rawOutput.contains("Destination host unreachable") || rawOutput.contains("Destination Host Unreachable")) {
                return CheckResult.unreachable(targetId, targetLabel, host, CheckType.ICMP_PING, 0,
                        "Destination host unreachable (ICMP routing error)");
            }

            if (rawOutput.contains("Request timed out") || rawOutput.contains("100% packet loss")
                    || rawOutput.contains("100% loss")) {
                return CheckResult.unreachable(targetId, targetLabel, host, CheckType.ICMP_PING, 0,
                        String.format("ICMP Echo request timed out after %d ms (packet filtered or host offline)", timeoutMs));
            }

            if (rawOutput.contains("General failure") || rawOutput.contains("Transmit failed")) {
                return CheckResult.unknown(targetId, targetLabel, host, CheckType.ICMP_PING, 0,
                        "Network stack general failure or interface down", true);
            }

            return CheckResult.unreachable(targetId, targetLabel, host, CheckType.ICMP_PING, 0,
                    "Ping returned code " + exitCode + ": " + extractRelevantLine(rawOutput));
        }
    }

    /**
     * Extracts numerical latency from standard OS ping output.
     */
    public Double parseLatencyFromPingOutput(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }

        if (TIME_LESS_THAN_ONE_PATTERN.matcher(output).find()) {
            return 0.5; // Represents sub-millisecond local response
        }

        Matcher matcher = TIME_PATTERN.matcher(output);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String extractRelevantLine(String output) {
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("Pinging") && !trimmed.startsWith("PING")) {
                return trimmed;
            }
        }
        return "No diagnostic output available";
    }
}
