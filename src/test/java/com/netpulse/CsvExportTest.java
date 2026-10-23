package com.netpulse;

import com.netpulse.model.CheckStatus;
import com.netpulse.model.CheckType;
import com.netpulse.model.EventLogEntry;
import com.netpulse.service.CsvExportService;
import com.netpulse.test.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

import static com.netpulse.test.Assert.*;

/**
 * Tests RFC 4180 CSV generation and special character escaping.
 * Requirement 9: CSV export.
 */
public class CsvExportTest {

    private final CsvExportService csvExportService = new CsvExportService();

    @Test
    public void testCsvEscaping() {
        assertEquals("normalText", csvExportService.escapeCsvValue("normalText"));
        assertEquals("\"text,with,comma\"", csvExportService.escapeCsvValue("text,with,comma"));
        assertEquals("\"text\"\"with\"\"quotes\"", csvExportService.escapeCsvValue("text\"with\"quotes"));
        assertEquals("\"line1\nline2\"", csvExportService.escapeCsvValue("line1\nline2"));
        assertEquals("", csvExportService.escapeCsvValue(null));
    }

    @Test
    public void testExportEventsToCsvFile() throws IOException {
        File tempFile = File.createTempFile("netpulse-test-", ".csv");
        tempFile.deleteOnExit();

        EventLogEntry e1 = new EventLogEntry(
                Instant.now(), "Google DNS", "8.8.8.8", CheckType.ICMP_PING, 0,
                CheckStatus.REACHABLE, 14.2, "Echo reply OK", false, false
        );

        EventLogEntry e2 = EventLogEntry.alert("Internal API", "192.168.1.10", CheckType.TCP_PORT, 8080,
                3, "Connection refused");

        csvExportService.exportEventsToCsv(List.of(e1, e2), tempFile);

        assertTrue(tempFile.exists(), "CSV file must exist");
        assertTrue(tempFile.length() > 0, "CSV file must not be empty");

        List<String> lines = Files.readAllLines(tempFile.toPath());
        assertTrue(lines.size() >= 3, "File should have at least header and 2 records");

        String header = lines.get(0);
        assertTrue(header.contains("Timestamp") && header.contains("Target Label") && header.contains("Status"));

        String line1 = lines.get(1);
        assertTrue(line1.contains("Google DNS") && line1.contains("14.20"));

        String line2 = lines.get(2);
        assertTrue(line2.contains("Internal API") && line2.contains("ALERT"));
    }
}
