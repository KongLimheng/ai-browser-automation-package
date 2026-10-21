package com.automationanywhere.botcommand.aibrowser;

import com.automationanywhere.botcommand.aibrowser.logging.ExecutionLogger;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ExecutionLoggerTest {

    @Test
    public void testDefaultTempLogFileCreation() throws Exception {
        String logPath;
        try (ExecutionLogger logger = new ExecutionLogger(null)) {
            logger.logSessionStart("Search product", "gemini-3.8-flash", "CHROME", true, "https://example.com", 2);
            logger.logStepStart(1, 10, "https://example.com");
            logger.logActionExecution(1, "CLICK", "Element #3");
            logger.logActionSuccess(1, "CLICK", 350);
            logger.logSessionEnd("SUCCESS", 1, 1200, "Found product", "/tmp/shot.png");
            logPath = logger.getLogFilePath();
        }

        Assert.assertNotNull(logPath);
        Path path = Paths.get(logPath);
        Assert.assertTrue(Files.exists(path), "Default log file should exist");

        List<String> lines = Files.readAllLines(path);
        Assert.assertTrue(lines.size() >= 5);
        Assert.assertTrue(lines.stream().anyMatch(l -> l.contains("gemini-3.8-flash")));
        Assert.assertTrue(lines.stream().anyMatch(l -> l.contains("Action [CLICK] completed successfully")));
        Assert.assertTrue(lines.stream().anyMatch(l -> l.contains("Overall Status: SUCCESS")));

        // Cleanup
        Files.deleteIfExists(path);
    }

    @Test
    public void testCustomLogFilePathWithAutoDirCreation() throws Exception {
        Path tempDir = Files.createTempDirectory("ai_custom_log_test_");
        Path customLog = tempDir.resolve("sub_folder").resolve("agent_run.log");

        try (ExecutionLogger logger = new ExecutionLogger(customLog.toString())) {
            logger.logSessionStart("Automate checkout", "gemini-2.5-flash", "CHROME", false, "", 1);
            logger.logActionFailure(2, "CLICK", "Locator timeout 5000ms", 5010);
            logger.logSessionEnd("FAILED", 2, 6000, "Could not click checkout", "");
        }

        Assert.assertTrue(Files.exists(customLog), "Custom log file in nested folder should exist");
        List<String> lines = Files.readAllLines(customLog);
        Assert.assertTrue(lines.stream().anyMatch(l -> l.contains("Action [CLICK] FAILED after 5010 ms: Locator timeout 5000ms")));
        Assert.assertTrue(lines.stream().anyMatch(l -> l.contains("Overall Status: FAILED")));

        // Cleanup
        Files.deleteIfExists(customLog);
        Files.deleteIfExists(tempDir.resolve("sub_folder"));
        Files.deleteIfExists(tempDir);
    }
}
