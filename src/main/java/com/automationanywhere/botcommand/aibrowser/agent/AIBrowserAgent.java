package com.automationanywhere.botcommand.aibrowser.agent;

import com.automationanywhere.botcommand.aibrowser.gemini.ApiKeyPool;
import com.automationanywhere.botcommand.aibrowser.gemini.GeminiClient;
import com.automationanywhere.botcommand.aibrowser.logging.ExecutionLogger;
import com.automationanywhere.botcommand.aibrowser.model.AgentAction;
import com.automationanywhere.botcommand.aibrowser.model.AgentResult;
import com.automationanywhere.botcommand.aibrowser.model.StepRecord;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class AIBrowserAgent {
    private static final Logger logger = LogManager.getLogger(AIBrowserAgent.class);

    private final ApiKeyPool apiKeyPool;
    private final String model;
    private final String fallbackModel;
    private final boolean headless;
    private final int maxSteps;
    private final String browserType;
    private final String cdpEndpoint;
    private final String logFilePath;
    private final String downloadDirectory;

    public AIBrowserAgent(ApiKeyPool apiKeyPool, String model, String fallbackModel, boolean headless, int maxSteps, String browserType, String cdpEndpoint, String logFilePath, String downloadDirectory) {
        this.apiKeyPool = apiKeyPool != null ? apiKeyPool : new ApiKeyPool("", Collections.emptyList());
        this.model = model != null && !model.trim().isEmpty() ? model.trim() : "gemini-3.8-flash";
        this.fallbackModel = fallbackModel != null && !fallbackModel.trim().isEmpty() ? fallbackModel.trim() : "gemini-2.5-flash";
        this.headless = headless;
        this.maxSteps = maxSteps > 0 ? maxSteps : 15;
        this.browserType = browserType != null ? browserType.toUpperCase() : "CHROME";
        this.cdpEndpoint = cdpEndpoint != null ? cdpEndpoint.trim() : "";
        this.logFilePath = logFilePath != null ? logFilePath.trim() : "";
        this.downloadDirectory = downloadDirectory != null ? downloadDirectory.trim() : "";
    }

    public AIBrowserAgent(ApiKeyPool apiKeyPool, String model, boolean headless, int maxSteps, String browserType, String cdpEndpoint, String logFilePath, String downloadDirectory) {
        this(apiKeyPool, model, "gemini-2.5-flash", headless, maxSteps, browserType, cdpEndpoint, logFilePath, downloadDirectory);
    }

    public AIBrowserAgent(ApiKeyPool apiKeyPool, String model, boolean headless, int maxSteps, String browserType, String cdpEndpoint, String logFilePath) {
        this(apiKeyPool, model, headless, maxSteps, browserType, cdpEndpoint, logFilePath, "");
    }

    public AIBrowserAgent(ApiKeyPool apiKeyPool, String model, boolean headless, int maxSteps, String browserType, String cdpEndpoint) {
        this(apiKeyPool, model, headless, maxSteps, browserType, cdpEndpoint, "", "");
    }

    public AIBrowserAgent(String singleApiKey, String model, boolean headless, int maxSteps, String browserType, String cdpEndpoint) {
        this(new ApiKeyPool(singleApiKey, Collections.emptyList()), model, headless, maxSteps, browserType, cdpEndpoint, "", "");
    }

    public AgentResult execute(String goalPrompt, String startUrl) {
        long sessionStart = System.currentTimeMillis();
        List<StepRecord> stepRecords = new ArrayList<>();
        List<String> history = new ArrayList<>();
        List<String> downloadedFiles = Collections.synchronizedList(new ArrayList<>());
        String status = "FAILED";
        String finalOutput = "";
        String lastScreenshotPath = "";
        int stepsExecuted = 0;
        int failedStep = 0;
        String failedAction = "";
        String errorMessage = "";
        String resolvedActiveModel = model;
        boolean fallbackOccurred = false;
        GeminiClient geminiClient = null;

        try (ExecutionLogger execLogger = new ExecutionLogger(logFilePath)) {
            execLogger.logSessionStart(goalPrompt, model, browserType, headless, startUrl, apiKeyPool.size());
            if (!downloadDirectory.isEmpty()) {
                execLogger.info("Configured Download Directory: " + downloadDirectory);
            }

            try (Playwright playwright = Playwright.create()) {
                Browser browser = null;
                BrowserContext context = null;

                if ("CDP".equals(browserType) || !cdpEndpoint.isEmpty()) {
                    String endpoint = !cdpEndpoint.isEmpty() ? cdpEndpoint : "http://localhost:9222";
                    execLogger.info("Connecting to existing browser via CDP: " + endpoint);
                    browser = playwright.chromium().connectOverCDP(endpoint);
                    context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
                } else {
                    BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
                    if ("MSEDGE".equals(browserType)) {
                        options.setChannel("msedge");
                    } else if ("CHROME".equals(browserType)) {
                        options.setChannel("chrome");
                    }
                    execLogger.info("Launching browser: " + browserType + " (headless=" + headless + ")");
                    try {
                        browser = playwright.chromium().launch(options);
                    } catch (Exception e) {
                        execLogger.warn("Could not launch with channel '" + browserType + "', falling back to bundled chromium: " + e.getMessage());
                        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
                    }
                    Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                            .setViewportSize(1280, 800)
                            .setAcceptDownloads(true);
                    context = browser.newContext(contextOptions);
                }

                Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
                page.setViewportSize(1280, 800);

                // Register automatic download interception
                page.onDownload(download -> {
                    try {
                        String filename = download.suggestedFilename();
                        Path targetPath;
                        if (downloadDirectory != null && !downloadDirectory.isEmpty()) {
                            Path dir = Paths.get(downloadDirectory);
                            if (!Files.exists(dir)) {
                                Files.createDirectories(dir);
                            }
                            targetPath = dir.resolve(filename);
                        } else {
                            Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "ai_browser_downloads");
                            if (!Files.exists(tmpDir)) {
                                Files.createDirectories(tmpDir);
                            }
                            targetPath = tmpDir.resolve(filename);
                        }
                        download.saveAs(targetPath);
                        String absPath = targetPath.toAbsolutePath().toString();
                        downloadedFiles.add(absPath);
                        execLogger.info("DOWNLOAD EVENT: Intercepted and saved file to " + absPath);
                        history.add("System event: File downloaded and saved to " + absPath);
                    } catch (Exception dlEx) {
                        execLogger.warn("Failed to save downloaded file: " + dlEx.getMessage());
                    }
                });

                if (startUrl != null && !startUrl.trim().isEmpty()) {
                    String resolvedUrl = normalizeUrl(startUrl);
                    String logMsg = resolvedUrl.equals(startUrl.trim())
                            ? "Navigating to initial start URL: " + resolvedUrl
                            : "Navigating to initial start URL: " + resolvedUrl + " (normalized from '" + startUrl.trim() + "')";
                    execLogger.info(logMsg);
                    try {
                        page.navigate(resolvedUrl);
                        try {
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                        } catch (Exception ignored) {}
                    } catch (Exception navEx) {
                        String navErr = navEx.getMessage() != null ? navEx.getMessage() : navEx.toString();
                        execLogger.warn("Initial navigation to '" + resolvedUrl + "' failed: " + navErr);
                        history.add("System notice: Initial navigation to " + resolvedUrl + " failed: " + navErr);
                    }
                }

                geminiClient = new GeminiClient(apiKeyPool, model, fallbackModel);

                for (int step = 1; step <= maxSteps; step++) {
                    stepsExecuted = step;
                    long stepStartTime = System.currentTimeMillis();
                    execLogger.logStepStart(step, maxSteps, page.url());

                    // 1. Highlight interactive elements & extract DOM description
                    String domContext = DomTreeExtractor.highlightAndExtract(page);
                    execLogger.logDomAnalysis(step, 0, domContext != null ? domContext.length() : 0);

                    // 2. Capture screenshot with overlays
                    byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
                    String base64Screenshot = Base64.getEncoder().encodeToString(screenshotBytes);

                    // 3. Request next action from Gemini
                    AgentAction action;
                    try {
                        action = geminiClient.decideNextAction(goalPrompt, page.url(), domContext, base64Screenshot, history, execLogger, step);
                    } catch (Exception geminiEx) {
                        long failDuration = System.currentTimeMillis() - stepStartTime;
                        String apiErr = geminiEx.getMessage() != null ? geminiEx.getMessage() : geminiEx.toString();
                        StepRecord failRec = new StepRecord(step, "API_CALL", "Gemini Inference Error: " + apiErr);
                        failRec.setStatus("FAILED");
                        failRec.setErrorMessage(apiErr);
                        failRec.setDurationMs(failDuration);
                        stepRecords.add(failRec);

                        failedStep = step;
                        failedAction = "API_CALL";
                        errorMessage = "Gemini API failed on step " + step + ": " + apiErr;
                        status = "ERROR";
                        finalOutput = errorMessage;
                        execLogger.logActionFailure(step, "API_CALL", apiErr, failDuration);

                        lastScreenshotPath = captureScreenshot(page, execLogger);
                        break;
                    }

                    // 4. Remove highlight badges before interaction
                    DomTreeExtractor.removeHighlights(page);

                    // 5. Build step record
                    String type = action.getType();
                    StepRecord stepRecord = new StepRecord(step, type, action.getDescription());

                    // 6. Execute action with granular error handling
                    try {
                        if ("FINISH".equals(type)) {
                            status = "SUCCESS";
                            finalOutput = action.getResult();
                            stepRecord.setStatus("SUCCESS");
                            long duration = System.currentTimeMillis() - stepStartTime;
                            stepRecord.setDurationMs(duration);
                            stepRecords.add(stepRecord);
                            history.add(stepRecord.toLogFormat());
                            execLogger.logActionSuccess(step, "FINISH", duration);
                            lastScreenshotPath = captureScreenshot(page, execLogger);
                            break;
                        } else if ("FAIL".equals(type)) {
                            status = "FAILED";
                            finalOutput = action.getResult();
                            failedStep = step;
                            failedAction = "FAIL";
                            errorMessage = action.getResult() != null ? action.getResult() : "AI determined task cannot be completed.";
                            stepRecord.setStatus("FAILED");
                            stepRecord.setErrorMessage(errorMessage);
                            long duration = System.currentTimeMillis() - stepStartTime;
                            stepRecord.setDurationMs(duration);
                            stepRecords.add(stepRecord);
                            history.add(stepRecord.toLogFormat());
                            execLogger.logActionFailure(step, "FAIL", errorMessage, duration);
                            lastScreenshotPath = captureScreenshot(page, execLogger);
                            break;
                        } else if ("CLICK".equals(type)) {
                            stepRecord.setElementId(action.getElementId());
                            execLogger.logActionExecution(step, "CLICK", "Element ID: " + action.getElementId());
                            if (action.getElementId() != null) {
                                Locator loc = page.locator(String.format("[data-ai-id='%d']", action.getElementId()));
                                loc.click(new Locator.ClickOptions().setTimeout(5000));
                                page.waitForTimeout(1000);
                            }
                        } else if ("TYPE".equals(type)) {
                            stepRecord.setElementId(action.getElementId());
                            execLogger.logActionExecution(step, "TYPE", "Element ID: " + action.getElementId() + ", text: '" + action.getText() + "', pressEnter: " + action.isPressEnter());
                            if (action.getElementId() != null && action.getText() != null) {
                                Locator loc = page.locator(String.format("[data-ai-id='%d']", action.getElementId()));
                                loc.fill(action.getText());
                                if (action.isPressEnter()) {
                                    loc.press("Enter");
                                }
                                page.waitForTimeout(1000);
                            }
                        } else if ("NAVIGATE".equals(type)) {
                            String targetUrl = normalizeUrl(action.getUrl());
                            stepRecord.setTargetUrl(targetUrl);
                            execLogger.logActionExecution(step, "NAVIGATE", "URL: " + targetUrl);
                            if (!targetUrl.isEmpty()) {
                                page.navigate(targetUrl);
                                try {
                                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                                } catch (Exception ignored) {}
                            }
                        } else if ("SCROLL".equals(type)) {
                            execLogger.logActionExecution(step, "SCROLL", "Amount: " + action.getScrollAmount());
                            page.mouse().wheel(0, action.getScrollAmount());
                            page.waitForTimeout(500);
                        } else if ("KEY_PRESS".equals(type)) {
                            execLogger.logActionExecution(step, "KEY_PRESS", "Key: " + action.getKey());
                            if (action.getKey() != null && !action.getKey().isEmpty()) {
                                page.keyboard().press(action.getKey());
                                page.waitForTimeout(500);
                            }
                        } else if ("WAIT".equals(type)) {
                            int waitSec = action.getWaitSeconds();
                            execLogger.logActionExecution(step, "WAIT", "Seconds: " + waitSec);
                            page.waitForTimeout(waitSec * 1000L);
                        }

                        stepRecord.setStatus("SUCCESS");
                        long stepDuration = System.currentTimeMillis() - stepStartTime;
                        stepRecord.setDurationMs(stepDuration);
                        stepRecords.add(stepRecord);
                        history.add(stepRecord.toLogFormat());
                        execLogger.logActionSuccess(step, type, stepDuration);

                    } catch (Exception actionEx) {
                        long stepDuration = System.currentTimeMillis() - stepStartTime;
                        String actionErr = actionEx.getMessage() != null ? actionEx.getMessage() : actionEx.toString();
                        stepRecord.setStatus("FAILED");
                        stepRecord.setErrorMessage(actionErr);
                        stepRecord.setDurationMs(stepDuration);
                        stepRecords.add(stepRecord);

                        failedStep = step;
                        failedAction = type;
                        errorMessage = actionErr;

                        execLogger.logActionFailure(step, type, actionErr, stepDuration);
                        history.add(String.format("Step %d [%s] FAILED - %s", step, type, actionErr));

                        if (page.isClosed() || (browser != null && !browser.isConnected())) {
                            status = "ERROR";
                            finalOutput = "Browser or page closed unexpectedly: " + actionErr;
                            break;
                        }
                    }

                    if (step == maxSteps && !"SUCCESS".equals(status)) {
                        status = "MAX_STEPS_REACHED";
                        finalOutput = "Agent reached maximum step limit (" + maxSteps + ") without completing.";
                        if (failedStep == 0) {
                            failedStep = maxSteps;
                            failedAction = type;
                            errorMessage = "Reached maximum step limit (" + maxSteps + ") without completing objective.";
                        }
                    }
                }

                // If download happened and finalOutput is empty or success, acknowledge download
                String finalDownloadedPath = downloadedFiles.isEmpty() ? "" : downloadedFiles.get(downloadedFiles.size() - 1);
                if (!downloadedFiles.isEmpty() && ("SUCCESS".equals(status) || finalOutput.isEmpty())) {
                    if (finalOutput.isEmpty()) {
                        finalOutput = "File downloaded successfully to: " + finalDownloadedPath;
                        status = "SUCCESS";
                    }
                }

                // Capture final screenshot if not already captured
                if (lastScreenshotPath.isEmpty()) {
                    lastScreenshotPath = captureScreenshot(page, execLogger);
                }

                if (!"CDP".equals(browserType) && browser != null) {
                    browser.close();
                }
            } catch (Exception e) {
                logger.error("Error during AI browser automation run: {}", e.getMessage(), e);
                execLogger.error("Fatal error during browser automation run: " + e.getMessage(), e);
                status = "ERROR";
                finalOutput = "Execution failed: " + e.getMessage();
                if (failedStep == 0) {
                    failedStep = stepsExecuted > 0 ? stepsExecuted : 1;
                    failedAction = "SYSTEM";
                    errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                }
            }

            long totalDurationMs = System.currentTimeMillis() - sessionStart;
            execLogger.logSessionEnd(status, stepsExecuted, totalDurationMs, finalOutput, lastScreenshotPath);

            String finalDownloadedPath = downloadedFiles.isEmpty() ? "" : downloadedFiles.get(downloadedFiles.size() - 1);
            int totalDownloads = downloadedFiles.size();

            if (geminiClient != null) {
                resolvedActiveModel = geminiClient.getActiveModel();
                fallbackOccurred = geminiClient.isFallbackActive();
            }

            return new AgentResult(
                    status,
                    finalOutput,
                    history,
                    stepRecords,
                    lastScreenshotPath,
                    stepsExecuted,
                    failedStep,
                    failedAction,
                    errorMessage,
                    execLogger.getLogFilePath(),
                    finalDownloadedPath,
                    totalDownloads,
                    resolvedActiveModel,
                    fallbackOccurred
            );
        }
    }

    private String captureScreenshot(Page page, ExecutionLogger execLogger) {
        try {
            if (page != null && !page.isClosed()) {
                Path tempScreenshot = Files.createTempFile("ai_browser_shot_", ".png");
                page.screenshot(new Page.ScreenshotOptions().setPath(tempScreenshot));
                return tempScreenshot.toAbsolutePath().toString();
            }
        } catch (Exception e) {
            if (execLogger != null) {
                execLogger.warn("Could not capture screenshot: " + e.getMessage());
            }
        }
        return "";
    }

    public ApiKeyPool getApiKeyPool() {
        return apiKeyPool;
    }

    public String getModel() {
        return model;
    }

    public String getLogFilePath() {
        return logFilePath;
    }

    public String getDownloadDirectory() {
        return downloadDirectory;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    /**
     * Normalizes a web or local URL string, ensuring an appropriate protocol scheme is present.
     * Prevents Playwright 'Protocol error (Page.navigate): Cannot navigate to invalid URL' when
     * bare domain names (e.g. 'google.com', 'www.google.com') are provided.
     *
     * @param input Raw URL or domain string
     * @return Normalized URL with protocol scheme, or empty string if input is null/empty
     */
    public static String normalizeUrl(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String lower = trimmed.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("file://") || lower.startsWith("about:")
                || lower.startsWith("data:") || lower.startsWith("chrome://")
                || lower.startsWith("edge://")) {
            return trimmed;
        }

        // Windows local file path: C:\path or C:/path
        if (trimmed.matches("^[a-zA-Z]:[\\\\/].*")) {
            try {
                return Paths.get(trimmed).toUri().toString();
            } catch (Exception ignored) {
                return "file:///" + trimmed.replace('\\', '/');
            }
        }

        // Unix local file path
        if (trimmed.startsWith("/") && (trimmed.endsWith(".html") || trimmed.endsWith(".htm"))) {
            return "file://" + trimmed;
        }

        // Localhost and loopback IPs default to http://
        if (lower.startsWith("localhost") || lower.startsWith("127.0.0.1") || lower.startsWith("0.0.0.0")) {
            return "http://" + trimmed;
        }

        // Standard web domains default to https://
        return "https://" + trimmed;
    }
}
