package com.automationanywhere.botcommand.aibrowser;

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
}
