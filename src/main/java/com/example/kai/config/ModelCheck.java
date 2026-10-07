package com.example.kai.config;

import org.springframework.stereotype.Component;

import com.example.kai.ai.AiConnectionService;

/** Compatibility service retained for callers that want an explicit connection check. */
@Component
public class ModelCheck {

    private final AiConnectionService connection;
    private final UserConfigService config;

    public ModelCheck(AiConnectionService connection, UserConfigService config) {
        this.connection = connection;
        this.config = config;
    }

    public String problem() {
        var ai = config.ai();
        if (!ai.configured()) return "AI connection is not configured.";
        var result = connection.test(ai.baseUrl(), null, ai.model());
        return result.ok() ? null : result.message();
    }
}
