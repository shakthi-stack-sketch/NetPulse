package com.netpulse.ui;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;

/**
 * Metric card component for top-level dashboard telemetry counters.
 */
public class MetricCard extends JPanel {

    private final JLabel titleLabel;
    private final JLabel valueLabel;
    private final JLabel subtitleLabel;
    private final Color accentColor;

    public MetricCard(String title, String initialValue, String subtitle, Color accentColor) {
        this.accentColor = accentColor;
        setLayout(new BorderLayout(4, 4));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 14, 10, 14));
        setPreferredSize(new Dimension(170, 78));

        titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(Theme.FONT_BODY_BOLD);
        titleLabel.setForeground(Theme.TEXT_MUTED);

        valueLabel = new JLabel(initialValue);
        valueLabel.setFont(Theme.FONT_METRIC);
        valueLabel.setForeground(accentColor);

        subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(Theme.FONT_BODY);
        subtitleLabel.setForeground(Theme.TEXT_SECONDARY);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        centerPanel.setOpaque(false);
        centerPanel.add(valueLabel);
        centerPanel.add(subtitleLabel);

        add(titleLabel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public void setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g2.setColor(Theme.BG_CARD);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

        // Left accent indicator bar
        g2.setColor(accentColor);
        g2.fillRoundRect(0, 0, 4, getHeight(), 4, 4);

        // Border
        g2.setColor(Theme.BORDER_COLOR);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

        g2.dispose();
        super.paintComponent(g);
    }
}
