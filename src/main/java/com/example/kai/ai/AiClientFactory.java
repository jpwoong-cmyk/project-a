package com.example.kai.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import com.example.kai.config.UserConfigService;
import com.example.kai.config.UserConfigService.AiSettings;

/** Builds ChatClient instances from the settings currently saved through the UI. */
@Component
public class AiClientFactory {

    private final UserConfigService config;
    private volatile Cached cached;

    public AiClientFactory(UserConfigService config) {
        this.config = config;
    }

    public ChatClient client(String systemPrompt) {
        return client(config.ai(), systemPrompt);
    }

    public ChatClient client(AiSettings settings, String systemPrompt) {
        if (settings == null || !settings.configured()) {
            throw new IllegalStateException("AI connection is not configured. Open Settings > AI connection.");
        }
        OpenAiChatModel model = model(settings);
        return ChatClient.builder(model).defaultSystem(systemPrompt).build();
    }

    private OpenAiChatModel model(AiSettings settings) {
        Cached current = cached;
        if (current != null && current.settings().equals(settings)) {
            return current.model();
        }
        synchronized (this) {
            current = cached;
            if (current != null && current.settings().equals(settings)) {
                return current.model();
            }
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .baseUrl(settings.baseUrl())
                    .apiKey(settings.apiKey())
                    .model(settings.model())
                    .build();
            OpenAiChatModel model = OpenAiChatModel.builder().options(options).build();
            cached = new Cached(settings, model);
            return model;
        }
    }

    private record Cached(AiSettings settings, OpenAiChatModel model) {
    }
}
