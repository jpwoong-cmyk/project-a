package com.example.kai.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Dynamic view of the settings used by the engine. Values come from UserConfigService,
 * so the UI can change sources and processing settings without editing files by hand.
 */
@Component
public class KaiProperties {

    private final UserConfigService config;

    public KaiProperties(UserConfigService config) {
        this.config = config;
    }

    public Path backupDir() {
        return config.backupDir();
    }

    public List<Target> targets() {
        return config.targets();
    }

    public int parallel() {
        return config.parallel();
    }

    public record Target(String id, String type, String location, String entry, String name,
            Map<String, String> options) {

        public Target {
            options = options == null ? Map.of() : Map.copyOf(options);
        }

        public String displayName() {
            return name == null || name.isBlank() ? entry : name;
        }

        public String label() {
            return id + "[" + type + "]:" + location;
        }
    }
}
