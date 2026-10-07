package com.example.kai.ai;

import org.springframework.stereotype.Service;

import com.example.kai.config.UserConfigService;
import com.example.kai.config.UserConfigService.AiSettings;

@Service
public class AiConnectionService {

    private static final String SYSTEM = "You are a connection test. Follow the user's instruction exactly.";

    private final AiClientFactory clients;
    private final UserConfigService config;

    public AiConnectionService(AiClientFactory clients, UserConfigService config) {
        this.clients = clients;
        this.config = config;
    }

    public TestResult test(String baseUrl, String apiKey, String model) {
        AiSettings saved = config.ai();
        String effectiveKey = apiKey == null || apiKey.isBlank() ? saved.apiKey() : apiKey.trim();
        String effectiveUrl = baseUrl == null || baseUrl.isBlank() ? saved.baseUrl() : baseUrl.trim();
        String effectiveModel = model == null || model.isBlank() ? saved.model() : model.trim();

        if (effectiveUrl == null || effectiveUrl.isBlank()) {
            return new TestResult(false, "Enter the AI endpoint first.");
        }
        if (effectiveKey == null || effectiveKey.isBlank()) {
            return new TestResult(false, "Enter an API key first.");
        }
        if (effectiveModel == null || effectiveModel.isBlank()) {
            return new TestResult(false, "Enter a model name first.");
        }
        if (!effectiveUrl.startsWith("http://") && !effectiveUrl.startsWith("https://")) {
            return new TestResult(false, "The endpoint must start with http:// or https://");
        }

        try {
            String answer = clients.client(new AiSettings(effectiveUrl, effectiveKey, effectiveModel), SYSTEM)
                    .prompt().user("Reply with only the word OK.").call().content();
            return new TestResult(true, answer == null || answer.isBlank()
                    ? "Connection succeeded."
                    : "Connection succeeded (" + answer.trim() + ").");
        }
        catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) message = e.getClass().getSimpleName();
            return new TestResult(false, "Could not connect: " + message);
        }
    }

    public record TestResult(boolean ok, String message) {
    }
}
