package com.automationanywhere.botcommand.aibrowser.gemini;

import com.automationanywhere.botcommand.aibrowser.logging.ExecutionLogger;
import com.automationanywhere.botcommand.aibrowser.model.AgentAction;
import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class GeminiClient {
    private static final Logger logger = LogManager.getLogger(GeminiClient.class);
    private static final Gson GSON = new GsonBuilder().create();

    private final ApiKeyPool apiKeyPool;
    private final String model;
    private final HttpClient httpClient;

    public GeminiClient(ApiKeyPool apiKeyPool, String model) {
        this.apiKeyPool = apiKeyPool != null ? apiKeyPool : new ApiKeyPool("", Collections.emptyList());
        this.model = (model != null && !model.trim().isEmpty()) ? model.trim() : "gemini-2.0-flash";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(25))
                .build();
    }

    public GeminiClient(String singleApiKey, String model) {
        this(new ApiKeyPool(singleApiKey, Collections.emptyList()), model);
    }

    public AgentAction decideNextAction(String goal, String currentUrl, String domContext, String base64Screenshot, List<String> history) throws Exception {
        return decideNextAction(goal, currentUrl, domContext, base64Screenshot, history, null, 0);
    }

    public AgentAction decideNextAction(String goal, String currentUrl, String domContext, String base64Screenshot, List<String> history, ExecutionLogger execLogger, int stepNumber) throws Exception {
        String prompt = buildPrompt(goal, currentUrl, domContext, history);
        String requestJson = buildRequestBody(prompt, base64Screenshot);

        int maxAttempts = Math.max(1, apiKeyPool.size());
        String lastErrorMsg = "";

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String currentKey = apiKeyPool.getActiveKey();
            int keyIndex = apiKeyPool.getCurrentIndex() + 1;
            String maskedKey = apiKeyPool.getMaskedCurrentKey();

            String endpoint = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", model, currentKey);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                    .build();

            if (execLogger != null) {
                execLogger.logGeminiCall(stepNumber, model, keyIndex, apiKeyPool.size(), maskedKey);
            }
            logger.info("Calling Gemini API [Model: {}, Key #{}/{}: {}]", model, keyIndex, apiKeyPool.size(), maskedKey);

            long apiStart = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long apiLatency = System.currentTimeMillis() - apiStart;

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode == 200) {
                AgentAction action = parseGeminiResponse(responseBody);
                if (execLogger != null) {
                    execLogger.logGeminiResponse(stepNumber, action.getType(), action.getDescription(), apiLatency);
                }
                return action;
            }

            lastErrorMsg = String.format("Status %d: %s", statusCode, responseBody);
            logger.warn("Gemini API call failed with status {} on Key #{}: {}", statusCode, keyIndex, responseBody);

            if (ApiKeyPool.isRotatableError(statusCode, responseBody) && attempt < maxAttempts - 1) {
                apiKeyPool.rotate();
                int nextIndex = apiKeyPool.getCurrentIndex() + 1;
                String nextMasked = apiKeyPool.getMaskedCurrentKey();

                logger.warn("Rotatable error detected (HTTP {} / limit). Auto-rotating to Key #{} [{}]...", statusCode, nextIndex, nextMasked);
                if (execLogger != null) {
                    execLogger.logKeyRotation(keyIndex, nextIndex, nextMasked, statusCode, "HTTP Error / Limit");
                }
                if (history != null) {
                    history.add(String.format("Auto-rotation notice: Key #%d (%s) encountered HTTP %d. Switching to Key #%d.", keyIndex, maskedKey, statusCode, nextIndex));
                }
                try {
                    Thread.sleep(500); // brief debounce between key failovers
                } catch (InterruptedException ignored) {}
                continue;
            }

            // If non-rotatable error (e.g. invalid syntax 400), don't burn other keys
            if (!ApiKeyPool.isRotatableError(statusCode, responseBody)) {
                if (execLogger != null) {
                    execLogger.warn(String.format("Gemini API non-retryable error on Key #%d: HTTP %d - %s", keyIndex, statusCode, responseBody));
                }
                throw new RuntimeException("Gemini API non-retryable error: " + lastErrorMsg);
            }
        }

        throw new RuntimeException(String.format("Gemini API request failed across all %d key(s). Last error: %s", maxAttempts, lastErrorMsg));
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

        sb.append("\nYou are provided with a screenshot of the current page with yellow numbered badges [id] over interactive elements.\n");
        sb.append("Carefully inspect the screenshot and element list, then choose ONE next action.\n\n");
        sb.append("Available action types:\n");
        sb.append("- CLICK: requires 'elementId' (number)\n");
        sb.append("- TYPE: requires 'elementId' (number) and 'text' (string), optionally 'pressEnter' (boolean)\n");
        sb.append("- NAVIGATE: requires 'url' (string)\n");
        sb.append("- SCROLL: requires 'scrollAmount' (number, e.g. 500 for down, -500 for up)\n");
        sb.append("- KEY_PRESS: requires 'key' (e.g. 'Enter', 'Tab', 'Escape')\n");
        sb.append("- WAIT: requires 'waitSeconds' (number, e.g. 3)\n");
        sb.append("- FINISH: when the objective is fully completed. Put your final answer or extracted data into 'result'.\n");
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
}
