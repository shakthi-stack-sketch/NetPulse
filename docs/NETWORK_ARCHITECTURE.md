# NetPulse — Network Architecture and Diagnostics Engineering Specification

NetPulse is an empirical network diagnostics and latency monitoring desktop system engineered in Java 17+ and Java Swing. This document outlines the fundamental networking principles, operating system interactions, concurrency models, and statistical criteria governing the application.

---

## 1. High-Level Architecture Diagram

```
+========================================================================================+
|                                    NetPulse UI Layer                                   |
|  +-------------------+  +-------------------+  +------------------------------------+  |
|  |  DashboardFrame   |  |  LatencyChart     |  |          EventAuditLog             |  |
|  |  (Summary Cards,  |  |  (2D Graphics     |  |  (RFC 4180 CSV Export,             |  |
|  |   Target Table)   |  |   Rolling Window) |  |   Alert/Failure Filter)            |  |
|  +--------^----------+  +--------^----------+  +----------------^-------------------+  |
+===========|======================|==============================|======================+
            |                      |                              |
            | Dispatched via SwingUtilities.invokeLater()         |
            +----------------------+------------------------------+
                                   |
+==================================|=====================================================+
|                                  | Monitoring Layer                                    |
|  +-------------------------------+--------------------------------------------------+  |
|  |                              MonitoringManager                                   |  |
|  |  - ScheduledExecutorService (Periodic 1s Master Scheduler)                       |  |
|  |  - Fixed ThreadPoolExecutor (Concurrent Worker Probes: N_cores * 2)              |  |
|  |  - Atomic Concurrency Guards (Non-overlapping checks via AtomicBoolean)          |  |
|  |  - Alert State Machine (Thresholding & duplicate alert suppression)               |  |
|  +---------------------------------------+------------------------------------------+  |
+==========================================|=============================================+
                                           |
                                           v
+========================================================================================+
|                                   Diagnostic Engine                                    |
|                         +----------------------------------+                           |
|                         |       NetworkCheckService        |                           |
|                         +--------+----------------+--------+                           |
|                                  |                |                                    |
|                    ICMP_PING     |                | TCP_PORT                           |
|                                  v                v                                    |
|                +--------------------+   +---------------------+                        |
|                |    PingService     |   |   TcpProbeService   |                        |
|                +---------+----------+   +----------+----------+                        |
+==========================|=========================|===================================+
                           |                         |
                           v                         v
+========================================================================================+
|                              Operating System & Network Stack                          |
|  +-------------------------------------+   +----------------------------------------+  |
|  |  Java InetAddress.isReachable()     |   |  Java Socket.connect()                 |  |
|  |  Native OS Ping Fallback            |   |  - DNS Resolution (InetAddress)        |  |
|  |  - Windows: ping.exe -n 1 -w <ms>   |   |  - SYN Packet -> SYN-ACK Handshake     |  |
|  |  - Linux/Unix: ping -c 1 -W <sec>   |   |  - RST Detection (Port Closed vs Down) |  |
|  |  - Sub-millisecond parsing (<1ms)   |   |  - SocketTimeoutException Catch        |  |
|  +-------------------------------------+   +----------------------------------------+  |
+========================================================================================+
```

---

## 2. Core Networking Concepts

### 2.1 IP Addressing and Hostnames
Every machine connected to an IP network possesses one or more Internet Protocol (IP) addresses:
- **IPv4**: 32-bit addresses conventionally represented in dot-decimal notation (e.g., `127.0.0.1`, `8.8.8.8`, `192.168.1.1`).
- **IPv6**: 128-bit hexadecimal addresses segmented by colons (e.g., `::1`, `2001:4860:4860::8888`).
- **Loopback**: `127.0.0.1` and `::1` route traffic directly to the local host's internal network stack without traversing any external network interface card (NIC).

NetPulse accepts fully qualified domain names (FQDNs), standard hostnames, IPv4, and IPv6 addresses.

### 2.2 DNS Resolution
When a hostname is specified (e.g., `dns.google`), NetPulse queries the local resolver via `InetAddress.getByName(host)`:
1. If the DNS query succeeds, the target IP address is resolved and cached.
2. If the hostname does not exist or the DNS server fails to reply, an `UnknownHostException` is captured.
3. **Critical Diagnostic Distinction**: DNS resolution failures are reported as `UNKNOWN` / Inconclusive rather than `UNREACHABLE`. A failure to resolve a name does not confirm that the target computer is offline; it indicates that the naming lookup subsystem cannot translate the identifier into an IP address.

### 2.3 ICMP Echo and Reachability Checks
The **Internet Control Message Protocol (ICMP)** is part of the Internet Protocol suite (RFC 792) used for diagnostic and network management messaging.
- **Echo Request (Type 8)**: Sent to the target host.
- **Echo Reply (Type 0)**: Returned by the target host if online and configured to answer.

#### Java `InetAddress.isReachable()` Realities & OS Fallback:
In standard Java:
- Raw ICMP sockets require privileged operating system access (`CAP_NET_RAW` on Linux, Administrator on Windows).
- On Windows and unprivileged Linux, `InetAddress.isReachable(timeout)` does not issue raw ICMP packets. Instead, it attempts a TCP connection on port 7 (the legacy Echo Service), which is almost universally disabled on modern systems. As a consequence, `isReachable()` produces false negatives on healthy hosts.
- **NetPulse Solution**: NetPulse checks `InetAddress.isReachable(timeout)` first. If inconclusive or failing on Windows/unprivileged platforms, it falls back to the native operating system ping binary (`ping.exe` on Windows, `ping` on POSIX systems) with exact millisecond response-time extraction.

### 2.4 TCP Connections and Port Numbers
**Transmission Control Protocol (TCP)** provides reliable, ordered, and error-checked byte streams between applications over an IP network (RFC 793).
- While ICMP checks host-to-host IP reachability, TCP verifies whether a specific service application (web server on port 80/443, SSH daemon on port 22, database on port 5432) is actively listening and answering.
- The connection phase operates via the **Three-Way Handshake**:
  1. **SYN**: Client sends Synchronize segment with an initial sequence number.
  2. **SYN-ACK**: Server sends Synchronize-Acknowledge confirming connection acceptance.
  3. **ACK**: Client sends Acknowledgment; connection is established.
- **NetPulse Precision**: NetPulse starts high-precision nanosecond timing (`System.nanoTime()`) immediately before initiating `socket.connect(endpoint, timeoutMs)` and records the completion of the 3-way handshake. The socket is immediately closed, ensuring minimal resource consumption.

### 2.5 Probe Failure vs. Definitively Offline Host
A central tenet of senior network engineering is understanding that **a failed probe does not equate to a definitively offline host**:
1. **ICMP Dropped by Firewall**: Many enterprise gateways and cloud security groups (e.g., AWS Security Groups) intentionally drop all incoming ICMP Echo Requests (`DROP` without response), while remaining fully operational for HTTP/HTTPS.
2. **TCP Connection Refused (RST)**: If a probe to port 80 returns a `ConnectException: Connection refused`, a TCP `RST` (Reset) packet was sent back by the host. **This proves the target host is alive and responsive at the IP layer**, but the application is not listening on that specific port.
3. **Routing / TTL Expiration**: Intermediate routers may drop packets due to route flapping or egress policies without notifying the client.

NetPulse preserves these distinctions in the diagnostic event log, giving the operator actionable intelligence rather than naive binary assertions.

---

## 3. Concurrency and Execution Architecture

### 3.1 Non-Blocking Swing Architecture
The Java Swing Event Dispatch Thread (EDT) processes user interaction, mouse clicks, and window repainting. Executing network I/O on the EDT causes UI freezes, unresponsive buttons, and application stuttering.
- **Strict Separation**: No network I/O, DNS lookup, process execution, or file export ever runs on the EDT.
- **Worker Pools**: Probes execute concurrently on a dedicated `ExecutorService` sized dynamically to `Math.max(4, Runtime.getRuntime().availableProcessors() * 2)`.
- **EDT Dispatch**: Results update UI components strictly through `SwingUtilities.invokeLater()`.

### 3.2 Concurrency Guards & Non-Overlapping Probes
If a target's check interval is set to 2 seconds, but the target network experiences severe congestion and takes 4 seconds to time out, naive timers will spawn overlapping checks against the same host, exhausting sockets and skewing measurements.
- NetPulse enforces a thread-safe `AtomicBoolean checkInProgress` per target.
- If a scheduled tick fires while an existing check is in flight, the new check is skipped for that cycle.
- One failing or slow host cannot delay or bottleneck checks for other targets.

### 3.3 Alert State Machine and Spam Suppression
Alerts must notify administrators of persistent outages without flooding event logs every few seconds.
- NetPulse tracks `consecutiveFailures` against a user-configured threshold (default: 3).
- **Latching Flag**: An `AtomicBoolean alertActive` transitions from `false` to `true` when the threshold is first reached, logging a single distinct `ALERT` event.
- While the failure condition persists, subsequent failed checks update counters but **do not generate duplicate alert events**.
- When the target recovers with a successful check, `alertActive` transitions back to `false` and generates a single `RECOVERY` event recording the restoration latency.

---

## 4. Latency Measurements and Empirical Statistics

### 4.1 Millisecond Measurement Precision
- Latency calculations utilize `System.nanoTime()` rather than `System.currentTimeMillis()`. Wall-clock time is subject to NTP clock jumps, daylight saving adjustments, and low OS resolution (~15 ms on Windows). `System.nanoTime()` provides monotonic elapsed time converted to fractional milliseconds (`elapsed / 1_000_000.0`).
- Native ping output parses exact reported hardware/driver latencies (`time=12ms`, `time<1ms`).

### 4.2 Statistical Rigor
Target statistics maintain empirical results observed exclusively within the running session:
- **Minimum Latency**: Smallest recorded round-trip time in milliseconds.
- **Average Latency**: $\bar{x} = \frac{\sum_{i=1}^{N_{success}} t_i}{N_{success}}$
- **Maximum Latency**: Largest recorded round-trip time in milliseconds.
- **Success Rate**: $\text{Success \%} = \frac{N_{success}}{N_{total}} \times 100$
- **Observed Failure Rate**: $\text{Failure \%} = \frac{N_{failed}}{N_{total}} \times 100$
- **Empty Dataset Safety**: When $N_{total} = 0$, success and failure rates return $0.0\%$ (avoiding `NaN` or `ArithmeticException`), and latency displays em-dashes (`—`).

---

## 5. Security & Operating System Boundaries

- NetPulse requires **no administrative / root privileges** for normal operation.
- Sockets are closed using Java `try-with-resources`.
- Process builder calls to OS ping use bounded arguments and sanitized targets to prevent command injection.
- Exported CSV files implement RFC 4180 quotation escaping and UTF-8 Byte Order Marks (BOM) for compatibility with spreadsheet software.
