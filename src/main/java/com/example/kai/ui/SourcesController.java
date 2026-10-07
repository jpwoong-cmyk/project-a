package com.example.kai.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.kai.config.KaiProperties.Target;
import com.example.kai.config.UserConfigService;
import com.example.kai.config.UserConfigService.SourceSettings;
import com.example.kai.repository.AdapterRegistry;
import com.example.kai.repository.ConnectorCatalog;

@Controller
public class SourcesController {

    public record SourceRow(SourceSettings source, String connectorName, boolean installed,
            String status, boolean ok) {
    }

    private final UserConfigService config;
    private final ConnectorCatalog connectors;
    private final AdapterRegistry adapters;

    public SourcesController(UserConfigService config, ConnectorCatalog connectors, AdapterRegistry adapters) {
        this.config = config;
        this.connectors = connectors;
        this.adapters = adapters;
    }

    @GetMapping("/sources")
    public String sources(Model model) {
        List<SourceRow> rows = new ArrayList<>();
        for (SourceSettings source : config.sources()) {
            boolean installed = adapters.supports(source.type());
            String status;
            boolean ok = false;
            if (!installed) {
                status = "Adapter not installed";
            }
            else if (!source.enabled()) {
                status = "Disabled";
            }
            else {
                try {
                    Target target = target(source);
                    adapters.require(source.type()).validate(target);
                    status = "Ready";
                    ok = true;
                }
                catch (Exception e) {
                    status = e.getMessage() == null ? "Connection problem" : e.getMessage();
                }
            }
            rows.add(new SourceRow(source, connectors.get(source.type()).name(), installed, status, ok));
        }
        model.addAttribute("active", "sources");
        model.addAttribute("rows", rows);
        model.addAttribute("connectors", connectors.all());
        return "sources";
    }

    @PostMapping("/sources/add")
    public String add(@RequestParam String type,
            @RequestParam String name,
            @RequestParam String location,
            @RequestParam(required = false) String library,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String prefix,
            @RequestParam(required = false) String drive,
            RedirectAttributes flash) {

        Map<String, String> options = new LinkedHashMap<>();
        put(options, "library", library);
        put(options, "region", region);
        put(options, "prefix", prefix);
        put(options, "drive", drive);

        String backupProblem = config.backupProblem(config.backupDir().toString(), type, location);
        if (backupProblem != null) {
            flash.addFlashAttribute("error", "Could not add source: " + backupProblem);
            return "redirect:/sources";
        }

        boolean installed = adapters.supports(type);
        boolean enabled = installed;
        if (installed) {
            try {
                Target target = new Target("new", type, normalized(type, location), location, name, options);
                adapters.require(type).validate(target);
            }
            catch (Exception e) {
                flash.addFlashAttribute("error", "Could not add source: " + e.getMessage());
                return "redirect:/sources";
            }
        }

        config.saveSource(null, type, name, normalized(type, location), enabled, options);
        if (installed) {
            flash.addFlashAttribute("success", name + " connected.");
        }
        else {
            flash.addFlashAttribute("warning", name + " was saved as a connector draft. The "
                    + connectors.get(type).name() + " adapter still needs to be implemented before it can scan.");
        }
        return "redirect:/sources";
    }

    @PostMapping("/sources/{id}/toggle")
    public String toggle(@PathVariable String id, RedirectAttributes flash) {
        SourceSettings source = config.source(id);
        if (source == null) return "redirect:/sources";
        if (!source.enabled() && !adapters.supports(source.type())) {
            flash.addFlashAttribute("error", "Cannot enable " + source.name() + ": its "
                    + connectors.get(source.type()).name() + " adapter is not installed yet.");
            return "redirect:/sources";
        }
        if (!source.enabled()) {
            try {
                adapters.require(source.type()).validate(target(source));
            }
            catch (Exception e) {
                flash.addFlashAttribute("error", "Cannot enable " + source.name() + ": " + e.getMessage());
                return "redirect:/sources";
            }
        }
        config.setSourceEnabled(id, !source.enabled());
        flash.addFlashAttribute("success", source.name() + (source.enabled() ? " disabled." : " enabled."));
        return "redirect:/sources";
    }

    @PostMapping("/sources/{id}/delete")
    public String delete(@PathVariable String id, RedirectAttributes flash) {
        SourceSettings source = config.source(id);
        if (source != null) {
            config.deleteSource(id);
            flash.addFlashAttribute("success", source.name() + " removed from KAI. The original knowledge source was not changed.");
        }
        return "redirect:/sources";
    }

    private static Target target(SourceSettings source) {
        return new Target(source.id(), source.type(), normalized(source.type(), source.location()),
                source.location(), source.name(), source.options());
    }

    private static String normalized(String type, String location) {
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
