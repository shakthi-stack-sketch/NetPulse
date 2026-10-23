package com.netpulse.ui;

import com.netpulse.model.CheckStatus;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Custom table cell renderer displaying modern colored pill badges for CheckStatus.
 */
public class StatusBadgeRenderer extends DefaultTableCellRenderer {

    private CheckStatus currentStatus = CheckStatus.UNKNOWN;

    public StatusBadgeRenderer() {
        setHorizontalAlignment(SwingConstants.CENTER);
        setOpaque(false);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value instanceof CheckStatus status) {
            currentStatus = status;
            label.setText(status.getDisplayName());
            label.setFont(Theme.FONT_BODY_BOLD);

            switch (status) {
                case REACHABLE -> label.setForeground(Theme.GREEN_REACHABLE);
                case UNREACHABLE -> label.setForeground(Theme.RED_FAIL);
                case UNKNOWN -> label.setForeground(Theme.GREY_UNKNOWN);
            }
        } else if (value != null) {
            label.setText(value.toString());
            label.setForeground(Theme.TEXT_PRIMARY);
        }

        return label;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int padX = 6;
        int padY = 4;
        int width = getWidth() - (padX * 2);
        int height = getHeight() - (padY * 2);

        // Fill pill background
        switch (currentStatus) {
            case REACHABLE -> g2.setColor(Theme.GREEN_BG);
            case UNREACHABLE -> g2.setColor(Theme.RED_BG);
            case UNKNOWN -> g2.setColor(Theme.BG_INPUT);
        }
        g2.fillRoundRect(padX, padY, width, height, height, height);

        // Subtle border
        switch (currentStatus) {
            case REACHABLE -> g2.setColor(Theme.GREEN_REACHABLE);
            case UNREACHABLE -> g2.setColor(Theme.RED_FAIL);
            case UNKNOWN -> g2.setColor(Theme.BORDER_COLOR);
        }
        g2.drawRoundRect(padX, padY, width - 1, height - 1, height, height);

        g2.dispose();
        super.paintComponent(g);
    }
}
