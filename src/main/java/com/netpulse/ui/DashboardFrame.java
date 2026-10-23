package com.netpulse.ui;

import com.netpulse.model.CheckResult;
import com.netpulse.model.CheckStatus;
import com.netpulse.model.CheckType;
import com.netpulse.model.EventLogEntry;
import com.netpulse.model.NetworkTarget;
import com.netpulse.model.TargetStatistics;
import com.netpulse.monitoring.AlertListener;
import com.netpulse.monitoring.CheckListener;
import com.netpulse.monitoring.MonitoringManager;
import com.netpulse.service.CsvExportService;
import com.netpulse.service.StatisticsService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main application window for NetPulse.
 * High-performance dark desktop dashboard coordinating live telemetry,
 * target management, rolling latency charts, and audit logging.
 */
public class DashboardFrame extends JFrame implements CheckListener, AlertListener {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MonitoringManager monitoringManager;
    private final StatisticsService statisticsService = new StatisticsService();
    private final CsvExportService csvExportService = new CsvExportService();

    // UI Metrics
    private MetricCard totalTargetsCard;
    private MetricCard reachableCard;
    private MetricCard failingCard;
    private MetricCard activeAlertsCard;
    private MetricCard successRateCard;
    private JLabel statusBanner;

    // Controls
    private JButton startBtn;
    private JButton stopBtn;
    private JButton refreshBtn;
    private JButton addBtn;
    private JButton editBtn;
    private JButton removeBtn;
    private JButton exportBtn;
    private JButton clearLogBtn;

    // Tables & Models
    private JTable targetsTable;
    private TargetsTableModel targetsTableModel;
    private LatencyChartPanel latencyChartPanel;

    private JTable eventTable;
    private EventTableModel eventTableModel;
    private JComboBox<String> eventFilterCombo;

    private JLabel footerStatusLabel;

    public DashboardFrame(MonitoringManager monitoringManager) {
        super("NetPulse — Network Health and Latency Monitoring System");
        this.monitoringManager = monitoringManager;

        monitoringManager.addCheckListener(this);
        monitoringManager.addAlertListener(this);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 720));
        setPreferredSize(new Dimension(1240, 800));
        getContentPane().setBackground(Theme.BG_DARK);
        setLayout(new BorderLayout());

        buildUI();

        // Setup default realistic targets so the user has immediate endpoints to monitor
        initDefaultTargets();

        pack();
        setLocationRelativeTo(null);
    }

    private void buildUI() {
        // 1. Top Panel: Title, Live Status Indicator, Telemetry Cards
        JPanel topContainer = new JPanel(new BorderLayout(0, 10));
        topContainer.setBackground(Theme.BG_DARK);
        topContainer.setBorder(BorderFactory.createEmptyBorder(14, 18, 10, 18));

        // Header Title Row
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);

        JLabel appTitle = new JLabel("NetPulse");
        appTitle.setFont(Theme.FONT_TITLE);
        appTitle.setForeground(Theme.GOLD_LIGHT);

        JLabel appSubtitle = new JLabel("  Network Diagnostics & Empirical Latency Monitor");
        appSubtitle.setFont(Theme.FONT_BODY);
        appSubtitle.setForeground(Theme.TEXT_SECONDARY);

        JPanel titleGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleGroup.setOpaque(false);
        titleGroup.add(appTitle);
        titleGroup.add(appSubtitle);

        statusBanner = new JLabel("● STOPPED (Click Start Monitoring)");
        statusBanner.setFont(Theme.FONT_BODY_BOLD);
        statusBanner.setForeground(Theme.GREY_UNKNOWN);
        statusBanner.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        titleRow.add(titleGroup, BorderLayout.WEST);
        titleRow.add(statusBanner, BorderLayout.EAST);
        topContainer.add(titleRow, BorderLayout.NORTH);

        // Telemetry Metric Cards
        JPanel metricsPanel = new JPanel(new GridLayout(1, 5, 12, 0));
        metricsPanel.setOpaque(false);

        totalTargetsCard = new MetricCard("Total Targets", "0", "Endpoints configured", Theme.GOLD);
        reachableCard = new MetricCard("Reachable", "0", "Responding to probes", Theme.GREEN_REACHABLE);
        failingCard = new MetricCard("Failing / Down", "0", "Connection failures", Theme.RED_FAIL);
        activeAlertsCard = new MetricCard("Active Alerts", "0", "Threshold violations", Theme.AMBER_WARN);
        successRateCard = new MetricCard("Observed Success", "0.0%", "Empirical check ratio", Theme.GOLD_LIGHT);

        metricsPanel.add(totalTargetsCard);
        metricsPanel.add(reachableCard);
        metricsPanel.add(failingCard);
        metricsPanel.add(activeAlertsCard);
        metricsPanel.add(successRateCard);
        topContainer.add(metricsPanel, BorderLayout.CENTER);

        // Action Toolbar Row
        JPanel toolbarPanel = createToolbar();
        topContainer.add(toolbarPanel, BorderLayout.SOUTH);

        add(topContainer, BorderLayout.NORTH);

        // 2. Center Panel: Split Pane (Upper: Targets Table & Latency Chart | Lower: Event Log)
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplitPane.setDividerLocation(360);
        mainSplitPane.setContinuousLayout(true);
        mainSplitPane.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        mainSplitPane.setBackground(Theme.BG_DARK);

        // Upper Section: Targets Table (Left) + Chart (Right)
        JSplitPane upperSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        upperSplitPane.setDividerLocation(700);
        upperSplitPane.setContinuousLayout(true);
        upperSplitPane.setBackground(Theme.BG_DARK);
        upperSplitPane.setBorder(null);

        JPanel targetsPanel = createTargetsPanel();
        latencyChartPanel = new LatencyChartPanel();

        upperSplitPane.setLeftComponent(targetsPanel);
        upperSplitPane.setRightComponent(latencyChartPanel);
        mainSplitPane.setTopComponent(upperSplitPane);

        // Lower Section: Event History Log Table with Filter & Export
        JPanel eventLogPanel = createEventLogPanel();
        mainSplitPane.setBottomComponent(eventLogPanel);

        add(mainSplitPane, BorderLayout.CENTER);

        // 3. Footer Status Bar
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(Theme.BG_PANEL);
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 18, 6, 18)
        ));

        footerStatusLabel = new JLabel("Engine ready. Select targets and click 'Start Monitoring'.");
        footerStatusLabel.setFont(Theme.FONT_BODY);
        footerStatusLabel.setForeground(Theme.TEXT_SECONDARY);

        JLabel legendLabel = new JLabel("All latency metrics are empirically measured in milliseconds. No simulated data.");
        legendLabel.setFont(Theme.FONT_BODY);
        legendLabel.setForeground(Theme.TEXT_MUTED);

        footerPanel.add(footerStatusLabel, BorderLayout.WEST);
        footerPanel.add(legendLabel, BorderLayout.EAST);
        add(footerPanel, BorderLayout.SOUTH);
    }

    private JPanel createToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setOpaque(false);

        startBtn = Theme.createStyledButton("▶ Start Monitoring", Theme.GREEN_BG, Theme.GREEN_REACHABLE, Theme.TEXT_PRIMARY);
        startBtn.addActionListener(e -> onStartMonitoring());

        stopBtn = Theme.createStyledButton("⏹ Stop Monitoring", Theme.RED_BG, Theme.RED_FAIL, Theme.TEXT_PRIMARY);
        stopBtn.setEnabled(false);
        stopBtn.addActionListener(e -> onStopMonitoring());

        refreshBtn = Theme.createStyledButton("⟳ Refresh Now", Theme.BG_INPUT, Theme.BORDER_COLOR, Theme.GOLD_LIGHT);
        refreshBtn.addActionListener(e -> onRefreshNow());

        addBtn = Theme.createStyledButton("➕ Add Target", Theme.GOLD, Theme.GOLD_LIGHT, Theme.BG_DARK);
        addBtn.addActionListener(e -> onAddTarget());

        editBtn = Theme.createStyledButton("✏ Edit Target", Theme.BG_CARD, Theme.BORDER_COLOR, Theme.TEXT_PRIMARY);
        editBtn.addActionListener(e -> onEditTarget());

        removeBtn = Theme.createStyledButton("🗑 Remove", Theme.BG_CARD, Theme.BORDER_COLOR, Theme.RED_FAIL);
        removeBtn.addActionListener(e -> onRemoveTarget());

        exportBtn = Theme.createStyledButton("📊 Export CSV", Theme.BG_INPUT, Theme.BORDER_COLOR, Theme.TEXT_PRIMARY);
        exportBtn.addActionListener(e -> onExportCsv());

        clearLogBtn = Theme.createStyledButton("🧹 Clear History", Theme.BG_INPUT, Theme.BORDER_COLOR, Theme.TEXT_MUTED);
        clearLogBtn.addActionListener(e -> onClearHistory());

        bar.add(startBtn);
        bar.add(stopBtn);
        bar.add(refreshBtn);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(addBtn);
        bar.add(editBtn);
        bar.add(removeBtn);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(exportBtn);
        bar.add(clearLogBtn);

        return bar;
    }

    private JPanel createTargetsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BG_PANEL);
        panel.setBorder(Theme.createPanelBorder());

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel title = new JLabel("Monitored Targets");
        title.setFont(Theme.FONT_SUBTITLE);
        title.setForeground(Theme.GOLD_LIGHT);
        headerPanel.add(title, BorderLayout.WEST);

        targetsTableModel = new TargetsTableModel();
        targetsTable = new JTable(targetsTableModel);
        Theme.styleTable(targetsTable);
        targetsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Column Renderers
        targetsTable.getColumnModel().getColumn(3).setCellRenderer(new StatusBadgeRenderer()); // Status
        DefaultTableCellRenderer monoRight = new DefaultTableCellRenderer();
        monoRight.setHorizontalAlignment(SwingConstants.RIGHT);
        monoRight.setFont(Theme.FONT_MONO);
        targetsTable.getColumnModel().getColumn(4).setCellRenderer(monoRight); // Latency
        targetsTable.getColumnModel().getColumn(5).setCellRenderer(monoRight); // Success %
        targetsTable.getColumnModel().getColumn(6).setCellRenderer(monoRight); // Failures

        // Row selection handler: updates the Latency Chart
        targetsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = targetsTable.getSelectedRow();
                if (row >= 0 && row < targetsTableModel.getRowCount()) {
                    NetworkTarget selected = targetsTableModel.getTargetAt(row);
                    latencyChartPanel.setTarget(selected);
                } else {
                    latencyChartPanel.setTarget(null);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(targetsTable);
        scrollPane.getViewport().setBackground(Theme.BG_PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR));

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createEventLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BG_PANEL);
        panel.setBorder(Theme.createPanelBorder());

        // Header with Filter Controls
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JLabel title = new JLabel("Diagnostic Event Audit Log");
        title.setFont(Theme.FONT_SUBTITLE);
        title.setForeground(Theme.GOLD_LIGHT);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filterPanel.setOpaque(false);
        JLabel filterLabel = new JLabel("Filter View:");
        filterLabel.setFont(Theme.FONT_BODY_BOLD);
        filterLabel.setForeground(Theme.TEXT_SECONDARY);

        eventFilterCombo = new JComboBox<>(new String[]{
                "All Check Outcomes", "Only Alerts & Recoveries", "Only Failures", "Reachable Checks"
        });
        eventFilterCombo.setBackground(Theme.BG_INPUT);
        eventFilterCombo.setForeground(Theme.TEXT_PRIMARY);
        eventFilterCombo.setFont(Theme.FONT_BODY);
        eventFilterCombo.addActionListener(e -> updateEventFilter());

        filterPanel.add(filterLabel);
        filterPanel.add(eventFilterCombo);

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(filterPanel, BorderLayout.EAST);

        eventTableModel = new EventTableModel();
        eventTable = new JTable(eventTableModel);
        Theme.styleTable(eventTable);

        // Highlight alert rows
        eventTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                EventLogEntry entry = eventTableModel.getEntryAt(row);
                if (entry != null) {
                    if (entry.isAlertEvent()) {
                        c.setForeground(Theme.RED_FAIL);
                        setFont(Theme.FONT_BODY_BOLD);
                    } else if (entry.isRecoveryEvent()) {
                        c.setForeground(Theme.GREEN_REACHABLE);
                        setFont(Theme.FONT_BODY_BOLD);
                    } else if (entry.getStatus() == CheckStatus.UNREACHABLE) {
                        c.setForeground(new Color(0xFC, 0x81, 0x81));
                    } else {
                        c.setForeground(Theme.TEXT_PRIMARY);
                        setFont(Theme.FONT_BODY);
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(eventTable);
        scrollPane.getViewport().setBackground(Theme.BG_PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR));

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void initDefaultTargets() {
        // Pre-populate with realistic targets: Localhost ICMP, Google DNS 8.8.8.8, Cloudflare 1.1.1.1 HTTPS 443
        NetworkTarget localhostPing = new NetworkTarget("Localhost Ping", "127.0.0.1", CheckType.ICMP_PING, 0);
        NetworkTarget googleDns = new NetworkTarget("Google DNS", "8.8.8.8", CheckType.ICMP_PING, 0);
        NetworkTarget cloudflareHttps = new NetworkTarget("Cloudflare Web", "1.1.1.1", CheckType.TCP_PORT, 443);

        monitoringManager.addTarget(localhostPing);
        monitoringManager.addTarget(googleDns);
        monitoringManager.addTarget(cloudflareHttps);

        refreshAllViews();
        // Select first target by default
        if (targetsTable.getRowCount() > 0) {
            targetsTable.setRowSelectionInterval(0, 0);
        }
    }

    private void onStartMonitoring() {
        monitoringManager.startMonitoring();
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        statusBanner.setText("● MONITORING RUNNING (Active)");
        statusBanner.setForeground(Theme.GREEN_REACHABLE);
        footerStatusLabel.setText("Monitoring active. Background threads probing targets concurrently.");
    }

    private void onStopMonitoring() {
        monitoringManager.stopMonitoring();
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        statusBanner.setText("● MONITORING PAUSED");
        statusBanner.setForeground(Theme.GREY_UNKNOWN);
        footerStatusLabel.setText("Monitoring paused. Background worker threads halted.");
    }

    private void onRefreshNow() {
        int selectedRow = targetsTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < targetsTableModel.getRowCount()) {
            NetworkTarget target = targetsTableModel.getTargetAt(selectedRow);
            monitoringManager.dispatchCheck(target, true);
            footerStatusLabel.setText("Triggered immediate check for: " + target.getLabel());
        } else {
            monitoringManager.triggerManualCheckAll();
            footerStatusLabel.setText("Triggered immediate check for all targets.");
        }
    }

    private void onAddTarget() {
        TargetDialog dialog = new TargetDialog(this, null);
        dialog.setVisible(true);
        if (dialog.isConfirmed() && dialog.getTarget() != null) {
            NetworkTarget target = dialog.getTarget();
            monitoringManager.addTarget(target);
            refreshAllViews();
            int newIndex = targetsTableModel.getRowCount() - 1;
            targetsTable.setRowSelectionInterval(newIndex, newIndex);
            footerStatusLabel.setText("Added target: " + target.getLabel());
        }
    }

    private void onEditTarget() {
        int selectedRow = targetsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a target to edit.",
                    "No Target Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        NetworkTarget target = targetsTableModel.getTargetAt(selectedRow);
        TargetDialog dialog = new TargetDialog(this, target);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            refreshAllViews();
            footerStatusLabel.setText("Updated target configuration: " + target.getLabel());
        }
    }

    private void onRemoveTarget() {
        int selectedRow = targetsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a target to remove.",
                    "No Target Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        NetworkTarget target = targetsTableModel.getTargetAt(selectedRow);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to remove target '" + target.getLabel() + "'?",
                "Confirm Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            monitoringManager.removeTarget(target.getId());
            refreshAllViews();
            latencyChartPanel.setTarget(null);
            footerStatusLabel.setText("Removed target: " + target.getLabel());
        }
    }

    private void onClearHistory() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Clear all session diagnostic events?",
                "Clear History", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            monitoringManager.clearEventHistory();
            updateEventFilter();
            footerStatusLabel.setText("Diagnostic event audit log cleared.");
        }
    }

    private void onExportCsv() {
        List<EventLogEntry> events = monitoringManager.getEventHistory();
        if (events.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No recorded event history to export yet.",
                    "Export CSV", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Network Diagnostics to CSV");
        fileChooser.setSelectedFile(new File("netpulse-diagnostics-" + System.currentTimeMillis() + ".csv"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".csv");
            }

            File finalFile = fileToSave;
            // Execute in background thread so EDT is never blocked
            new Thread(() -> {
                try {
                    csvExportService.exportEventsToCsv(events, finalFile);
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(DashboardFrame.this,
                            "Successfully exported " + events.size() + " events to:\n" + finalFile.getAbsolutePath(),
                            "CSV Export Complete", JOptionPane.INFORMATION_MESSAGE));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(DashboardFrame.this,
                            "Failed to export CSV file:\n" + ex.getMessage(),
                            "Export Error", JOptionPane.ERROR_MESSAGE));
                }
            }, "NetPulse-CsvExporter").start();
        }
    }

    private void refreshAllViews() {
        targetsTableModel.setTargets(monitoringManager.getTargets());
        updateTelemetryCards();
        updateEventFilter();
        latencyChartPanel.refresh();
    }

    private void updateTelemetryCards() {
        List<NetworkTarget> targets = monitoringManager.getTargets();
        StatisticsService.DashboardSummary summary = statisticsService.computeSummary(targets);

        totalTargetsCard.setValue(String.valueOf(summary.totalTargets()));
        reachableCard.setValue(String.valueOf(summary.reachableTargets()));
        failingCard.setValue(String.valueOf(summary.failingTargets()));
        activeAlertsCard.setValue(String.valueOf(summary.activeAlerts()));
        successRateCard.setValue(String.format("%.1f%%", summary.getGlobalSuccessRatePercent()));
    }

    private void updateEventFilter() {
        String filter = (String) eventFilterCombo.getSelectedItem();
        List<EventLogEntry> allEvents = monitoringManager.getEventHistory();
        List<EventLogEntry> filtered = new ArrayList<>();

        for (EventLogEntry entry : allEvents) {
            if ("Only Alerts & Recoveries".equals(filter)) {
                if (entry.isAlertEvent() || entry.isRecoveryEvent()) {
                    filtered.add(entry);
                }
            } else if ("Only Failures".equals(filter)) {
                if (entry.getStatus() == CheckStatus.UNREACHABLE) {
                    filtered.add(entry);
                }
            } else if ("Reachable Checks".equals(filter)) {
                if (entry.getStatus() == CheckStatus.REACHABLE) {
                    filtered.add(entry);
                }
            } else {
                filtered.add(entry); // All
            }
        }

        eventTableModel.setEntries(filtered);
    }

    // Callbacks from MonitoringManager
    @Override
    public void onCheckCompleted(NetworkTarget target, CheckResult result) {
        targetsTableModel.fireTableDataChanged();
        updateTelemetryCards();
        updateEventFilter();

        int selectedRow = targetsTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < targetsTableModel.getRowCount()) {
            NetworkTarget selected = targetsTableModel.getTargetAt(selectedRow);
            if (selected.getId().equals(target.getId())) {
                latencyChartPanel.refresh();
            }
        }

        footerStatusLabel.setText(String.format("Last probe: %s [%s] -> %s (latency: %s) at %s",
                target.getLabel(), target.getHost(), result.getStatus().getDisplayName(),
                result.getFormattedLatency(), TIME_FORMATTER.format(Instant.now())));
    }

    @Override
    public void onAlertTriggered(NetworkTarget target, int consecutiveFailures, String lastError) {
        targetsTableModel.fireTableDataChanged();
        updateTelemetryCards();
        updateEventFilter();
        footerStatusLabel.setText("⚠️ ALERT: " + target.getLabel() + " reached " + consecutiveFailures + " consecutive failures!");
    }

    @Override
    public void onAlertRecovered(NetworkTarget target, double latencyMs) {
        targetsTableModel.fireTableDataChanged();
        updateTelemetryCards();
        updateEventFilter();
        footerStatusLabel.setText("✅ RECOVERY: " + target.getLabel() + " is reachable again (" + String.format("%.1f", latencyMs) + " ms)");
    }

    // Table Models
    private static class TargetsTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Label", "Target Host / IP", "Probe Mechanism", "Status",
                "Last Latency", "Success Rate", "Failures", "Last Check Time"
        };
        private List<NetworkTarget> targets = new ArrayList<>();

        public void setTargets(List<NetworkTarget> targets) {
            this.targets = new ArrayList<>(targets);
            fireTableDataChanged();
        }

        public NetworkTarget getTargetAt(int row) {
            if (row >= 0 && row < targets.size()) {
                return targets.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return targets.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            NetworkTarget target = targets.get(rowIndex);
            TargetStatistics stats = target.getStatisticsSnapshot();
            CheckResult lastResult = target.getLastCheckResult();

            return switch (columnIndex) {
                case 0 -> (target.isAlertActive() ? "⚠️ " : "") + target.getLabel();
                case 1 -> target.getHost() + (target.getCheckType() == CheckType.TCP_PORT ? ":" + target.getPort() : "");
                case 2 -> target.getCheckType().getDisplayName();
                case 3 -> target.getCurrentStatus();
                case 4 -> (lastResult != null) ? lastResult.getFormattedLatency() : "—";
                case 5 -> stats.getFormattedSuccessRate();
                case 6 -> stats.getFailedChecks() + " (" + stats.getConsecutiveFailures() + " consec.)";
                case 7 -> (lastResult != null) ? TIME_FORMATTER.format(lastResult.getTimestamp()) : "Pending";
                default -> "";
            };
        }
    }

    private static class EventTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Timestamp", "Target", "Check Type", "Status", "Latency", "Event & Diagnostic Details"
        };
        private List<EventLogEntry> entries = new ArrayList<>();

        public void setEntries(List<EventLogEntry> entries) {
            this.entries = new ArrayList<>(entries);
            fireTableDataChanged();
        }

        public EventLogEntry getEntryAt(int row) {
            if (row >= 0 && row < entries.size()) {
                return entries.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            EventLogEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.getFormattedTimestamp();
                case 1 -> entry.getTargetLabel();
                case 2 -> entry.getCheckType().getDisplayName();
                case 3 -> entry.getStatus().getDisplayName();
                case 4 -> entry.getFormattedLatency();
                case 5 -> entry.getDetails();
                default -> "";
            };
        }
    }
}
