package com.netpulse.ui;

import com.netpulse.model.CheckType;
import com.netpulse.model.NetworkTarget;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Modal dialog for creating and editing monitored network targets.
 */
public class TargetDialog extends JDialog {

    private final JTextField labelField;
    private final JTextField hostField;
    private final JComboBox<CheckType> checkTypeCombo;
    private final JSpinner portSpinner;
    private final JSpinner timeoutSpinner;
    private final JSpinner intervalSpinner;
    private final JSpinner thresholdSpinner;
    private final JLabel errorLabel;

    private boolean confirmed = false;
    private NetworkTarget resultTarget = null;
    private final NetworkTarget existingTarget;

    public TargetDialog(JFrame parent, NetworkTarget existing) {
        super(parent, existing == null ? "Add Network Target" : "Edit Network Target", true);
        this.existingTarget = existing;

        getContentPane().setBackground(Theme.BG_DARK);
        setLayout(new BorderLayout());
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(Theme.BG_DARK);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // Presets Panel (helpful quick setups)
        if (existing == null) {
            JPanel presetsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            presetsPanel.setOpaque(false);
            JLabel presetLabel = new JLabel("Presets:");
            presetLabel.setFont(Theme.FONT_BODY_BOLD);
            presetLabel.setForeground(Theme.GOLD_LIGHT);
            presetsPanel.add(presetLabel);

            JButton btnLocalPing = createPresetButton("Localhost Ping", () -> fillForm("Localhost ICMP", "127.0.0.1", CheckType.ICMP_PING, 80));
            JButton btnGoogleDns = createPresetButton("Google DNS (8.8.8.8)", () -> fillForm("Google DNS", "8.8.8.8", CheckType.ICMP_PING, 80));
            JButton btnCloudflareHttps = createPresetButton("Cloudflare HTTPS (443)", () -> fillForm("Cloudflare Web", "1.1.1.1", CheckType.TCP_PORT, 443));
            JButton btnLocalHttp = createPresetButton("Localhost HTTP (8080)", () -> fillForm("Localhost Dev Server", "127.0.0.1", CheckType.TCP_PORT, 8080));

            presetsPanel.add(btnLocalPing);
            presetsPanel.add(btnGoogleDns);
            presetsPanel.add(btnCloudflareHttps);
            presetsPanel.add(btnLocalHttp);
            mainPanel.add(presetsPanel);
            mainPanel.add(Box.createVerticalStrut(10));
        }

        // Form Fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Theme.BG_PANEL);
        formPanel.setBorder(Theme.createPanelBorder());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        labelField = createStyledTextField(existing != null ? existing.getLabel() : "Localhost Ping");
        hostField = createStyledTextField(existing != null ? existing.getHost() : "127.0.0.1");

        checkTypeCombo = new JComboBox<>(CheckType.values());
        checkTypeCombo.setBackground(Theme.BG_INPUT);
        checkTypeCombo.setForeground(Theme.TEXT_PRIMARY);
        checkTypeCombo.setFont(Theme.FONT_BODY);
        if (existing != null) {
            checkTypeCombo.setSelectedItem(existing.getCheckType());
        }

        portSpinner = new JSpinner(new SpinnerNumberModel(
                existing != null && existing.getPort() > 0 ? existing.getPort() : 80, 1, 65535, 1));
        styleSpinner(portSpinner);

        timeoutSpinner = new JSpinner(new SpinnerNumberModel(
                existing != null ? existing.getTimeoutMs() : 2000, 100, 30000, 250));
        styleSpinner(timeoutSpinner);

        intervalSpinner = new JSpinner(new SpinnerNumberModel(
                existing != null ? existing.getIntervalSeconds() : 5, 1, 3600, 1));
        styleSpinner(intervalSpinner);

        thresholdSpinner = new JSpinner(new SpinnerNumberModel(
                existing != null ? existing.getConsecutiveFailureThreshold() : 3, 1, 100, 1));
        styleSpinner(thresholdSpinner);

        // Port enabled state toggles with check type
        checkTypeCombo.addActionListener(e -> {
            boolean isTcp = checkTypeCombo.getSelectedItem() == CheckType.TCP_PORT;
            portSpinner.setEnabled(isTcp);
        });
        portSpinner.setEnabled(checkTypeCombo.getSelectedItem() == CheckType.TCP_PORT);

        int row = 0;
        addFormRow(formPanel, gbc, row++, "Descriptive Label:", labelField);
        addFormRow(formPanel, gbc, row++, "Target Host / IP:", hostField);
        addFormRow(formPanel, gbc, row++, "Check Mechanism:", checkTypeCombo);
        addFormRow(formPanel, gbc, row++, "TCP Port (1-65535):", portSpinner);
        addFormRow(formPanel, gbc, row++, "Timeout (ms):", timeoutSpinner);
        addFormRow(formPanel, gbc, row++, "Check Interval (sec):", intervalSpinner);
        addFormRow(formPanel, gbc, row++, "Alert Failure Threshold:", thresholdSpinner);

        mainPanel.add(formPanel);

        // Error message label
        errorLabel = new JLabel(" ");
        errorLabel.setFont(Theme.FONT_BODY);
        errorLabel.setForeground(Theme.RED_FAIL);
        errorLabel.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        mainPanel.add(errorLabel);

        add(mainPanel, BorderLayout.CENTER);

        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        buttonPanel.setBackground(Theme.BG_DARK);

        JButton cancelBtn = Theme.createStyledButton("Cancel", Theme.BG_INPUT, Theme.BORDER_COLOR, Theme.TEXT_PRIMARY);
        cancelBtn.addActionListener(e -> dispose());

        JButton saveBtn = Theme.createStyledButton(
                existing == null ? "Add Target" : "Save Changes",
                Theme.GOLD, Theme.GOLD_LIGHT, Theme.BG_DARK
        );
        saveBtn.addActionListener(e -> onSubmit());

        buttonPanel.add(cancelBtn);
        buttonPanel.add(saveBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }

    private void fillForm(String label, String host, CheckType type, int port) {
        labelField.setText(label);
        hostField.setText(host);
        checkTypeCombo.setSelectedItem(type);
        portSpinner.setValue(port);
        portSpinner.setEnabled(type == CheckType.TCP_PORT);
    }

    private JButton createPresetButton(String text, Runnable action) {
        JButton btn = Theme.createStyledButton(text, Theme.BG_PANEL, Theme.BG_CARD, Theme.GOLD_LIGHT);
        btn.setFont(Theme.FONT_BODY);
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String labelText, javax.swing.JComponent comp) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.35;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(Theme.FONT_BODY_BOLD);
        lbl.setForeground(Theme.TEXT_SECONDARY);
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.65;
        panel.add(comp, gbc);
    }

    private JTextField createStyledTextField(String text) {
        JTextField tf = new JTextField(text, 22);
        tf.setBackground(Theme.BG_INPUT);
        tf.setForeground(Theme.TEXT_PRIMARY);
        tf.setCaretColor(Theme.GOLD);
        tf.setFont(Theme.FONT_BODY);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        return tf;
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setFont(Theme.FONT_MONO);
        spinner.setPreferredSize(new Dimension(100, 28));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(Theme.BG_INPUT);
            de.getTextField().setForeground(Theme.TEXT_PRIMARY);
            de.getTextField().setCaretColor(Theme.GOLD);
        }
    }

    private void onSubmit() {
        String label = labelField.getText().trim();
        String host = hostField.getText().trim();
        CheckType checkType = (CheckType) checkTypeCombo.getSelectedItem();
        int port = (int) portSpinner.getValue();
        int timeoutMs = (int) timeoutSpinner.getValue();
        int intervalSeconds = (int) intervalSpinner.getValue();
        int failureThreshold = (int) thresholdSpinner.getValue();

        String validationError = NetworkTarget.validate(label, host, checkType, port, timeoutMs, intervalSeconds, failureThreshold);
        if (validationError != null) {
            errorLabel.setText(validationError);
            return;
        }

        if (existingTarget != null) {
            existingTarget.setLabel(label);
            existingTarget.setHost(host);
            existingTarget.setCheckType(checkType);
            existingTarget.setPort(port);
            existingTarget.setTimeoutMs(timeoutMs);
            existingTarget.setIntervalSeconds(intervalSeconds);
            existingTarget.setConsecutiveFailureThreshold(failureThreshold);
            resultTarget = existingTarget;
        } else {
            resultTarget = new NetworkTarget(
                    null, label, host, checkType, port, timeoutMs, intervalSeconds, failureThreshold
            );
        }

        confirmed = true;
        dispose();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public NetworkTarget getTarget() {
        return resultTarget;
    }
}
