package com.example.kai.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.example.kai.config.UserConfigService;

@Controller
public class HistoryController {

    public record Run(String id, FileTime modified) {}

    private final UserConfigService config;

    public HistoryController(UserConfigService config) {
        this.config = config;
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("active", "history");
        model.addAttribute("runs", runs());
        model.addAttribute("backupDir", config.backupDir().toString());
        return "history";
    }

    @GetMapping("/history/report/{run}")
    public ResponseEntity<String> report(@PathVariable String run) throws IOException {
        if (!run.matches("[0-9-]+")) return ResponseEntity.notFound().build();
        Path file = config.backupDir().resolve(run).resolve("report.html").normalize();
        if (!file.startsWith(config.backupDir()) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(Files.readString(file));
    }

    private List<Run> runs() {
        Path root = config.backupDir();
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().matches("[0-9-]+"))
                    .filter(p -> Files.isRegularFile(p.resolve("report.html")))
                    .map(p -> {
                        try {
                            return new Run(p.getFileName().toString(), Files.getLastModifiedTime(p.resolve("report.html")));
                        }
                        catch (IOException e) {
                            return new Run(p.getFileName().toString(), FileTime.fromMillis(0));
                        }
                    })
                    .sorted(Comparator.comparing(Run::modified).reversed())
                    .toList();
        }
        catch (IOException e) {
            return List.of();
        }
    }
}
