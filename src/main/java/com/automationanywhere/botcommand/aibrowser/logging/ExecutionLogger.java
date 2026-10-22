package com.automationanywhere.botcommand.aibrowser.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExecutionLogger implements AutoCloseable {

    private static final Logger log4j = LogManager.getLogger(ExecutionLogger.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final Path logFilePath;
    private final BufferedWriter writer;

    public ExecutionLogger(String configuredPath) {
        Path resolvedPath;
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            resolvedPath = Paths.get(configuredPath.trim()).toAbsolutePath();
        } else {
            String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
            Path dir = Paths.get(tmpDir, "ai_browser_logs");
            String fileName = "ai_browser_run_" + FILE_TS_FORMATTER.format(LocalDateTime.now()) + ".log";
            resolvedPath = dir.resolve(fileName).toAbsolutePath();
        }

        this.logFilePath = resolvedPath;

        BufferedWriter bw = null;
        try {
            Path parent = logFilePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            bw = Files.newBufferedWriter(
                    logFilePath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            log4j.error("Failed to initialize log file at {}: {}", logFilePath, e.getMessage(), e);
        }
        this.writer = bw;

        logLine("INFO", "================================================================================");
        logLine("INFO", "AI Browser Automation - Session Initialized");
        logLine("INFO", "Log File: " + logFilePath.toString());
        logLine("INFO", "================================================================================");
    }

    public synchronized void logLine(String level, String message) {
        String timestamp = TIME_FORMATTER.format(LocalDateTime.now());
        String formattedLine = String.format("[%s] [%-5s] %s", timestamp, level, message);

        if ("ERROR".equalsIgnoreCase(level)) {
            log4j.error(message);
        } else if ("WARN".equalsIgnoreCase(level)) {
            log4j.warn(message);
        } else {
            log4j.info(message);
        }

        if (writer != null) {
            try {
                writer.write(formattedLine);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                log4j.error("Failed to write to log file: {}", e.getMessage());
            }
        }
    }

    public void logSessionStart(String prompt, String model, String browserType, boolean headless, String startUrl,
            int totalKeys) {
        logLine("INFO", "SESSION PARAMETERS:");
        logLine("INFO", "  - Prompt: " + prompt);
        logLine("INFO", "  - Gemini Model: " + model);
        logLine("INFO", "  - Browser Type: " + browserType + " (Headless: " + headless + ")");
        logLine("INFO",
                "  - Start URL: " + (startUrl != null && !startUrl.isEmpty() ? startUrl : "(AI will decide/navigate)"));
        logLine("INFO", "  - Gemini API Keys Configured: " + totalKeys);
        logLine("INFO", "--------------------------------------------------------------------------------");
    }

    public void logStepStart(int step, int maxSteps, String currentUrl) {
        logLine("INFO", String.format(">>> [STEP %d of %d] Current URL: %s", step, maxSteps, currentUrl));
    }

    public void logDomAnalysis(int step, int elementsCount, int domChars) {
        logLine("INFO", String.format("  [Step %d] Extracted %d interactive DOM elements (%d chars)", step,
                elementsCount, domChars));
    }

    public void logGeminiCall(int step, String model, int keyIndex, int keyTotal, String maskedKey) {
        logLine("INFO", String.format("  [Step %d] Requesting decision from Gemini API (%s) using Key #%d/%d [%s]...",
                step, model, keyIndex, keyTotal, maskedKey));
    }

    public void logGeminiResponse(int step, String actionType, String description, long latencyMs) {
        logLine("INFO", String.format("  [Step %d] AI Decision received in %d ms -> Action: [%s] %s",
                step, latencyMs, actionType, description));
    }

    public void logKeyRotation(int fromIndex, int toIndex, String maskedKey, int statusCode, String reason) {
        logLine("WARN",
                String.format("  ! KEY FAILOVER: Key #%d encountered HTTP %d (%s). Auto-rotating to Key #%d [%s].",
                        fromIndex, statusCode, reason, toIndex, maskedKey));
    }

    public void logModelFallback(String fromModel, String toModel, int statusCode, String reason) {
        logLine("WARN",
                String.format("  ! MODEL FAILOVER: Primary model '%s' unavailable (HTTP %d - %s). Falling back to model '%s' across key pool...",
                        fromModel, statusCode, reason, toModel));
    }

    public void logActionExecution(int step, String actionType, String details) {
        logLine("INFO", String.format("  [Step %d] Executing browser action: [%s] %s", step, actionType, details));
    }

    public void logActionSuccess(int step, String actionType, long durationMs) {
        logLine("INFO", String.format("  [Step %d] Action [%s] completed successfully in %d ms.", step, actionType,
                durationMs));
    }

    public void logActionFailure(int step, String actionType, String error, long durationMs) {
        logLine("ERROR",
                String.format("  [Step %d] Action [%s] FAILED after %d ms: %s", step, actionType, durationMs, error));
    }

    public void logSessionEnd(String status, int stepsExecuted, long totalDurationMs, String finalOutput,
            String screenshotPath) {
        logLine("INFO", "================================================================================");
        logLine("INFO", "AI Browser Automation - Session Concluded");
        logLine("INFO", "  - Overall Status: " + status);
        logLine("INFO", "  - Total Steps Executed: " + stepsExecuted);
        logLine("INFO", "  - Total Duration: " + totalDurationMs + " ms");
        logLine("INFO", "  - Final Output: " + finalOutput);
        logLine("INFO", "  - Last Screenshot: "
                + (screenshotPath != null && !screenshotPath.isEmpty() ? screenshotPath : "N/A"));
        logLine("INFO", "================================================================================");
    }

    public void info(String message) {
        logLine("INFO", message);
    }

    public void warn(String message) {
        logLine("WARN", message);
    }

    public void error(String message, Throwable t) {
        logLine("ERROR", message);
        if (t != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            logLine("ERROR", sw.toString());
        }
    }

    public String getLogFilePath() {
        return logFilePath.toAbsolutePath().toString();
    }

    @Override
    public void close() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                log4j.error("Failed to close log writer: {}", e.getMessage());
            }
        }
    }
}
