package com.automationanywhere.botcommand.aibrowser;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.automationanywhere.botcommand.aibrowser.gemini.ApiKeyPool;

public class ApiKeyPoolTest {

    @Test
    public void testDelimitedKeysParsing() {
        String raw = "AIzaSyKey1, AIzaSyKey2; AIzaSyKey3\nAIzaSyKey4\r\nAIzaSyKey5";
        ApiKeyPool pool = new ApiKeyPool(raw, null);

        Assert.assertEquals(pool.size(), 5);
        Assert.assertEquals(pool.getActiveKey(), "AIzaSyKey1");
        Assert.assertEquals(pool.getCurrentIndex(), 0);
    }

    @Test
    public void testListKeysIntegrationAndDeduplication() {
        String raw = "AIzaSyKey1, AIzaSyKey2";
        List<String> list = Arrays.asList("AIzaSyKey2", "AIzaSyKey3", "  ", null);
        ApiKeyPool pool = new ApiKeyPool(raw, list);

        Assert.assertEquals(pool.size(), 3);
        Assert.assertEquals(pool.getActiveKey(), "AIzaSyKey1");
    }

    @Test
    public void testKeyRotationCycle() {
        ApiKeyPool pool = new ApiKeyPool("KeyA,KeyB,KeyC", Collections.emptyList());

        Assert.assertEquals(pool.getActiveKey(), "KeyA");
        Assert.assertEquals(pool.getTotalRotations(), 0);

        // Rotate to KeyB
        String next = pool.rotate();
        Assert.assertEquals(next, "KeyB");
        Assert.assertEquals(pool.getActiveKey(), "KeyB");
        Assert.assertEquals(pool.getCurrentIndex(), 1);
        Assert.assertEquals(pool.getTotalRotations(), 1);

        // Rotate to KeyC
        next = pool.rotate();
        Assert.assertEquals(next, "KeyC");
        Assert.assertEquals(pool.getCurrentIndex(), 2);

        // Wrap around to KeyA
        next = pool.rotate();
        Assert.assertEquals(next, "KeyA");
        Assert.assertEquals(pool.getCurrentIndex(), 0);
        Assert.assertEquals(pool.getTotalRotations(), 3);
    }

    @Test
    public void testKeyMasking() {
        Assert.assertEquals(ApiKeyPool.maskKey("AIzaSyBC123456789xyz"), "AIzaSy...9xyz");
        Assert.assertEquals(ApiKeyPool.maskKey("short"), "****");
        Assert.assertEquals(ApiKeyPool.maskKey(null), "null");
    }

    @Test
    public void testRotatableErrorDetection() {
        // Status codes
        Assert.assertTrue(ApiKeyPool.isRotatableError(429, "Too Many Requests"));
        Assert.assertTrue(ApiKeyPool.isRotatableError(503, "Service Unavailable"));
        Assert.assertTrue(ApiKeyPool.isRotatableError(529, "Site is overloaded"));
        Assert.assertTrue(ApiKeyPool.isRotatableError(500, "Internal Server Error"));
        Assert.assertTrue(ApiKeyPool.isRotatableError(502, "Bad Gateway"));
        Assert.assertTrue(ApiKeyPool.isRotatableError(504, "Gateway Timeout"));

        // Body message match
        Assert.assertTrue(ApiKeyPool.isRotatableError(403, "{\"error\": {\"status\": \"RESOURCE_EXHAUSTED\"}}"));
        Assert.assertTrue(ApiKeyPool.isRotatableError(400, "Rate_limit_exceeded for current quota"));
        Assert.assertTrue(ApiKeyPool.isRotatableError(200, "overloaded server"));
        Assert.assertTrue(ApiKeyPool.isRotatableError(503,
                "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later."));
        Assert.assertTrue(ApiKeyPool.isRotatableError(200, "{\"status\": \"UNAVAILABLE\"}"));

        // Non-rotatable
        Assert.assertFalse(ApiKeyPool.isRotatableError(400, "{\"error\": \"INVALID_ARGUMENT: missing field\"}"));
        Assert.assertFalse(ApiKeyPool.isRotatableError(401, "{\"error\": \"API key not valid\"}"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testEmptyPoolThrows() {
        ApiKeyPool pool = new ApiKeyPool("", Collections.emptyList());
        pool.getActiveKey();
    }
}
