package com.example.kai.repository;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

/**
 * Registers every KnowledgeSourceAdapter Spring bean under its adapter type.
 * The Orchestrator asks this registry for a source implementation and remains
 * completely unaware of provider-specific APIs.
 */
@Component
public class AdapterRegistry {

    private final Map<String, KnowledgeSourceAdapter> adapters = new TreeMap<>();

    public AdapterRegistry(List<KnowledgeSourceAdapter> adapters) {
        for (KnowledgeSourceAdapter adapter : adapters) {
            KnowledgeSourceAdapter previous = this.adapters.put(adapter.type(), adapter);
            if (previous != null) {
                throw new IllegalStateException("Duplicate knowledge adapter type '" + adapter.type() + "'");
            }
        }
    }

    public KnowledgeSourceAdapter require(String type) {
        KnowledgeSourceAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("No knowledge adapter for '" + type + "'. Available: " + adapters.keySet());
        }
        return adapter;
    }

    public boolean supports(String type) {
        return adapters.containsKey(type);
    }

    public List<String> types() {
        return List.copyOf(adapters.keySet());
    }
}
