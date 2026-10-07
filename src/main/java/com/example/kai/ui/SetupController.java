package com.example.kai.ui;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.kai.ai.AiConnectionService;
import com.example.kai.config.KaiProperties.Target;
import com.example.kai.config.UserConfigService;
import com.example.kai.repository.AdapterRegistry;
import com.example.kai.repository.ConnectorCatalog;
import com.example.kai.repository.KnowledgeSourceAdapter;

@Controller
public class SetupController {

    private final UserConfigService config;
    private final AiConnectionService ai;
    private final ConnectorCatalog connectors;
    private final AdapterRegistry adapters;

    public SetupController(UserConfigService config, AiConnectionService ai,
            ConnectorCatalog connectors, AdapterRegistry adapters) {
        this.config = config;
        this.ai = ai;
        this.connectors = connectors;
        this.adapters = adapters;
    }

    @GetMapping("/setup")
    public String setup(Model model) {
        var settings = config.ai();
        model.addAttribute("ai", settings);
        model.addAttribute("hasApiKey", settings.apiKey() != null && !settings.apiKey().isBlank());
        model.addAttribute("connectors", connectors.all());
        model.addAttribute("backupDir", config.backupDir().toString());
        model.addAttribute("parallel", config.parallel());
        return "setup";
    }

    @PostMapping("/setup")
    public String saveSetup(@RequestParam String baseUrl,
            @RequestParam(required = false) String apiKey,
            @RequestParam String modelName,
            @RequestParam String sourceType,
            @RequestParam String sourceName,
            @RequestParam String sourceLocation,
            @RequestParam(required = false) String library,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String prefix,
            @RequestParam String backupDir,
            @RequestParam(defaultValue = "4") int parallel,
            Model model) {

        String backupProblem = config.backupProblem(backupDir, sourceType, sourceLocation);
        if (backupProblem != null) {
            return setupError(model, backupProblem);
        }

        config.saveAi(baseUrl, apiKey, modelName);
        config.saveGeneral(backupDir, parallel, config.port());

        var aiTest = ai.test(baseUrl, apiKey, modelName);
        if (!aiTest.ok()) {
            return setupError(model, "AI connection test failed: " + aiTest.message());
        }

        if (!adapters.supports(sourceType)) {
            return setupError(model, "The " + connectors.get(sourceType).name()
                    + " connector UI is ready, but its adapter is not installed yet. Choose Local / synced folder for this build.");
        }

        Map<String, String> options = new LinkedHashMap<>();
        put(options, "library", library);
        put(options, "region", region);
        put(options, "prefix", prefix);
        try {
            Target probe = new Target("setup-source", sourceType, normalize(sourceType, sourceLocation), sourceLocation, sourceName, options);
            adapters.require(sourceType).validate(probe);
        }
        catch (Exception e) {
            return setupError(model, "Knowledge source could not be opened: " + e.getMessage());
        }

        config.saveSource(null, sourceType, sourceName, normalize(sourceType, sourceLocation), true, options);
        config.setSetupComplete(true);
        return "redirect:/impact";
    }

    private String setupError(Model model, String error) {
        setup(model);
        model.addAttribute("error", error);
        return "setup";
    }

    @PostMapping("/api/ai/test")
    @ResponseBody
    public Map<String, Object> testAi(@RequestParam(required = false) String baseUrl,
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String model) {
        var result = ai.test(baseUrl, apiKey, model);
        return Map.of("ok", result.ok(), "message", result.message());
    }

    @PostMapping("/api/source/test")
    @ResponseBody
    public Map<String, Object> testSource(@RequestParam String type,
            @RequestParam String location,
            @RequestParam(required = false) String name) {
        if (!adapters.supports(type)) {
            return Map.of("ok", false, "message", connectors.get(type).name() + " adapter is not installed in this build yet.");
        }
        try {
            KnowledgeSourceAdapter adapter = adapters.require(type);
            Target target = new Target("test", type, normalize(type, location), location,
                    name == null || name.isBlank() ? "Test source" : name, Map.of());
            adapter.validate(target);
            int count = adapter.list(target).size();
            return Map.of("ok", true, "message", "Connected. Found " + count + " supported document" + (count == 1 ? "" : "s") + ".");
        }
        catch (Exception e) {
            return Map.of("ok", false, "message", "Could not connect: " + e.getMessage());
        }
    }

    private static String normalize(String type, String location) {
        if (!"local".equals(type)) return location;
        String v = location == null ? "" : location.trim();
        if (v.startsWith("~/") || v.startsWith("~\\")) {
            v = System.getProperty("user.home") + v.substring(1);
        }
        return Path.of(v).toAbsolutePath().normalize().toString();
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value.trim());
    }
}
