package com.automationanywhere.botcommand.aibrowser.gemini;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class ApiKeyPool {
    private static final Logger logger = LogManager.getLogger(ApiKeyPool.class);

    private final List<String> keys = new ArrayList<>();
    private int currentIndex = 0;
    private int totalRotations = 0;

    public ApiKeyPool(String rawKeys, List<String> listKeys) {
        Set<String> uniqueKeys = new LinkedHashSet<>();

        if (rawKeys != null && !rawKeys.trim().isEmpty()) {
            String[] tokens = rawKeys.split("[,;\\r?\\n]+");
            for (String token : tokens) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    uniqueKeys.add(trimmed);
                }
            }
        }

        if (listKeys != null) {
            for (String key : listKeys) {
                if (key != null) {
                    String trimmed = key.trim();
                    if (!trimmed.isEmpty()) {
                        uniqueKeys.add(trimmed);
                    }
                }
            }
        }

        this.keys.addAll(uniqueKeys);
        logger.info("Initialized ApiKeyPool with {} unique API key(s)", this.keys.size());
    }

    public synchronized String getActiveKey() {
        if (keys.isEmpty()) {
            throw new IllegalStateException("No valid Gemini API keys provided in ApiKeyPool.");
        }
        return keys.get(currentIndex);
    }

    public synchronized String rotate() {
        if (keys.size() <= 1) {
            return getActiveKey();
        }
        int previousIndex = currentIndex;
        currentIndex = (currentIndex + 1) % keys.size();
        totalRotations++;
        logger.info("ApiKeyPool rotated from Key #{} ({}) to Key #{} ({})", 
                previousIndex + 1, maskKey(keys.get(previousIndex)),
                currentIndex + 1, maskKey(keys.get(currentIndex)));
        return getActiveKey();
    }

    public synchronized String getMaskedCurrentKey() {
        return maskKey(getActiveKey());
    }

    public static String maskKey(String key) {
        if (key == null) return "null";
        int len = key.length();
        if (len <= 8) return "****";
        return key.substring(0, Math.min(6, len)) + "..." + key.substring(len - 4);
    }

    public static boolean isRotatableError(int statusCode, String responseBody) {
        // HTTP Status Codes for rate limits, overload, or temporary unavailabilities
        if (statusCode == 429  // Too Many Requests / Resource Exhausted
                || statusCode == 503  // Service Unavailable
                || statusCode == 529  // Site is Overloaded (Google / Cloudflare)
                || statusCode == 500  // Internal Server Error (transient)
                || statusCode == 502  // Bad Gateway
                || statusCode == 504) // Gateway Timeout
        {
            return true;
        }

        // Check if response body indicates quota or resource exhaustion even under other status codes
        if (responseBody != null && !responseBody.isEmpty()) {
            String lower = responseBody.toLowerCase();
            return lower.contains("resource_exhausted")
                    || lower.contains("rate_limit_exceeded")
                    || lower.contains("quota exceeded")
                    || lower.contains("quota_exceeded")
                    || lower.contains("exceeded your current quota")
                    || lower.contains("overloaded")
                    || lower.contains("temporarily unavailable");
        }

        return false;
    }

    public int size() {
        return keys.size();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getTotalRotations() {
        return totalRotations;
    }

    public boolean hasMultipleKeys() {
        return keys.size() > 1;
    }
}
