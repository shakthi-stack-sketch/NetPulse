package com.netpulse.ui;

import com.netpulse.model.NetworkTarget;
import com.netpulse.model.TargetStatistics;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Rolling response-time telemetry chart painted with Java Swing Graphics2D.
 * Visualizes genuine measured round-trip latencies over time.
 * Clearly displays a prompt when no empirical measurements exist.
 */
public class LatencyChartPanel extends JPanel {

    private final JLabel titleLabel;
    private final JLabel statsLabel;
    private final ChartCanvas canvas;

    private volatile NetworkTarget currentTarget;

    public LatencyChartPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        titleLabel = new JLabel("Rolling Latency (Select a Target)");
        titleLabel.setFont(Theme.FONT_SUBTITLE);
        titleLabel.setForeground(Theme.GOLD_LIGHT);

        statsLabel = new JLabel("Min: — | Avg: — | Max: —");
        statsLabel.setFont(Theme.FONT_MONO);
        statsLabel.setForeground(Theme.TEXT_SECONDARY);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statsLabel, BorderLayout.EAST);

        canvas = new ChartCanvas();

        add(headerPanel, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        setPreferredSize(new Dimension(420, 240));
    }

    /**
     * Updates the chart with a target's latest data.
     */
    public void setTarget(NetworkTarget target) {
        this.currentTarget = target;
        if (target != null) {
            titleLabel.setText("Latency Trend: " + target.getLabel() + " (" + target.getHost() + ")");
            TargetStatistics stats = target.getStatisticsSnapshot();
            statsLabel.setText(String.format("Min: %s | Avg: %s | Max: %s",
                    stats.getFormattedMinLatency(),
                    stats.getFormattedAvgLatency(),
                    stats.getFormattedMaxLatency()));
        } else {
            titleLabel.setText("Rolling Latency (No Target Selected)");
            statsLabel.setText("Min: — | Avg: — | Max: —");
        }
        canvas.repaint();
    }

    public void refresh() {
        if (currentTarget != null) {
            TargetStatistics stats = currentTarget.getStatisticsSnapshot();
            statsLabel.setText(String.format("Min: %s | Avg: %s | Max: %s",
                    stats.getFormattedMinLatency(),
                    stats.getFormattedAvgLatency(),
                    stats.getFormattedMaxLatency()));
        }
        canvas.repaint();
    }

    /**
     * Internal canvas for high-performance anti-aliased 2D chart rendering.
     */
    private class ChartCanvas extends JPanel {
        public ChartCanvas() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            int padLeft = 55;
            int padRight = 20;
            int padTop = 20;
            int padBottom = 30;

            int plotWidth = width - padLeft - padRight;
            int plotHeight = height - padTop - padBottom;

            // Background plot area
            g2.setColor(Theme.BG_PANEL);
            g2.fillRoundRect(padLeft, padTop, plotWidth, plotHeight, 6, 6);
            g2.setColor(Theme.BORDER_COLOR);
            g2.drawRoundRect(padLeft, padTop, plotWidth, plotHeight, 6, 6);

            NetworkTarget target = currentTarget;
            List<Double> points = (target != null) ? target.getRollingLatencySnapshot() : null;

            // Check if there are valid measured points
            boolean hasData = false;
            double maxVal = 10.0; // minimum scale

            if (points != null && !points.isEmpty()) {
                for (Double val : points) {
                    if (val != null && val >= 0) {
                        hasData = true;
                        if (val > maxVal) {
                            maxVal = val;
                        }
                    }
                }
            }

            if (!hasData) {
                // Render informative empty-state message
                g2.setFont(Theme.FONT_BODY_BOLD);
                g2.setColor(Theme.GREY_UNKNOWN);
                String msg1 = (target == null)
                        ? "Select a target from the table above to view live latency telemetry"
                        : "No empirical latency measurements recorded yet";
                String msg2 = (target != null)
                        ? "Click 'Refresh Now' or verify that the target is reachable"
                        : "Probes will plot actual round-trip times in milliseconds";

                FontMetrics fm = g2.getFontMetrics();
                int x1 = padLeft + (plotWidth - fm.stringWidth(msg1)) / 2;
                int y1 = padTop + plotHeight / 2 - 8;
                g2.drawString(msg1, x1, y1);

                g2.setFont(Theme.FONT_BODY);
                g2.setColor(Theme.TEXT_MUTED);
                fm = g2.getFontMetrics();
                int x2 = padLeft + (plotWidth - fm.stringWidth(msg2)) / 2;
                int y2 = y1 + 20;
                g2.drawString(msg2, x2, y2);

                g2.dispose();
                return;
            }

            // Round maxVal up to pleasant step (e.g. 20, 50, 100, 200, 500, etc.)
            maxVal = computeNiceMax(maxVal);

            // Draw Y-axis grid lines and labels
            g2.setFont(Theme.FONT_MONO);
            g2.setColor(Theme.BORDER_COLOR);
            BasicStroke dashedStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10.0f, new float[]{3.0f, 3.0f}, 0.0f);

            int gridSteps = 4;
            for (int i = 0; i <= gridSteps; i++) {
                int y = padTop + plotHeight - (int) ((double) i / gridSteps * plotHeight);
                double valAtY = (maxVal / gridSteps) * i;

                if (i > 0 && i < gridSteps) {
                    g2.setStroke(dashedStroke);
                    g2.drawLine(padLeft, y, padLeft + plotWidth, y);
                }

                // Y-axis label
                g2.setColor(Theme.TEXT_MUTED);
                String label = String.format("%.0f ms", valAtY);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, padLeft - fm.stringWidth(label) - 8, y + 4);
                g2.setColor(Theme.BORDER_COLOR);
            }

            // Plot points
            int count = points.size();
            double xStep = (count > 1) ? ((double) plotWidth / (count - 1)) : plotWidth;

            Path2D.Double linePath = new Path2D.Double();
            Polygon fillPoly = new Polygon();
            fillPoly.addPoint(padLeft, padTop + plotHeight);

            List<Point> validPoints = new ArrayList<>();
            boolean pathStarted = false;

            for (int i = 0; i < count; i++) {
                Double val = points.get(i);
                int x = padLeft + (int) (i * xStep);

                if (val != null && val >= 0) {
                    double clampedVal = Math.min(val, maxVal);
                    int y = padTop + plotHeight - (int) ((clampedVal / maxVal) * plotHeight);
                    validPoints.add(new Point(x, y));

                    if (!pathStarted) {
                        linePath.moveTo(x, y);
                        fillPoly.addPoint(x, y);
                        pathStarted = true;
                    } else {
                        linePath.lineTo(x, y);
                        fillPoly.addPoint(x, y);
                    }
                } else {
                    // Packet loss / timeout marker
                    int yBottom = padTop + plotHeight - 3;
                    g2.setColor(Theme.RED_FAIL);
                    g2.fillRect(x - 2, yBottom - 6, 4, 6);
                }
            }

            if (pathStarted && !validPoints.isEmpty()) {
                Point lastPt = validPoints.get(validPoints.size() - 1);
                fillPoly.addPoint(lastPt.x, padTop + plotHeight);

                // Gradient fill underneath curve
                GradientPaint fillGradient = new GradientPaint(
                        0, padTop, new Color(Theme.GOLD.getRed(), Theme.GOLD.getGreen(), Theme.GOLD.getBlue(), 60),
                        0, padTop + plotHeight, new Color(Theme.GOLD.getRed(), Theme.GOLD.getGreen(), Theme.GOLD.getBlue(), 0)
                );
                g2.setPaint(fillGradient);
                g2.fill(fillPoly);

                // Line stroke
                g2.setColor(Theme.GOLD);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(linePath);

                // Data point dots
                for (Point p : validPoints) {
                    g2.setColor(Theme.BG_DARK);
                    g2.fillOval(p.x - 4, p.y - 4, 8, 8);
                    g2.setColor(Theme.GOLD_LIGHT);
                    g2.fillOval(p.x - 3, p.y - 3, 6, 6);
                }

                // Highlight latest point
                Point latest = validPoints.get(validPoints.size() - 1);
                g2.setColor(Theme.GREEN_REACHABLE);
                g2.fillOval(latest.x - 5, latest.y - 5, 10, 10);
                g2.setColor(Color.WHITE);
                g2.fillOval(latest.x - 2, latest.y - 2, 4, 4);
            }

            // X-axis label
            g2.setFont(Theme.FONT_BODY);
            g2.setColor(Theme.TEXT_MUTED);
            String xLabel = "Last " + count + " Observations (Periodic Window)";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(xLabel, padLeft + (plotWidth - fm.stringWidth(xLabel)) / 2, height - 8);

            g2.dispose();
        }

        private double computeNiceMax(double val) {
            if (val <= 10.0) return 15.0;
            if (val <= 25.0) return 30.0;
            if (val <= 50.0) return 60.0;
            if (val <= 100.0) return 120.0;
            if (val <= 250.0) return 300.0;
            if (val <= 500.0) return 600.0;
            if (val <= 1000.0) return 1200.0;
            return Math.ceil(val * 1.2 / 100.0) * 100.0;
        }
    }
}
