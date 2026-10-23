package com.netpulse;

import com.netpulse.monitoring.MonitoringManager;
import com.netpulse.ui.DashboardFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * NetPulse Application Entry Point.
 * Initializes the monitoring core and launches the dark Swing desktop dashboard.
 */
public class Main {

    public static void main(String[] args) {
        // Configure native font rendering on Windows/Linux/macOS
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        try {
            // Use cross-platform or system look and feel
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        MonitoringManager monitoringManager = new MonitoringManager();

        // Register clean JVM shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down NetPulse monitoring background executors...");
            monitoringManager.shutdown();
        }, "NetPulse-ShutdownHook"));

        // Launch UI strictly on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            DashboardFrame dashboard = new DashboardFrame(monitoringManager);
            dashboard.setVisible(true);
        });
    }
}
