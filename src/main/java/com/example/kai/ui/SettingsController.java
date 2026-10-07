package com.example.kai.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.kai.config.UserConfigService;

@Controller
public class SettingsController {

    private final UserConfigService config;

    public SettingsController(UserConfigService config) {
        this.config = config;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        var ai = config.ai();
        model.addAttribute("active", "settings");
        model.addAttribute("ai", ai);
        model.addAttribute("hasApiKey", ai.apiKey() != null && !ai.apiKey().isBlank());
        model.addAttribute("backupDir", config.backupDir().toString());
        model.addAttribute("parallel", config.parallel());
        model.addAttribute("port", config.port());
        model.addAttribute("configFile", config.configFile().toString());
        return "settings";
    }

    @PostMapping("/settings/ai")
    public String saveAi(@RequestParam String baseUrl,
            @RequestParam(required = false) String apiKey,
            @RequestParam String model,
            RedirectAttributes flash) {
        config.saveAi(baseUrl, apiKey, model);
        flash.addFlashAttribute("success", "AI connection settings saved.");
        return "redirect:/settings";
    }

    @PostMapping("/settings/general")
    public String saveGeneral(@RequestParam String backupDir,
            @RequestParam int parallel,
            @RequestParam int port,
            RedirectAttributes flash) {
        String problem = config.backupProblem(backupDir);
        if (problem != null) {
            flash.addFlashAttribute("error", problem);
            return "redirect:/settings";
        }
        int oldPort = config.port();
        config.saveGeneral(backupDir, parallel, port);
        if (oldPort != port) {
            flash.addFlashAttribute("warning", "Settings saved. The port change will take effect the next time KAI starts.");
        }
        else {
            flash.addFlashAttribute("success", "Processing settings saved.");
        }
        return "redirect:/settings";
    }
}
