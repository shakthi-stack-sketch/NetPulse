package com.netpulse.service;

import com.netpulse.model.EventLogEntry;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Service for exporting network diagnostic event logs to RFC 4180-compliant CSV files.
 */
public class CsvExportService {

    private static final String CSV_HEADER = "Timestamp,Target Label,Host,Check Type,Port,Status,Response Time (ms),Event Details,Is Alert,Is Recovery";

    /**
     * Exports a list of event log entries to the specified file.
     *
     * @param events list of event log entries to export
     * @param destinationFile destination CSV file
     * @throws IOException on I/O failure
     */
    public void exportEventsToCsv(List<EventLogEntry> events, File destinationFile) throws IOException {
        Objects.requireNonNull(events, "events must not be null");
        Objects.requireNonNull(destinationFile, "destinationFile must not be null");

        // Ensure parent directory exists
        File parent = destinationFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(destinationFile), StandardCharsets.UTF_8))) {

            // UTF-8 Byte Order Mark (BOM) for seamless opening in Microsoft Excel
            writer.write("\uFEFF");
            writer.write(CSV_HEADER);
            writer.newLine();

            for (EventLogEntry entry : events) {
                String line = formatCsvRow(
                        entry.getFormattedTimestamp(),
                        entry.getTargetLabel(),
                        entry.getHost(),
                        entry.getCheckType().getDisplayName(),
                        entry.getPort() > 0 ? String.valueOf(entry.getPort()) : "N/A",
                        entry.getStatus().getDisplayName(),
                        entry.getResponseTimeMs().map(ms -> String.format("%.2f", ms)).orElse(""),
                        entry.getDetails(),
                        entry.isAlertEvent() ? "YES" : "NO",
                        entry.isRecoveryEvent() ? "YES" : "NO"
                );
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /**
     * Formats an array of string values into an escaped CSV row.
     */
    public String formatCsvRow(String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(escapeCsvValue(values[i]));
        }
        return sb.toString();
    }

    /**
     * Escapes a single CSV value according to RFC 4180 rules.
     */
    public String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        boolean containsSpecialChar = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");

        if (containsSpecialChar) {
            // Escape double quotes by doubling them
            String escaped = value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
        return value;
    }
}
