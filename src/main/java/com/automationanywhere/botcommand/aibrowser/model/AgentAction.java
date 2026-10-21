package com.automationanywhere.botcommand.aibrowser.model;

import com.google.gson.annotations.SerializedName;

public class AgentAction {

    @SerializedName("type")
    private String type; // CLICK, TYPE, NAVIGATE, SCROLL, KEY_PRESS, WAIT, FINISH, FAIL

    @SerializedName("elementId")
    private Integer elementId;

    @SerializedName("text")
    private String text;

    @SerializedName("pressEnter")
    private Boolean pressEnter;

    @SerializedName("url")
    private String url;

    @SerializedName("scrollAmount")
    private Integer scrollAmount; // positive = scroll down, negative = scroll up

    @SerializedName("key")
    private String key; // Enter, Escape, Tab, etc.

    @SerializedName("waitSeconds")
    private Integer waitSeconds;

    @SerializedName("description")
    private String description;

    @SerializedName("result")
    private String result;

    public AgentAction() {
    }

    public AgentAction(String type, String description) {
        this.type = type;
        this.description = description;
    }

    public String getType() {
        return type != null ? type.toUpperCase() : "WAIT";
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getElementId() {
        return elementId;
    }

    public void setElementId(Integer elementId) {
        this.elementId = elementId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isPressEnter() {
        return pressEnter != null && pressEnter;
    }

    public void setPressEnter(Boolean pressEnter) {
        this.pressEnter = pressEnter;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getScrollAmount() {
        return scrollAmount != null ? scrollAmount : 400;
    }

    public void setScrollAmount(Integer scrollAmount) {
        this.scrollAmount = scrollAmount;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getWaitSeconds() {
        return waitSeconds != null ? waitSeconds : 2;
    }

    public void setWaitSeconds(Integer waitSeconds) {
        this.waitSeconds = waitSeconds;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResult() {
        return result != null ? result : "";
    }

    public void setResult(String result) {
        this.result = result;
    }
}
