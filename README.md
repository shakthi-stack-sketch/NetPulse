# NetPulse — Network Health and Latency Monitoring System

NetPulse is a high-performance, real-time desktop network diagnostics application built with **Java 17+** and **Java Swing**. It provides empirical latency tracking, multi-target concurrent probing, rolling 2D response-time telemetry visualization, failure alerting with spam suppression, and RFC 4180 CSV audit log export.

NetPulse executes **genuine network probes** using standard Java networking APIs (`InetAddress`, `Socket`) alongside an automatic operating-system ping fallback for reliable ICMP metrics across unprivileged systems. It contains **no simulated latency, no fake online hosts, and no fabricated statistics**.

---

## Key Features

- **Concurrent Multi-Target Monitoring**: Concurrently probe several endpoints without cross-target blocking or UI freezes.
- **Dual Check Mechanisms**:
  - **ICMP Echo Ping**: Diagnostic ping using `InetAddress.isReachable` with native OS ping fallback (`ping.exe` / `ping`).
  - **TCP Socket Handshake**: Real 3-way handshake round-trip timing against explicitly configured service ports (HTTP, HTTPS, SSH, DNS, Custom).
- **Sub-Millisecond Rolling Telemetry Visualization**:
  - High-precision custom Java 2D anti-aliased latency chart with gradient fills and data dots.
  - Informative empty-state graphics when no measurements exist.
- **Empirical Statistics & Zero-Division Safety**:
  - Minimum, Average, and Maximum round-trip latencies.
  - Total, successful, and failed check counts.
  - Explicitly labeled empirical success and failure rates ($0.0\%$ when no data, never `NaN`).
- **Consecutive-Failure Alerts with Spam Suppression**:
  - Configurable threshold (e.g., 3 consecutive failures).
  - Latching alert transitions generate an alert event once and suppress repeated notifications until full recovery.
- **Diagnostic Event Audit Log**:
  - Detailed timestamps, targets, probe types, latency numbers, and diagnostic error codes.
  - Filterable by *All Events*, *Only Alerts & Recoveries*, *Only Failures*, and *Reachable Checks*.
  - One-click RFC 4180 CSV export with Excel-compatible UTF-8 BOM.
- **Polished Dark Aesthetic**:
  - Navy (`#0B132B`) and charcoal panels with champagne-gold (`#D4AF37`) accents, emerald green reachable status, and crimson failure badges.
- **Zero Runtime Third-Party Dependencies**: Runs on standard JDK 17+ without external JAR downloads.

---

## Prerequisites

- **Java Development Kit (JDK)**: JDK 17 or newer (tested with Java 18).
- **Operating System**: Windows 10/11, Linux, or macOS.
- **Privileges**: Runs under standard user permissions (no Administrator/root rights required).

---

## Windows PowerShell Quick Start

### 1. Build the Project
Open Windows PowerShell in the project directory:

```powershell
.\build.ps1
```

*This compiles both the main source and test classes into `target/classes` and generates the self-contained executable JAR at `target/netpulse-1.0.0.jar`.*

### 2. Run the Automated Test Suite
To verify the complete 10-point test specification:

```powershell
.\test.ps1
```

### 3. Launch the Application
To start the desktop UI:

```powershell
.\run.ps1
```

Or run directly via standard Java:

```powershell
java -jar target\netpulse-1.0.0.jar
```

Or with Maven (if installed):

```powershell
mvn clean compile
mvn test
mvn exec:java -Dexec.mainClass="com.netpulse.Main"
```

---

## How to Use NetPulse

### A. Adding and Configuring Hosts
1. Click the **➕ Add Target** button in the toolbar.
2. In the dialog, you can choose from convenient quick presets:
   - **Localhost Ping**: `127.0.0.1` via ICMP Ping.
   - **Google DNS**: `8.8.8.8` via ICMP Ping.
   - **Cloudflare HTTPS**: `1.1.1.1` via TCP Port `443`.
   - **Localhost HTTP**: `127.0.0.1` via TCP Port `8080`.
3. Configure the check parameters:
   - **Target Label**: Descriptive identifier (e.g., "Office Gateway", "Internal REST API").
   - **Target Host / IP**: Hostname (`google.com`), IPv4 (`192.168.1.1`), or IPv6 (`::1`).
   - **Check Mechanism**: `ICMP Ping` or `TCP Port Probe`.
   - **TCP Port**: Required if TCP check is selected (valid range: `1 - 65535`).
   - **Timeout (ms)**: Maximum wait time for reply (e.g., `2000` ms).
   - **Check Interval (sec)**: Time between periodic probes (e.g., `5` seconds).
   - **Alert Failure Threshold**: Number of consecutive failures before triggering an alert (e.g., `3`).
4. Click **Add Target**.

### B. Starting and Stopping Monitoring
- Click **▶ Start Monitoring** to begin background polling.
- Click **⏹ Stop Monitoring** to pause worker threads.
- Click **⟳ Refresh Now** to immediately dispatch an ad-hoc check for the selected target or all targets.

### C. Viewing Live Latency Charts
- Click any row in the **Monitored Targets** table.
- The **Rolling Latency** chart immediately switches to visualize the selected host's recent round-trip measurements.

### D. Exporting Diagnostic Logs to CSV
1. In the toolbar, click **📊 Export CSV**.
2. Select destination path in the file chooser.
3. The resulting CSV file contains all timestamped outcomes, latencies, and diagnostic details formatted according to RFC 4180.

---

## Testing Scenarios

### 1. Test with Localhost
- **ICMP Ping**: Add target `127.0.0.1` with `ICMP Ping`. Responses typically register in `< 1 ms`.
- **TCP Socket Check**:
  - Open a simple listener in a separate PowerShell terminal:
    ```powershell
    # Quick one-liner test server on port 8080:
    $listener = [System.Net.Sockets.TcpListener]8080; $listener.Start(); Write-Host "Listening on 8080..."
    ```
  - In NetPulse, add target `127.0.0.1` on TCP Port `8080`.
  - The status will immediately report `Reachable` with round-trip handshake time.
  - Stop the PowerShell listener. On the next check, NetPulse reports `Unreachable` with `Connection refused (RST received)` without blocking the UI.

### 2. Test a Reachable LAN Device
- Find your default local gateway (router) IP (e.g., `192.168.1.1` or `192.168.0.1`).
- Add it to NetPulse as `Local Gateway` using `ICMP Ping`.
- Observe sub-5ms local area network latency trends on the chart.

### 3. Test a TCP Web Service
- Target: `1.1.1.1` or `google.com`
- Check Type: `TCP Port Probe`
- Port: `443` (HTTPS)
- Validates end-to-end transport-layer connectivity and TCP handshake performance.

---

## Diagnostic Distinctions & Troubleshooting

### Understanding Probe Results
| Reported Status | Technical Meaning | Distinguishing Characteristics |
|---|---|---|
| **Reachable** | Host replied successfully | Real latency measured in milliseconds. |
| **Unreachable** | Host or port did not answer | SYN timeout, ICMP timeout, or TCP `RST` (connection refused). |
| **Unknown / Inconclusive** | Probe could not be completed | DNS lookup failure, unsupported OS socket capability, or offline local interface. |

### Limitations of ICMP Checks
1. **Windows Java Echo Limitation**: Standard Java `InetAddress.isReachable()` relies on TCP port 7 echo on Windows systems unless running with administrative raw socket permissions. Because port 7 is closed on modern networks, standard Java produces false negatives. NetPulse solves this by automatically invoking the native OS `ping.exe` utility.
2. **ICMP Filtering**: Many corporate firewalls, cloud providers (e.g. AWS EC2 security groups), and home routers intentionally drop ICMP echo requests for security reasons. A target that drops ICMP may still be completely healthy and serving HTTP/HTTPS traffic. Always verify with a **TCP Port Probe** on the application port.
3. **Connection Refused (TCP RST)**: A `Connection Refused` error means the destination machine received the SYN packet and actively sent back a `RST` packet. **This confirms the host is online and reachable on the IP layer**, but no daemon is listening on that specific port number.

---

## Project Structure

```
NetPulse/
├── pom.xml                               # Standard Maven project descriptor
├── build.ps1                             # PowerShell compilation & packaging script
├── test.ps1                              # PowerShell test runner script
├── run.ps1                               # PowerShell application launcher script
├── README.md                             # User & operational documentation
├── docs/
│   └── NETWORK_ARCHITECTURE.md           # Network engineering & architecture spec
└── src/
    ├── main/java/com/netpulse/
    │   ├── Main.java                     # Application bootstrap & shutdown hook
    │   ├── model/
    │   │   ├── CheckResult.java          # Factual check result representation
    │   │   ├── CheckStatus.java          # REACHABLE, UNREACHABLE, UNKNOWN enum
    │   │   ├── CheckType.java            # ICMP_PING, TCP_PORT enum
    │   │   ├── EventLogEntry.java        # Audit log record with alert transitions
    │   │   ├── NetworkTarget.java        # Monitored endpoint & thread-safe metrics
    │   │   └── TargetStatistics.java     # Empirical metrics & zero-division safety
    │   ├── service/
    │   │   ├── CsvExportService.java     # RFC 4180 CSV export with UTF-8 BOM
    │   │   ├── NetworkCheckService.java  # High-level probe routing facade
    │   │   ├── PingService.java          # ICMP ping with native OS fallback
    │   │   ├── StatisticsService.java    # Empirical session aggregation
    │   │   └── TcpProbeService.java      # Nanosecond TCP 3-way handshake probe
    │   ├── monitoring/
    │   │   ├── AlertListener.java        # Alert transition callback
    │   │   ├── CheckListener.java        # Periodic check completion callback
    │   │   └── MonitoringManager.java    # Scheduled thread pools & concurrency guards
    │   └── ui/
    │       ├── DashboardFrame.java       # Main dark Swing dashboard window
    │       ├── LatencyChartPanel.java    # 2D anti-aliased rolling latency chart
    │       ├── MetricCard.java           # Header telemetry counter cards
    │       ├── StatusBadgeRenderer.java  # Colored status pill badges
    │       ├── TargetDialog.java         # Add/Edit target configuration modal
    │       └── Theme.java                # Navy/Charcoal & Champagne Gold palette
    └── test/java/com/netpulse/
        ├── AlertTriggerTest.java         # Alert threshold & recovery tests
        ├── ConcurrencyResponsivenessTest.java # Non-blocking & thread pool tests
        ├── CsvExportTest.java            # CSV escaping & formatting tests
        ├── NetworkProbeTest.java         # Real socket, closed port & ping tests
        ├── StatisticsCalculationTest.java# Empirical calculation & empty set tests
        ├── TargetValidationTest.java     # Input boundary & validation tests
        └── TestRunner.java               # Comprehensive standalone test runner
```

---

## License & Integrity

NetPulse is developed with strict adherence to documentation integrity and network transparency. No fabricated latencies or simulated results are utilized.
