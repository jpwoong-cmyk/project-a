package com.example.kai.config;

import org.springframework.stereotype.Component;

import com.example.kai.repository.AdapterRegistry;
import com.example.kai.repository.KnowledgeSourceAdapter;

/** Optional source validation used by UI/admin flows. KAI no longer blocks startup for configuration. */
@Component
public class SourceCheck {

    private final AdapterRegistry adapters;
    private final KaiProperties properties;

    public SourceCheck(AdapterRegistry adapters, KaiProperties properties) {
        this.adapters = adapters;
        this.properties = properties;
    }

    public String problem() {
        for (KaiProperties.Target source : properties.targets()) {
            if (!adapters.supports(source.type())) {
                return "Knowledge source '" + source.displayName() + "' uses adapter type '" + source.type()
                        + "', but that adapter is not installed. Available adapters: " + adapters.types();
            }
            KnowledgeSourceAdapter adapter = adapters.require(source.type());
            try {
                adapter.validate(source);
            }
            catch (Exception e) {
                return "KAI could not connect to knowledge source '" + source.displayName() + "' [" + source.type()
                        + "]: " + e.getMessage();
            }
        }
        return null;
    }
}
