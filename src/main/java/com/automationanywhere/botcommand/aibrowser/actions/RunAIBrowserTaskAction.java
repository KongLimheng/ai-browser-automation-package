package com.automationanywhere.botcommand.aibrowser.actions;

import static com.automationanywhere.commandsdk.model.DataType.DICTIONARY;
import static com.automationanywhere.commandsdk.model.DataType.STRING;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.automationanywhere.botcommand.aibrowser.agent.AIBrowserAgent;
import com.automationanywhere.botcommand.aibrowser.gemini.ApiKeyPool;
import com.automationanywhere.botcommand.aibrowser.model.AgentResult;
import com.automationanywhere.botcommand.data.Value;
import com.automationanywhere.botcommand.data.impl.DictionaryValue;
import com.automationanywhere.botcommand.data.impl.StringValue;
import com.automationanywhere.botcommand.exception.BotCommandException;
import com.automationanywhere.commandsdk.annotations.BotCommand;
import com.automationanywhere.commandsdk.annotations.CommandPkg;
import com.automationanywhere.commandsdk.annotations.Execute;
import com.automationanywhere.commandsdk.annotations.Idx;
import com.automationanywhere.commandsdk.annotations.Pkg;
import com.automationanywhere.commandsdk.annotations.rules.NotEmpty;
import com.automationanywhere.commandsdk.model.AllowedTarget;
import com.automationanywhere.commandsdk.model.AttributeType;
import com.automationanywhere.commandsdk.model.DataType;
import com.automationanywhere.core.security.SecureString;

@BotCommand
@CommandPkg(label = "Run AI Browser Task", name = "runAIBrowserTask", description = "Autonomous browser automation using natural language prompts powered by Google Gemini and Playwright", node_label = "AI Browser: {{prompt}}", icon = "ai-browser.svg", allowed_agent_targets = {
        AllowedTarget.WINDOWS,
        AllowedTarget.MAC_OS
}, return_type = DICTIONARY, return_sub_type = STRING, return_required = true, comment = true)
public class RunAIBrowserTaskAction {

    private static final Logger logger = LogManager.getLogger(RunAIBrowserTaskAction.class);

    @Execute
    public DictionaryValue execute(

            @Idx(index = "1", type = AttributeType.TEXTAREA) @Pkg(label = "Natural Language Prompt / Objective", description = "Describe what the agent should accomplish on the browser (e.g. 'Search for laptop on Amazon and extract the price of the first item')") @NotEmpty String prompt,

            @Idx(index = "2", type = AttributeType.CREDENTIAL) @Pkg(label = "Gemini API Key(s)", description = "Google Gemini API Key or multiple keys separated by comma, semicolon, or newline for automatic failover.") SecureString apiKey,

            @Idx(index = "3", type = AttributeType.LIST) @Pkg(label = "Gemini API Keys (List Variable)", description = "Optional: List of Gemini API keys from an Automation Anywhere List variable.") List<Value> apiKeysList,

            @Idx(index = "4", type = AttributeType.TEXT) @Pkg(label = "Start URL (Optional)", description = "Initial website URL to open (e.g. https://www.google.com, google.com). Leave blank to let AI decide or navigate.", default_value = "", default_value_type = DataType.STRING) String startUrl,

            @Idx(index = "5", type = AttributeType.SELECT, options = {
                    @Idx.Option(index = "5.1", pkg = @Pkg(label = "Gemini 3.8 Flash (Latest frontier reasoning & agentic — Recommended)", value = "gemini-3.8-flash")),
                    @Idx.Option(index = "5.2", pkg = @Pkg(label = "Gemini 3.5 Flash (Advanced multimodal reasoning)", value = "gemini-3.5-flash")),
                    @Idx.Option(index = "5.3", pkg = @Pkg(label = "Gemini 3.0 Flash (Next-gen fast multimodal)", value = "gemini-3-flash")),

                    @Idx.Option(index = "5.8", pkg = @Pkg(label = "Custom Model (Specify below in Custom Model ID)", value = "CUSTOM"))
            }) @Pkg(label = "Gemini Model", description = "Choose the Gemini model for visual understanding and decision making", default_value = "gemini-3.8-flash", default_value_type = DataType.STRING) @NotEmpty String model,

            @Idx(index = "6", type = AttributeType.SELECT, options = {
                    @Idx.Option(index = "6.1", pkg = @Pkg(label = "Google Chrome (Installed system browser)", value = "CHROME")),
                    @Idx.Option(index = "6.2", pkg = @Pkg(label = "Microsoft Edge (Installed system browser)", value = "MSEDGE")),
                    @Idx.Option(index = "6.3", pkg = @Pkg(label = "Chromium (Bundled)", value = "CHROMIUM")),
                    @Idx.Option(index = "6.4", pkg = @Pkg(label = "Connect to Existing Browser (via Chrome DevTools Protocol CDP)", value = "CDP"))
            }) @Pkg(label = "Browser Type", description = "Target browser engine or existing session", default_value = "CHROME", default_value_type = DataType.STRING) @NotEmpty String browserType,

            @Idx(index = "7", type = AttributeType.CHECKBOX) @Pkg(label = "Run Headless (Background)", description = "Check to run invisibly in background. Uncheck to watch the browser live.", default_value = "false", default_value_type = DataType.BOOLEAN) Boolean headless,

            @Idx(index = "8", type = AttributeType.NUMBER) @Pkg(label = "Max Steps Limit", description = "Maximum number of autonomous browser steps to prevent runaway loops", default_value = "15", default_value_type = DataType.NUMBER) @NotEmpty Double maxSteps,

            @Idx(index = "9", type = AttributeType.TEXT) @Pkg(label = "CDP Endpoint URL (Advanced)", description = "DevTools WebSocket/HTTP URL (e.g. http://localhost:9222). Only used if Browser Type is set to CDP.", default_value = "", default_value_type = DataType.STRING) String cdpEndpoint,

            @Idx(index = "10", type = AttributeType.TEXT) @Pkg(label = "Log File Path (Optional)", description = "File path to save real-time execution logs (e.g. C:\\Bots\\Logs\\ai_browser.log). Accepts bot variables. If left blank, a timestamped file in the temp directory will be created.", default_value = "", default_value_type = DataType.STRING) String logFilePath,

            @Idx(index = "11", type = AttributeType.TEXT) @Pkg(label = "Custom Gemini Model ID (Optional)", description = "Specify custom or fine-tuned Gemini model ID (e.g. gemini-3.8-flash) if 'Custom Model' is selected above, or override model name.", default_value = "", default_value_type = DataType.STRING) String customModel,

            @Idx(index = "12", type = AttributeType.TEXT) @Pkg(label = "Download Directory (Optional)", description = "Directory path to save intercepted file downloads (e.g. D:\\abc or C:\\Bots\\Downloads). Accepts bot variables. If left blank, files are saved to a temporary folder.", default_value = "", default_value_type = DataType.STRING) String downloadDirectory,

            @Idx(index = "13", type = AttributeType.SELECT, options = {
                    @Idx.Option(index = "13.1", pkg = @Pkg(label = "Gemini 3.5 Flash Lite(Recommended Default Fallback)", value = "gemini-3.5-flash-lite")),
                    @Idx.Option(index = "13.2", pkg = @Pkg(label = "Gemini 1.5 Flash (Lightweight universal fallback)", value = "gemini-1.5-flash")),
                    @Idx.Option(index = "13.3", pkg = @Pkg(label = "Gemini 2.5 Flash (Legacy)", value = "gemini-2.5-flash")),
                    @Idx.Option(index = "13.4", pkg = @Pkg(label = "Disable Model Fallback", value = "NONE"))
            }) @Pkg(label = "Fallback Gemini Model (Optional)", description = "Automatic fallback model if primary model experiences HTTP 503 high demand or quota exhaustion.", default_value = "gemini-2.0-flash", default_value_type = DataType.STRING) String fallbackModel

    ) {
        // Resolve effective model ID
        String effectiveModel;
        if (customModel != null && !customModel.trim().isEmpty()) {
            effectiveModel = customModel.trim();
        } else if ("CUSTOM".equalsIgnoreCase(model)) {
            effectiveModel = "gemini-3.8-flash";
        } else if (model != null && !model.trim().isEmpty()) {
            effectiveModel = model.trim();
        } else {
            effectiveModel = "gemini-3.8-flash";
        }

        String effectiveFallbackModel = (fallbackModel != null && !fallbackModel.trim().isEmpty())
                ? fallbackModel.trim()
                : "gemini-2.0-flash";

        logger.info(
                "Executing RunAIBrowserTaskAction - Model: {}, FallbackModel: {}, Browser: {}, Headless: {}, LogPath: {}, DownloadDir: {}",
                effectiveModel, effectiveFallbackModel, browserType, headless, logFilePath, downloadDirectory);

        if (prompt == null || prompt.trim().isEmpty()) {
            throw new BotCommandException("Prompt / Objective cannot be empty.");
        }

        String rawApiKey = apiKey != null ? apiKey.getInsecureString() : null;
        List<String> listKeys = new ArrayList<>();
        if (apiKeysList != null) {
            for (Value val : apiKeysList) {
                if (val != null && val.get() != null) {
                    listKeys.add(val.get().toString());
                }
            }
        }

        ApiKeyPool keyPool = new ApiKeyPool(rawApiKey, listKeys);
        if (keyPool.size() == 0) {
            throw new BotCommandException(
                    "At least one Gemini API key must be provided via 'Gemini API Key(s)' or 'Gemini API Keys (List Variable)'.");
        }

        int stepsLimit = (maxSteps != null && maxSteps > 0) ? maxSteps.intValue() : 15;
        boolean isHeadless = headless != null && headless;

        AIBrowserAgent agent = new AIBrowserAgent(
                keyPool,
                effectiveModel,
                effectiveFallbackModel,
                isHeadless,
                stepsLimit,
                browserType,
                cdpEndpoint,
                logFilePath,
                downloadDirectory);

        AgentResult result = agent.execute(prompt, startUrl);

        Map<String, Value> responseMap = new LinkedHashMap<>();
        responseMap.put("outputResult", new StringValue(result.getOutputResult()));
        responseMap.put("status", new StringValue(result.getStatus()));
        responseMap.put("stepsLog", new StringValue(result.getStepsSummary()));
        responseMap.put("lastScreenshotPath", new StringValue(result.getLastScreenshotPath()));
        responseMap.put("stepCount", new StringValue(String.valueOf(result.getStepCount())));
        responseMap.put("totalRotations", new StringValue(String.valueOf(keyPool.getTotalRotations())));
        responseMap.put("activeKeyIndex", new StringValue(String.valueOf(keyPool.getCurrentIndex() + 1)));
        responseMap.put("totalKeysConfigured", new StringValue(String.valueOf(keyPool.size())));
        responseMap.put("failedStep", new StringValue(String.valueOf(result.getFailedStep())));
        responseMap.put("failedAction", new StringValue(result.getFailedAction()));
        responseMap.put("errorMessage", new StringValue(result.getErrorMessage()));
        responseMap.put("completedStep", new StringValue(String.valueOf(result.getStepCount())));
        responseMap.put("stepDetailsJson", new StringValue(result.getStepDetailsJson()));
        responseMap.put("logFilePath", new StringValue(result.getLogFilePath()));
        responseMap.put("downloadedFilePath", new StringValue(result.getDownloadedFilePath()));
        responseMap.put("downloadCount", new StringValue(String.valueOf(result.getDownloadCount())));
        responseMap.put("activeModel", new StringValue(result.getActiveModel()));
        responseMap.put("modelFallbackOccurred", new StringValue(String.valueOf(result.isModelFallbackOccurred())));

        DictionaryValue dictValue = new DictionaryValue();
        dictValue.set(responseMap);
        return dictValue;
    }
}
