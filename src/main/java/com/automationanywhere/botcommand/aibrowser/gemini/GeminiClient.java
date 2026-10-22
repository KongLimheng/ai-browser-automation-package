package com.automationanywhere.botcommand.aibrowser.gemini;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.automationanywhere.botcommand.aibrowser.logging.ExecutionLogger;
import com.automationanywhere.botcommand.aibrowser.model.AgentAction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GeminiClient {
    private static final Logger logger = LogManager.getLogger(GeminiClient.class);
    private static final Gson GSON = new GsonBuilder().create();

    private final ApiKeyPool apiKeyPool;
    private final String primaryModel;
    private final String fallbackModel;
    private volatile String activeModel;
    private volatile boolean fallbackActive = false;
    private final HttpClient httpClient;

    public GeminiClient(ApiKeyPool apiKeyPool, String primaryModel, String fallbackModel) {
        this.apiKeyPool = apiKeyPool != null ? apiKeyPool : new ApiKeyPool("", Collections.emptyList());
        this.primaryModel = (primaryModel != null && !primaryModel.trim().isEmpty()) ? primaryModel.trim()
                : "gemini-3.8-flash";
        this.activeModel = this.primaryModel;

        if ("NONE".equalsIgnoreCase(fallbackModel)) {
            this.fallbackModel = null;
        } else if (fallbackModel != null && !fallbackModel.trim().isEmpty()) {
            this.fallbackModel = fallbackModel.trim();
        } else {
            this.fallbackModel = "gemini-2.0-flash";
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(25))
                .build();
    }

    public GeminiClient(ApiKeyPool apiKeyPool, String primaryModel) {
        this(apiKeyPool, primaryModel, "gemini-2.0-flash");
    }

    public GeminiClient(String singleApiKey, String model) {
        this(new ApiKeyPool(singleApiKey, Collections.emptyList()), model, "gemini-2.0-flash");
    }

    public List<String> getModelCandidates() {
        List<String> candidates = new ArrayList<>();
        if (activeModel != null && !activeModel.isEmpty()) {
            candidates.add(activeModel);
        }
        if (fallbackModel != null && !fallbackModel.isEmpty() && !"NONE".equalsIgnoreCase(fallbackModel)) {
            if (!candidates.contains(fallbackModel)) {
                candidates.add(fallbackModel);
            }
            if (!candidates.contains("gemini-2.0-flash")) {
                candidates.add("gemini-2.0-flash");
            }
            if (!candidates.contains("gemini-1.5-flash")) {
                candidates.add("gemini-1.5-flash");
            }
        }
        return candidates;
    }

    public static boolean isModelUnavailableError(int statusCode, String responseBody) {
        if (statusCode == 404 || statusCode == 503 || statusCode == 529) {
            return true;
        }
        if (responseBody != null && !responseBody.isEmpty()) {
            String lower = responseBody.toLowerCase();
            return lower.contains("no longer available")
                    || lower.contains("not_found")
                    || lower.contains("not found")
                    || lower.contains("high demand")
                    || lower.contains("spikes in demand")
                    || lower.contains("unavailable")
                    || lower.contains("overloaded")
                    || lower.contains("unknown model")
                    || lower.contains("invalid model");
        }
        return false;
    }

    public AgentAction decideNextAction(String goal, String currentUrl, String domContext, String base64Screenshot,
            List<String> history) throws Exception {
        return decideNextAction(goal, currentUrl, domContext, base64Screenshot, history, null, 0);
    }

    public AgentAction decideNextAction(String goal, String currentUrl, String domContext, String base64Screenshot,
            List<String> history, ExecutionLogger execLogger, int stepNumber) throws Exception {
        String prompt = buildPrompt(goal, currentUrl, domContext, history);
        String requestJson = buildRequestBody(prompt, base64Screenshot);

        List<String> candidateModels = getModelCandidates();
        String lastErrorMsg = "";

        for (int m = 0; m < candidateModels.size(); m++) {
            String currentCandidateModel = candidateModels.get(m);
            int maxAttempts = Math.max(1, apiKeyPool.size());

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                String currentKey = apiKeyPool.getActiveKey();
                int keyIndex = apiKeyPool.getCurrentIndex() + 1;
                String maskedKey = apiKeyPool.getMaskedCurrentKey();

                String endpoint = String.format(
                        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                        currentCandidateModel, currentKey);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                        .build();

                if (execLogger != null) {
                    execLogger.logGeminiCall(stepNumber, currentCandidateModel, keyIndex, apiKeyPool.size(), maskedKey);
                }
                logger.info("Calling Gemini API [Model: {}, Key #{}/{}: {}]", currentCandidateModel, keyIndex,
                        apiKeyPool.size(), maskedKey);

                long apiStart = System.currentTimeMillis();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                long apiLatency = System.currentTimeMillis() - apiStart;

                int statusCode = response.statusCode();
                String responseBody = response.body();

                if (statusCode == 200) {
                    AgentAction action = parseGeminiResponse(responseBody);
                    if (execLogger != null) {
                        execLogger.logGeminiResponse(stepNumber, action.getType(), action.getDescription(), apiLatency);
                    }

                    // If a fallback model succeeded, promote it as the active model for remaining
                    // steps
                    if (!currentCandidateModel.equalsIgnoreCase(this.primaryModel) && !this.fallbackActive) {
                        this.activeModel = currentCandidateModel;
                        this.fallbackActive = true;
                        logger.info("Promoted fallback model '{}' to activeModel for remainder of session",
                                currentCandidateModel);
                    }

                    return action;
                }

                lastErrorMsg = String.format("Status %d (%s): %s", statusCode, currentCandidateModel, responseBody);
                logger.warn("Gemini API call failed with status {} on Model '{}' Key #{}: {}", statusCode,
                        currentCandidateModel, keyIndex, responseBody);

                // If this is a model-level deprecation or 404 NOT_FOUND error, fast-track to next fallback model
                if (isModelUnavailableError(statusCode, responseBody) && m < candidateModels.size() - 1) {
                    if (statusCode == 404 || responseBody.toLowerCase().contains("no longer available")) {
                        logger.warn("Model '{}' is deprecated or not available (HTTP {}). Fast-tracking failover to next fallback model...",
                                currentCandidateModel, statusCode);
                        break;
                    }
                }

                // Auto-rotate key on rotatable error if more keys are available
                if (ApiKeyPool.isRotatableError(statusCode, responseBody) && attempt < maxAttempts - 1) {
                    apiKeyPool.rotate();
                    int nextIndex = apiKeyPool.getCurrentIndex() + 1;
                    String nextMasked = apiKeyPool.getMaskedCurrentKey();

                    logger.warn(
                            "Rotatable error detected (HTTP {} / limit). Auto-rotating to Key #{} [{}] on Model '{}'...",
                            statusCode, nextIndex, nextMasked, currentCandidateModel);
                    if (execLogger != null) {
                        execLogger.logKeyRotation(keyIndex, nextIndex, nextMasked, statusCode, "HTTP Error / Limit");
                    }
                    if (history != null) {
                        history.add(String.format(
                                "Auto-rotation notice: Key #%d (%s) encountered HTTP %d on model '%s'. Switching to Key #%d.",
                                keyIndex, maskedKey, statusCode, currentCandidateModel, nextIndex));
                    }
                    try {
                        Thread.sleep(500); // brief debounce between key failovers
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }

                // If non-rotatable error (e.g. invalid syntax 400), don't burn other keys
                if (!ApiKeyPool.isRotatableError(statusCode, responseBody)) {
                    if (isModelUnavailableError(statusCode, responseBody) && m < candidateModels.size() - 1) {
                        logger.warn("Model unavailable error detected (HTTP {}). Failing over to next fallback model candidate...", statusCode);
                        break;
                    }
                    if (execLogger != null) {
                        execLogger.warn(
                                String.format("Gemini API non-retryable error on Model '%s' Key #%d: HTTP %d - %s",
                                        currentCandidateModel, keyIndex, statusCode, responseBody));
                    }
                    throw new RuntimeException("Gemini API non-retryable error: " + lastErrorMsg);
                }
            }

            // All keys exhausted for currentCandidateModel. Check if we have a fallback
            // model!
            if (m < candidateModels.size() - 1) {
                String nextFallbackModel = candidateModels.get(m + 1);
                String msg = String.format(
                        "Primary model '%s' failed across all %d key(s) (%s). Falling back to model '%s'...",
                        currentCandidateModel, maxAttempts, lastErrorMsg, nextFallbackModel);
                logger.warn(msg);
                if (execLogger != null) {
                    execLogger.logModelFallback(currentCandidateModel, nextFallbackModel, 503,
                            "High Demand / Capacity / Quota Exhaustion");
                }
                if (history != null) {
                    history.add(String.format(
                            "Model failover notice: Model '%s' unavailable. Switching to fallback model '%s' across API key pool.",
                            currentCandidateModel, nextFallbackModel));
                }
                if (apiKeyPool.hasMultipleKeys()) {
                    apiKeyPool.rotate();
                }
            }
        }

        throw new RuntimeException(
                String.format("Gemini API request failed across all %d key(s) and candidate models %s. Last error: %s",
                        apiKeyPool.size(), candidateModels, lastErrorMsg));
    }

    private String buildPrompt(String goal, String currentUrl, String domContext, List<String> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an autonomous AI browser automation agent, like browser-use.\n");
        sb.append("Your objective: ").append(goal).append("\n\n");
        sb.append("Current URL: ").append(currentUrl).append("\n");

        if (history != null && !history.isEmpty()) {
            sb.append("\nPrevious actions taken:\n");
            for (String step : history) {
                sb.append("- ").append(step).append("\n");
            }
        }

        sb.append("\nInteractive Elements visible on screen (numbered [id]):\n");
        if (domContext != null && !domContext.isEmpty()) {
            sb.append(domContext).append("\n");
        } else {
            sb.append("(No interactive elements found or page still loading)\n");
        }

        sb.append(
                "\nYou are provided with a screenshot of the current page with yellow numbered badges [id] over interactive elements.\n");
        sb.append("Carefully inspect the screenshot and element list, then choose ONE next action.\n\n");
        sb.append("Available action types:\n");
        sb.append("- CLICK: requires 'elementId' (number)\n");
        sb.append("- TYPE: requires 'elementId' (number) and 'text' (string). If you want to submit or press Enter after typing (e.g. search bars, login fields), you MUST set 'pressEnter': true.\n");
        sb.append("- NAVIGATE: requires 'url' (string, e.g. 'https://www.google.com')\n");
        sb.append("- SCROLL: requires 'scrollAmount' (number, e.g. 500 for down, -500 for up)\n");
        sb.append("- KEY_PRESS: requires 'key' (e.g. 'Enter', 'Tab', 'Escape')\n");
        sb.append("- WAIT: requires 'waitSeconds' (number, e.g. 3)\n");
        sb.append(
                "- FINISH: when the objective is fully completed. Put your final answer or extracted data into 'result'.\n");
        sb.append("- FAIL: when the objective cannot be completed. Put explanation into 'result'.\n\n");
        sb.append("Return ONLY a valid JSON object matching the schema.\n");

        return sb.toString();
    }

    private String buildRequestBody(String promptText, String base64Screenshot) {
        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");

        JsonArray parts = new JsonArray();

        // 1. Text part
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", promptText);
        parts.add(textPart);

        // 2. Multimodal image part (if screenshot available)
        if (base64Screenshot != null && !base64Screenshot.isEmpty()) {
            JsonObject imagePart = new JsonObject();
            JsonObject inlineData = new JsonObject();
            inlineData.addProperty("mime_type", "image/png");
            inlineData.addProperty("data", base64Screenshot);
            imagePart.add("inline_data", inlineData);
            parts.add(imagePart);
        }

        userContent.add("parts", parts);
        contents.add(userContent);
        root.add("contents", contents);

        // Generation config for JSON
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.1);
        generationConfig.addProperty("response_mime_type", "application/json");

        // Structured schema
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "OBJECT");
        JsonObject properties = new JsonObject();

        JsonObject typeProp = new JsonObject();
        typeProp.addProperty("type", "STRING");
        properties.add("type", typeProp);

        JsonObject elemIdProp = new JsonObject();
        elemIdProp.addProperty("type", "INTEGER");
        properties.add("elementId", elemIdProp);

        JsonObject textProp = new JsonObject();
        textProp.addProperty("type", "STRING");
        properties.add("text", textProp);

        JsonObject pressEnterProp = new JsonObject();
        pressEnterProp.addProperty("type", "BOOLEAN");
        pressEnterProp.addProperty("description", "Set to true whenever Enter should be pressed after typing (e.g. search bars, form submission).");
        properties.add("pressEnter", pressEnterProp);

        JsonObject urlProp = new JsonObject();
        urlProp.addProperty("type", "STRING");
        properties.add("url", urlProp);

        JsonObject scrollProp = new JsonObject();
        scrollProp.addProperty("type", "INTEGER");
        properties.add("scrollAmount", scrollProp);

        JsonObject keyProp = new JsonObject();
        keyProp.addProperty("type", "STRING");
        properties.add("key", keyProp);

        JsonObject waitProp = new JsonObject();
        waitProp.addProperty("type", "INTEGER");
        properties.add("waitSeconds", waitProp);

        JsonObject descProp = new JsonObject();
        descProp.addProperty("type", "STRING");
        properties.add("description", descProp);

        JsonObject resultProp = new JsonObject();
        resultProp.addProperty("type", "STRING");
        properties.add("result", resultProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("type");
        required.add("description");
        schema.add("required", required);

        generationConfig.add("response_schema", schema);
        root.add("generationConfig", generationConfig);

        return GSON.toJson(root);
    }

    private AgentAction parseGeminiResponse(String responseJson) {
        JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("No response candidate returned by Gemini API.");
        }

        JsonObject candidate = candidates.get(0).getAsJsonObject();
        JsonObject content = candidate.getAsJsonObject("content");
        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("Empty content parts in Gemini API response.");
        }

        String rawJson = parts.get(0).getAsJsonObject().get("text").getAsString().trim();
        logger.debug("Raw action JSON from Gemini: {}", rawJson);

        // Sanitize code fence if present
        if (rawJson.startsWith("```json")) {
            rawJson = rawJson.substring(7);
        } else if (rawJson.startsWith("```")) {
            rawJson = rawJson.substring(3);
        }
        if (rawJson.endsWith("```")) {
            rawJson = rawJson.substring(0, rawJson.length() - 3);
        }
        rawJson = rawJson.trim();

        return GSON.fromJson(rawJson, AgentAction.class);
    }

    public ApiKeyPool getApiKeyPool() {
        return apiKeyPool;
    }

    public String getActiveModel() {
        return activeModel;
    }

    public String getPrimaryModel() {
        return primaryModel;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public boolean isFallbackActive() {
        return fallbackActive;
    }
}
