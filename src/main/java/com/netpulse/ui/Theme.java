package com.netpulse.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Modern Dark UI Theme constants and styling utilities for NetPulse.
 * Navy/charcoal backgrounds with champagne-gold accents, green reachable,
 * red failure, and muted grey unknown indicators.
 */
public final class Theme {

    private Theme() {}

    // Color Palette
    public static final Color BG_DARK = new Color(0x0B, 0x13, 0x2B);          // #0B132B Deep Navy
    public static final Color BG_PANEL = new Color(0x15, 0x1E, 0x36);         // #151E36 Dark Navy-Charcoal
    public static final Color BG_CARD = new Color(0x1C, 0x28, 0x48);          // #1C2848 Charcoal Card
    public static final Color BG_INPUT = new Color(0x25, 0x34, 0x5E);         // #25345E Input Fields
    public static final Color BG_TABLE_ALT = new Color(0x11, 0x1A, 0x30);     // #111A30 Table Stripe
    public static final Color BG_TABLE_SELECT = new Color(0x2C, 0x41, 0x77);  // #2C4177 Table Selection
    public static final Color BORDER_COLOR = new Color(0x2D, 0x3D, 0x66);     // #2D3D66 Subtle Border

    // Accent & Status Colors
    public static final Color GOLD = new Color(0xD4, 0xAF, 0x37);             // #D4AF37 Champagne Gold
    public static final Color GOLD_LIGHT = new Color(0xF3, 0xE5, 0xAB);       // #F3E5AB Pale Gold
    public static final Color GREEN_REACHABLE = new Color(0x10, 0xB9, 0x81);  // #10B981 Emerald Green
    public static final Color GREEN_BG = new Color(0x06, 0x4E, 0x3B);         // #064E3B Dark Green Tint
    public static final Color RED_FAIL = new Color(0xEF, 0x44, 0x44);         // #EF4444 Crimson Red
    public static final Color RED_BG = new Color(0x7F, 0x1D, 0x1D);           // #7F1D1D Dark Red Tint
    public static final Color AMBER_WARN = new Color(0xF5, 0x9E, 0x0B);       // #F59E0B Amber
    public static final Color GREY_UNKNOWN = new Color(0x94, 0xA3, 0xB8);     // #94A3B8 Muted Slate Grey

    // Typography Colors
    public static final Color TEXT_PRIMARY = new Color(0xF8, 0xFA, 0xFC);     // High contrast white
    public static final Color TEXT_SECONDARY = new Color(0x94, 0xA3, 0xB8);   // Muted slate
    public static final Color TEXT_MUTED = new Color(0x64, 0x74, 0x8B);       // Darker slate

    // Fonts
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_BODY_BOLD = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);
    public static final Font FONT_MONO_BOLD = new Font("Consolas", Font.BOLD, 12);
    public static final Font FONT_METRIC = new Font("Segoe UI", Font.BOLD, 24);

    public static Border createPanelBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        );
    }

    public static Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        );
    }

    /**
     * Creates a styled modern button with hover glow and rounded aesthetics.
     */
    public static JButton createStyledButton(String text, Color baseColor, Color hoverColor, Color textColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(baseColor.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(hoverColor);
                } else {
                    g2.setColor(baseColor);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

                // Champagne gold border for gold buttons or subtle border for others
                if (baseColor.equals(GOLD)) {
                    g2.setColor(GOLD_LIGHT);
                } else {
                    g2.setColor(BORDER_COLOR);
                }
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.setFont(FONT_BODY_BOLD);
        btn.setForeground(textColor);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
        return btn;
    }

    /**
     * Styles a JTable with dark rows, custom header, and alternating stripes.
     */
    public static void styleTable(JTable table) {
        table.setBackground(BG_PANEL);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(BORDER_COLOR);
        table.setFont(FONT_BODY);
        table.setRowHeight(28);
        table.setSelectionBackground(BG_TABLE_SELECT);
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);

        table.getTableHeader().setBackground(BG_CARD);
        table.getTableHeader().setForeground(GOLD_LIGHT);
        table.getTableHeader().setFont(FONT_BODY_BOLD);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        table.getTableHeader().setPreferredSize(new Dimension(table.getWidth(), 32));
    }
}
