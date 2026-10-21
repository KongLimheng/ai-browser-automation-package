package com.automationanywhere.botcommand.aibrowser.model;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;

public class StepRecord {

    @SerializedName("stepNumber")
    private int stepNumber;

    @SerializedName("actionType")
    private String actionType;

    @SerializedName("description")
    private String description;

    @SerializedName("status")
    private String status; // "SUCCESS" or "FAILED"

    @SerializedName("errorMessage")
    private String errorMessage;

    @SerializedName("elementId")
    private Integer elementId;

    @SerializedName("targetUrl")
    private String targetUrl;

    @SerializedName("durationMs")
    private long durationMs;

    @SerializedName("timestamp")
    private String timestamp;

    public StepRecord() {
        this.timestamp = Instant.now().toString();
        this.status = "SUCCESS";
        this.errorMessage = "";
    }

    public StepRecord(int stepNumber, String actionType, String description) {
        this();
        this.stepNumber = stepNumber;
        this.actionType = actionType != null ? actionType : "UNKNOWN";
        this.description = description != null ? description : "";
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getActionType() {
        return actionType != null ? actionType : "UNKNOWN";
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status != null ? status : "SUCCESS";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : "";
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    public Integer getElementId() {
        return elementId;
    }

    public void setElementId(Integer elementId) {
        this.elementId = elementId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(this.status);
    }

    public String toLogFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Step %d [%s] (%d ms): [%s] %s",
                stepNumber,
                getStatus(),
                durationMs,
                getActionType(),
                getDescription()));

        if (!isSuccess() && errorMessage != null && !errorMessage.isEmpty()) {
            sb.append(" -> Error: ").append(errorMessage);
        }
        return sb.toString();
    }
}
