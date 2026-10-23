package com.netpulse.monitoring;

import com.netpulse.model.CheckResult;
import com.netpulse.model.EventLogEntry;
import com.netpulse.model.NetworkTarget;
import com.netpulse.service.NetworkCheckService;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates periodic asynchronous network checks, concurrency, alerting, and event audit logging.
 */
public class MonitoringManager {

    private static final int MAX_EVENT_HISTORY = 3000;

    private final NetworkCheckService checkService;
    private final List<NetworkTarget> targets = new CopyOnWriteArrayList<>();
    private final List<EventLogEntry> eventHistory = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Long> lastCheckTimestampMap = new ConcurrentHashMap<>();

    private final List<CheckListener> checkListeners = new CopyOnWriteArrayList<>();
    private final List<AlertListener> alertListeners = new CopyOnWriteArrayList<>();

    private final AtomicBoolean monitoring = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private ExecutorService workerPool;

    public MonitoringManager() {
        this(new NetworkCheckService());
    }

    public MonitoringManager(NetworkCheckService checkService) {
        this.checkService = Objects.requireNonNull(checkService, "checkService must not be null");
    }

    /**
     * Starts the monitoring engine. Spawns scheduler and worker thread pools.
     */
    public synchronized void startMonitoring() {
        if (monitoring.compareAndSet(false, true)) {
            int workerCount = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
            workerPool = Executors.newFixedThreadPool(workerCount, new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "NetPulse-Worker-" + count.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });

            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "NetPulse-Scheduler");
                t.setDaemon(true);
                return t;
            });

            // Master schedule tick: evaluates targets every 1 second
            scheduler.scheduleAtFixedRate(this::tickSchedule, 0, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * Stops the monitoring engine and shuts down thread pools gracefully.
     */
    public synchronized void stopMonitoring() {
        if (monitoring.compareAndSet(true, false)) {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                scheduler = null;
            }

            if (workerPool != null) {
                workerPool.shutdown();
                try {
                    if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                        workerPool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    workerPool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                workerPool = null;
            }
        }
    }

    public boolean isMonitoring() {
        return monitoring.get();
    }

    /**
     * Periodic tick callback invoked by the scheduler thread.
     */
    private void tickSchedule() {
        if (!monitoring.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (NetworkTarget target : targets) {
            if (!target.isEnabled()) {
                continue;
            }

            long lastCheck = lastCheckTimestampMap.getOrDefault(target.getId(), 0L);
            long intervalMillis = target.getIntervalSeconds() * 1000L;

            if (now - lastCheck >= intervalMillis) {
                dispatchCheck(target, false);
            }
        }
    }

    /**
     * Dispatches a check for a target if no check is currently in progress.
     */
    public boolean dispatchCheck(NetworkTarget target, boolean forceManual) {
        if (target == null) {
            return false;
        }

        // Prevent overlapping probes for the same target
        if (!target.getCheckInProgress().compareAndSet(false, true)) {
            return false; // Probe already active for this target
        }

        lastCheckTimestampMap.put(target.getId(), System.currentTimeMillis());

        ExecutorService pool = workerPool;
        if (pool == null || pool.isShutdown()) {
            // If monitoring is stopped but user triggered manual check, use an ad-hoc thread
            Thread manualThread = new Thread(() -> executeTargetProbe(target), "NetPulse-ManualCheck-" + target.getLabel());
            manualThread.setDaemon(true);
            manualThread.start();
            return true;
        }

        pool.submit(() -> executeTargetProbe(target));
        return true;
    }

    /**
     * Worker method executing on background thread.
     */
    private void executeTargetProbe(NetworkTarget target) {
        try {
            boolean wasAlertActiveBefore = target.isAlertActive();
            CheckResult result = checkService.executeCheck(target);

            // Update model and detect alert transitions
            boolean alertStateChanged = target.recordResult(result);

            // Record check event in history
            EventLogEntry checkEvent = EventLogEntry.fromCheckResult(result);
            addEventLog(checkEvent);

            // Handle alert transitions
            if (alertStateChanged) {
                if (target.isAlertActive()) {
                    // Alert newly triggered
                    EventLogEntry alertEvent = EventLogEntry.alert(
                            target.getLabel(),
                            target.getHost(),
                            target.getCheckType(),
                            target.getPort(),
                            target.getConsecutiveFailures(),
                            result.getDetails()
                    );
                    addEventLog(alertEvent);
                    notifyAlertTriggered(target, target.getConsecutiveFailures(), result.getDetails());
                } else if (wasAlertActiveBefore && result.isSuccess()) {
                    // Alert recovered
                    double latency = result.getResponseTimeMs().orElse(0.0);
                    EventLogEntry recoveryEvent = EventLogEntry.recovery(
                            target.getLabel(),
                            target.getHost(),
                            target.getCheckType(),
                            target.getPort(),
                            latency
                    );
                    addEventLog(recoveryEvent);
                    notifyAlertRecovered(target, latency);
                }
            }

            // Notify check completion
            notifyCheckCompleted(target, result);

        } finally {
            target.getCheckInProgress().set(false);
        }
    }

    private void addEventLog(EventLogEntry entry) {
        synchronized (eventHistory) {
            eventHistory.add(entry);
            if (eventHistory.size() > MAX_EVENT_HISTORY) {
                eventHistory.remove(0);
            }
        }
    }

    private void notifyCheckCompleted(NetworkTarget target, CheckResult result) {
        for (CheckListener listener : checkListeners) {
            SwingUtilities.invokeLater(() -> listener.onCheckCompleted(target, result));
        }
    }

    private void notifyAlertTriggered(NetworkTarget target, int consecutiveFailures, String error) {
        for (AlertListener listener : alertListeners) {
            SwingUtilities.invokeLater(() -> listener.onAlertTriggered(target, consecutiveFailures, error));
        }
    }

    private void notifyAlertRecovered(NetworkTarget target, double latencyMs) {
        for (AlertListener listener : alertListeners) {
            SwingUtilities.invokeLater(() -> listener.onAlertRecovered(target, latencyMs));
        }
    }

    // Target Management
    public void addTarget(NetworkTarget target) {
        Objects.requireNonNull(target, "target must not be null");
        targets.add(target);
        // Dispatch immediate initial check
        dispatchCheck(target, true);
    }

    public boolean removeTarget(String targetId) {
        if (targetId == null) {
            return false;
        }
        lastCheckTimestampMap.remove(targetId);
        return targets.removeIf(t -> t.getId().equals(targetId));
    }

    public List<NetworkTarget> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    public NetworkTarget getTargetById(String targetId) {
        for (NetworkTarget t : targets) {
            if (t.getId().equals(targetId)) {
                return t;
            }
        }
        return null;
    }

    public void triggerManualCheckAll() {
        for (NetworkTarget target : targets) {
            dispatchCheck(target, true);
        }
    }

    public List<EventLogEntry> getEventHistory() {
        synchronized (eventHistory) {
            return new ArrayList<>(eventHistory);
        }
    }

    public void clearEventHistory() {
        synchronized (eventHistory) {
            eventHistory.clear();
        }
    }

    public void addCheckListener(CheckListener listener) {
        checkListeners.add(listener);
    }

    public void removeCheckListener(CheckListener listener) {
        checkListeners.remove(listener);
    }

    public void addAlertListener(AlertListener listener) {
        alertListeners.add(listener);
    }

    public void removeAlertListener(AlertListener listener) {
        alertListeners.remove(listener);
    }

    public void shutdown() {
        stopMonitoring();
    }
}
