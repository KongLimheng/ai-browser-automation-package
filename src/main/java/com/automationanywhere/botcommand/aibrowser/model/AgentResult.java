package com.automationanywhere.botcommand.aibrowser.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class AgentResult {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String status;
    private final String outputResult;
    private final List<String> steps;
    private final List<StepRecord> stepRecords;
    private final String lastScreenshotPath;
    private final int stepCount;
    private final int failedStep;
    private final String failedAction;
    private final String errorMessage;
    private final String logFilePath;
    private final String downloadedFilePath;
    private final int downloadCount;
    private final String activeModel;
    private final boolean modelFallbackOccurred;

    public AgentResult(String status, String outputResult, List<String> steps, String lastScreenshotPath,
            int stepCount) {
        this(status, outputResult, steps, Collections.emptyList(), lastScreenshotPath, stepCount, 0, "", "", "", "", 0,
                "", false);
    }

    public AgentResult(String status,
            String outputResult,
            List<String> steps,
            List<StepRecord> stepRecords,
            String lastScreenshotPath,
            int stepCount,
            int failedStep,
            String failedAction,
            String errorMessage,
            String logFilePath) {
        this(status, outputResult, steps, stepRecords, lastScreenshotPath, stepCount, failedStep, failedAction,
                errorMessage, logFilePath, "", 0, "", false);
    }

    public AgentResult(String status,
            String outputResult,
            List<String> steps,
            List<StepRecord> stepRecords,
            String lastScreenshotPath,
            int stepCount,
            int failedStep,
            String failedAction,
            String errorMessage,
            String logFilePath,
            String downloadedFilePath,
            int downloadCount) {
        this(status, outputResult, steps, stepRecords, lastScreenshotPath, stepCount, failedStep, failedAction,
                errorMessage, logFilePath, downloadedFilePath, downloadCount, "", false);
    }

    public AgentResult(String status,
            String outputResult,
            List<String> steps,
            List<StepRecord> stepRecords,
            String lastScreenshotPath,
            int stepCount,
            int failedStep,
            String failedAction,
            String errorMessage,
            String logFilePath,
            String downloadedFilePath,
            int downloadCount,
            String activeModel,
            boolean modelFallbackOccurred) {
        this.status = status != null ? status : "FAILED";
        this.outputResult = outputResult != null ? outputResult : "";
        this.steps = steps != null ? steps : Collections.emptyList();
        this.stepRecords = stepRecords != null ? stepRecords : Collections.emptyList();
        this.lastScreenshotPath = lastScreenshotPath != null ? lastScreenshotPath : "";
        this.stepCount = stepCount;
        this.failedStep = failedStep;
        this.failedAction = failedAction != null ? failedAction : "";
        this.errorMessage = errorMessage != null ? errorMessage : "";
        this.logFilePath = logFilePath != null ? logFilePath : "";
        this.downloadedFilePath = downloadedFilePath != null ? downloadedFilePath : "";
        this.downloadCount = downloadCount;
        this.activeModel = activeModel != null ? activeModel : "";
        this.modelFallbackOccurred = modelFallbackOccurred;
    }

    public String getStatus() {
        return status;
    }

    public String getOutputResult() {
        return outputResult;
    }

    public List<String> getSteps() {
        return steps;
    }

    public List<StepRecord> getStepRecords() {
        return stepRecords;
    }

    public String getStepsSummary() {
        if (stepRecords != null && !stepRecords.isEmpty()) {
            List<String> formatted = new ArrayList<>();
            for (StepRecord r : stepRecords) {
                formatted.add(r.toLogFormat());
            }
            return String.join("\n", formatted);
        }
        return String.join("\n", steps);
    }

    public String getStepDetailsJson() {
        return GSON.toJson(stepRecords);
    }

    public String getLastScreenshotPath() {
        return lastScreenshotPath;
    }

    public int getStepCount() {
        return stepCount;
    }

    public int getFailedStep() {
        return failedStep;
    }

    public String getFailedAction() {
        return failedAction;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getLogFilePath() {
        return logFilePath;
    }

    public String getDownloadedFilePath() {
        return downloadedFilePath;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public String getActiveModel() {
        return activeModel;
    }

    public boolean isModelFallbackOccurred() {
        return modelFallbackOccurred;
    }
}
