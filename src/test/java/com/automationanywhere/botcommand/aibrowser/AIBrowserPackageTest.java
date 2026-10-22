package com.automationanywhere.botcommand.aibrowser;

import com.automationanywhere.botcommand.aibrowser.agent.AIBrowserAgent;
import com.automationanywhere.botcommand.aibrowser.gemini.ApiKeyPool;
import com.automationanywhere.botcommand.aibrowser.gemini.GeminiClient;
import com.automationanywhere.botcommand.aibrowser.model.AgentAction;
import com.automationanywhere.botcommand.aibrowser.model.AgentResult;
import com.automationanywhere.botcommand.aibrowser.model.StepRecord;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AIBrowserPackageTest {

    @Test
    public void testAgentActionSerialization() {
        AgentAction action = new AgentAction();
        action.setType("CLICK");
        action.setElementId(4);
        action.setDescription("Click on search button");

        Gson gson = new Gson();
        String json = gson.toJson(action);

        AgentAction deserialized = gson.fromJson(json, AgentAction.class);
        Assert.assertEquals(deserialized.getType(), "CLICK");
        Assert.assertEquals(deserialized.getElementId().intValue(), 4);
        Assert.assertEquals(deserialized.getDescription(), "Click on search button");
    }

    @Test
    public void testAgentActionTypeAction() {
        AgentAction action = new AgentAction();
        action.setType("TYPE");
        action.setElementId(2);
        action.setText("Automation Anywhere");
        action.setPressEnter(true);

        Assert.assertEquals(action.getType(), "TYPE");
        Assert.assertEquals(action.getText(), "Automation Anywhere");
        Assert.assertTrue(action.isPressEnter());
    }

    @Test
    public void testStepRecordFormattingAndSuccess() {
        StepRecord step1 = new StepRecord(1, "NAVIGATE", "Navigate to https://google.com");
        step1.setTargetUrl("https://google.com");
        step1.setDurationMs(1250);
        step1.setStatus("SUCCESS");

        Assert.assertTrue(step1.isSuccess());
        Assert.assertEquals(step1.getActionType(), "NAVIGATE");
        Assert.assertEquals(step1.getTargetUrl(), "https://google.com");
        Assert.assertTrue(step1.toLogFormat().contains("Step 1 [SUCCESS] (1250 ms): [NAVIGATE] Navigate to https://google.com"));

        StepRecord step2 = new StepRecord(2, "CLICK", "Click search button");
        step2.setElementId(5);
        step2.setDurationMs(5010);
        step2.setStatus("FAILED");
        step2.setErrorMessage("Timeout 5000ms waiting for element [data-ai-id='5']");

        Assert.assertFalse(step2.isSuccess());
        Assert.assertEquals(step2.getElementId().intValue(), 5);
        Assert.assertTrue(step2.toLogFormat().contains("Step 2 [FAILED] (5010 ms): [CLICK] Click search button -> Error: Timeout 5000ms"));
    }

    @Test
    public void testAgentResultDiagnosticsAndJsonDetails() {
        List<StepRecord> records = new ArrayList<>();

        StepRecord r1 = new StepRecord(1, "NAVIGATE", "Opened https://amazon.com");
        r1.setStatus("SUCCESS");
        r1.setDurationMs(1100);
        records.add(r1);

        StepRecord r2 = new StepRecord(2, "TYPE", "Typed search query");
        r2.setStatus("SUCCESS");
        r2.setDurationMs(600);
        records.add(r2);

        StepRecord r3 = new StepRecord(3, "CLICK", "Clicked product title");
        r3.setStatus("FAILED");
        r3.setErrorMessage("Locator timeout 5000ms");
        r3.setDurationMs(5020);
        records.add(r3);

        AgentResult result = new AgentResult(
                "FAILED",
                "Execution halted due to click timeout",
                Arrays.asList(r1.toLogFormat(), r2.toLogFormat(), r3.toLogFormat()),
                records,
                "/tmp/failure_shot.png",
                3,
                3,
                "CLICK",
                "Locator timeout 5000ms",
                "/tmp/logs/run.log",
                "/tmp/downloads/report.xlsx",
                1
        );

        Assert.assertEquals(result.getStatus(), "FAILED");
        Assert.assertEquals(result.getStepCount(), 3);
        Assert.assertEquals(result.getFailedStep(), 3);
        Assert.assertEquals(result.getFailedAction(), "CLICK");
        Assert.assertEquals(result.getErrorMessage(), "Locator timeout 5000ms");
        Assert.assertEquals(result.getLogFilePath(), "/tmp/logs/run.log");
        Assert.assertEquals(result.getLastScreenshotPath(), "/tmp/failure_shot.png");
        Assert.assertEquals(result.getDownloadedFilePath(), "/tmp/downloads/report.xlsx");
        Assert.assertEquals(result.getDownloadCount(), 1);

        // Verify steps summary contains [SUCCESS] and [FAILED] tags
        String summary = result.getStepsSummary();
        Assert.assertTrue(summary.contains("Step 1 [SUCCESS]"));
        Assert.assertTrue(summary.contains("Step 2 [SUCCESS]"));
        Assert.assertTrue(summary.contains("Step 3 [FAILED]"));

        // Verify JSON serialization
        String json = result.getStepDetailsJson();
        Assert.assertNotNull(json);
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        Assert.assertEquals(arr.size(), 3);
        Assert.assertEquals(arr.get(0).getAsJsonObject().get("status").getAsString(), "SUCCESS");
        Assert.assertEquals(arr.get(2).getAsJsonObject().get("status").getAsString(), "FAILED");
        Assert.assertEquals(arr.get(2).getAsJsonObject().get("actionType").getAsString(), "CLICK");
    }

    @Test
    public void testUrlNormalization() {
        // Bare domains should default to https://
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("google.com"), "https://google.com");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("www.google.com"), "https://www.google.com");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("subdomain.example.com/path?param=123"), "https://subdomain.example.com/path?param=123");

        // Existing http/https schemes should remain untouched
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("https://google.com"), "https://google.com");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("http://example.org/test"), "http://example.org/test");

        // Localhost and loopback IPs should default to http://
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("localhost"), "http://localhost");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("localhost:8080/app"), "http://localhost:8080/app");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("127.0.0.1:3000"), "http://127.0.0.1:3000");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("0.0.0.0:5000"), "http://0.0.0.0:5000");

        // Special schemes
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("about:blank"), "about:blank");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("data:text/html,<h1>Test</h1>"), "data:text/html,<h1>Test</h1>");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("file:///C:/path/file.html"), "file:///C:/path/file.html");

        // File paths
        Assert.assertTrue(AIBrowserAgent.normalizeUrl("C:\\test\\index.html").startsWith("file:///"));
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("/var/www/index.html"), "file:///var/www/index.html");

        // Whitespace and empty handling
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("   google.com   "), "https://google.com");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl(null), "");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl(""), "");
        Assert.assertEquals(AIBrowserAgent.normalizeUrl("   "), "");
    }

    @Test
    public void testGeminiClientModelCandidates() {
        ApiKeyPool pool = new ApiKeyPool("dummy-key", null);

        // Primary 3.8 Flash, default fallback 2.0 Flash with 1.5 Flash safety chain
        GeminiClient clientDefault = new GeminiClient(pool, "gemini-3.8-flash");
        List<String> defaultCandidates = clientDefault.getModelCandidates();
        Assert.assertEquals(defaultCandidates, Arrays.asList("gemini-3.8-flash", "gemini-2.0-flash", "gemini-1.5-flash"));
        Assert.assertEquals(clientDefault.getActiveModel(), "gemini-3.8-flash");
        Assert.assertFalse(clientDefault.isFallbackActive());

        // Primary 3.8 Flash with legacy fallback 2.5 Flash -> auto appends 2.0 and 1.5 Flash safety chain
        GeminiClient client25 = new GeminiClient(pool, "gemini-3.8-flash", "gemini-2.5-flash");
        List<String> c25Candidates = client25.getModelCandidates();
        Assert.assertEquals(c25Candidates, Arrays.asList("gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash"));

        // Fallback explicitly disabled with "NONE"
        GeminiClient clientNoFallback = new GeminiClient(pool, "gemini-3.8-flash", "NONE");
        List<String> noFallbackCandidates = clientNoFallback.getModelCandidates();
        Assert.assertEquals(noFallbackCandidates, Collections.singletonList("gemini-3.8-flash"));
    }

    @Test
    public void testPressEnterIntentDetection() {
        AgentAction action1 = new AgentAction();
        action1.setType("TYPE");
        action1.setElementId(6);
        action1.setText("search wikipedia");
        action1.setPressEnter(false);
        action1.setDescription("Type 'search wikipedia' into the search bar and press Enter.");
        // Should detect 'press Enter' from description even if boolean is false
        Assert.assertTrue(action1.isPressEnter(), "Should detect press Enter from description");

        AgentAction action2 = new AgentAction();
        action2.setType("TYPE");
        action2.setElementId(2);
        action2.setText("John Doe");
        action2.setDescription("Fill first name in the input box");
        Assert.assertFalse(action2.isPressEnter(), "Should not press Enter when not mentioned");

        AgentAction action3 = new AgentAction();
        action3.setType("TYPE");
        action3.setElementId(5);
        action3.setText("laptops");
        action3.setDescription("Hit enter to search catalog");
        Assert.assertTrue(action3.isPressEnter(), "Should detect 'hit enter'");

        AgentAction action4 = new AgentAction();
        action4.setType("TYPE");
        action4.setPressEnter(true);
        action4.setDescription("Type something");
        Assert.assertTrue(action4.isPressEnter(), "Explicit boolean true should always return true");
    }

    @Test
    public void testModelUnavailableErrorDetection() {
        // HTTP 404 for deprecated model
        String deprecated404 = "{\n" +
                " \"error\": {\n" +
                " \"code\": 404,\n" +
                " \"message\": \"This model models/gemini-2.5-flash is no longer available to new users. Please update your code to use models/gemini-3.8-flash for the latest features and improvements.\",\n" +
                " \"status\": \"NOT_FOUND\"\n" +
                " }\n" +
                "}";
        Assert.assertTrue(GeminiClient.isModelUnavailableError(404, deprecated404));

        // HTTP 503 high demand
        String highDemand503 = "{\n" +
                " \"error\": {\n" +
                " \"code\": 503,\n" +
                " \"message\": \"This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.\",\n" +
                " \"status\": \"UNAVAILABLE\"\n" +
                " }\n" +
                "}";
        Assert.assertTrue(GeminiClient.isModelUnavailableError(503, highDemand503));

        // HTTP 529 site overloaded
        Assert.assertTrue(GeminiClient.isModelUnavailableError(529, "Site overloaded"));

        // Regular non-model client error (e.g. 400 invalid argument)
        Assert.assertFalse(GeminiClient.isModelUnavailableError(400, "{\"error\": \"INVALID_ARGUMENT: missing prompt\"}"));
    }

    @Test
    public void testAgentResultModelFields() {
        AgentResult result = new AgentResult(
                "SUCCESS",
                "Completed task",
                Collections.emptyList(),
                Collections.emptyList(),
                "/tmp/shot.png",
                2,
                0,
                "",
                "",
                "/tmp/run.log",
                "",
                0,
                "gemini-2.5-flash",
                true
        );

        Assert.assertEquals(result.getActiveModel(), "gemini-2.5-flash");
        Assert.assertTrue(result.isModelFallbackOccurred());
        Assert.assertEquals(result.getStatus(), "SUCCESS");
    }
}
