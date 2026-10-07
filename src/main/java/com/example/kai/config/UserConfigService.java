package com.example.kai.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.kai.config.KaiProperties.Target;

/**
 * Persistent application settings owned by the KAI UI.
 *
 * End users no longer edit kai.properties. KAI keeps a private settings file in the
 * user's application-data folder and the browser UI is the only supported editor.
 */
@Service
public class UserConfigService {

    private static final String FILE_NAME = "settings.properties";
    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_PARALLEL = 4;
    private static final String DEFAULT_MODEL = "gpt-5.6-terra";

    public record AiSettings(String baseUrl, String apiKey, String model) {
        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank()
                    && apiKey != null && !apiKey.isBlank()
                    && model != null && !model.isBlank();
        }
    }

    public record SourceSettings(String id, String type, String name, String location,
            boolean enabled, Map<String, String> options) {
        public SourceSettings {
            options = options == null ? Map.of() : Map.copyOf(options);
        }
    }

    private final Path configDir;
    private final Path file;
    private final Properties values = new Properties();

    public UserConfigService() {
        this.configDir = configDirectory();
        this.file = configDir.resolve(FILE_NAME);
        load();
    }

    public static Path configDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, "KAI").toAbsolutePath().normalize();
            }
        }
        if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "KAI")
                    .toAbsolutePath().normalize();
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg, "kai").toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".config", "kai").toAbsolutePath().normalize();
    }

    /** Read before Spring starts so the saved port can be applied without a user-editable file. */
    public static int bootstrapPort() {
        Path file = configDirectory().resolve(FILE_NAME);
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
            catch (IOException ignored) {
            }
        }
        return intValue(p.getProperty("app.port"), DEFAULT_PORT, 1, 65535);
    }

    public synchronized Path configFile() {
        return file;
    }

    public synchronized boolean setupComplete() {
        return Boolean.parseBoolean(values.getProperty("app.setup-complete", "false"));
    }

    public synchronized void setSetupComplete(boolean complete) {
        values.setProperty("app.setup-complete", Boolean.toString(complete));
        save();
    }

    public synchronized AiSettings ai() {
        String baseUrl = clean(values.getProperty("ai.base-url"));
        String model = clean(values.getProperty("ai.model"));
        String key = clean(values.getProperty("ai.api-key"));

        // Smooth migration from the original hackathon package: existing environment
        // variables continue to work, but the UI can replace them later.
        if (baseUrl == null) baseUrl = clean(System.getenv("ICA_BASE_URL_V1"));
        if (key == null) key = clean(System.getenv("ICA_CODEX_KEY"));
        if (model == null) model = DEFAULT_MODEL;
        return new AiSettings(baseUrl, key, model);
    }

    public synchronized boolean hasSavedApiKey() {
        return clean(values.getProperty("ai.api-key")) != null;
    }

    public synchronized void saveAi(String baseUrl, String apiKey, String model) {
        setOrRemove("ai.base-url", clean(baseUrl));
        setOrRemove("ai.model", clean(model) == null ? DEFAULT_MODEL : clean(model));
        // Blank means "keep the current key" so masked settings pages never erase it accidentally.
        if (apiKey != null && !apiKey.isBlank()) {
            values.setProperty("ai.api-key", apiKey.trim());
        }
        save();
    }

    public synchronized void clearApiKey() {
        values.remove("ai.api-key");
        save();
    }

    public synchronized Path backupDir() {
        String configured = clean(values.getProperty("app.backup-dir"));
        Path dir = configured == null ? configDir.resolve("backups") : expandPath(configured);
        try {
            Files.createDirectories(dir);
        }
        catch (IOException ignored) {
        }
        return dir.toAbsolutePath().normalize();
    }

    public synchronized int parallel() {
        return intValue(values.getProperty("app.parallel"), DEFAULT_PARALLEL, 1, 32);
    }

    public synchronized int port() {
        return intValue(values.getProperty("app.port"), DEFAULT_PORT, 1, 65535);
    }

    public synchronized String backupProblem(String backupDir) {
        return backupProblem(backupDir, null, null);
    }

    public synchronized String backupProblem(String backupDir, String prospectiveType, String prospectiveLocation) {
        String raw = clean(backupDir);
        Path backup = expandPath(raw == null ? configDir.resolve("backups").toString() : raw);
        for (SourceSettings source : sources()) {
            if (!"local".equals(source.type())) continue;
            Path root = expandPath(source.location());
            if (backup.startsWith(root)) {
                return "Backup location cannot be inside local knowledge source '" + source.name() + "'.";
            }
        }
        if ("local".equals(prospectiveType) && clean(prospectiveLocation) != null) {
            Path root = expandPath(prospectiveLocation);
            if (backup.startsWith(root)) {
                return "Backup location cannot be inside the local knowledge source you are connecting.";
            }
        }
        return null;
    }

    public synchronized void saveGeneral(String backupDir, int parallel, int port) {
        String backup = clean(backupDir);
        if (backup == null) {
            backup = configDir.resolve("backups").toString();
        }
        values.setProperty("app.backup-dir", expandPath(backup).toString());
        values.setProperty("app.parallel", Integer.toString(Math.max(1, Math.min(parallel, 32))));
        values.setProperty("app.port", Integer.toString(Math.max(1, Math.min(port, 65535))));
        save();
    }

    public synchronized List<SourceSettings> sources() {
        Set<String> ids = new LinkedHashSet<>();
        for (String key : values.stringPropertyNames()) {
            if (key.startsWith("source.")) {
                String rest = key.substring("source.".length());
                int dot = rest.indexOf('.');
                if (dot > 0) ids.add(rest.substring(0, dot));
            }
        }
        List<SourceSettings> result = new ArrayList<>();
        ids.stream().sorted().forEach(id -> {
            String prefix = "source." + id + ".";
            String type = clean(values.getProperty(prefix + "type"));
            String name = clean(values.getProperty(prefix + "name"));
            String location = clean(values.getProperty(prefix + "location"));
            boolean enabled = Boolean.parseBoolean(values.getProperty(prefix + "enabled", "true"));
            if (type == null || location == null) return;
            Map<String, String> options = new LinkedHashMap<>();
            String op = prefix + "option.";
            values.stringPropertyNames().stream().sorted().filter(k -> k.startsWith(op)).forEach(k -> {
                String v = clean(values.getProperty(k));
                if (v != null) options.put(k.substring(op.length()), v);
            });
            result.add(new SourceSettings(id, type, name == null ? id : name, location, enabled, options));
        });
        result.sort(Comparator.comparing(SourceSettings::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public synchronized List<Target> targets() {
        List<Target> result = new ArrayList<>();
        for (SourceSettings source : sources()) {
            if (!source.enabled()) continue;
            String location = source.location();
            if ("local".equals(source.type())) {
                location = expandPath(location).toString();
            }
            result.add(new Target(source.id(), source.type(), location, source.location(), source.name(), source.options()));
        }
        return List.copyOf(result);
    }

    public synchronized SourceSettings source(String id) {
        return sources().stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }

    public synchronized String saveSource(String requestedId, String type, String name, String location,
            boolean enabled, Map<String, String> options) {
        String id = slug(clean(requestedId) == null ? name : requestedId);
        if (id.isBlank()) id = "source";
        String base = id;
        int i = 2;
        while (source(id) != null && (requestedId == null || requestedId.isBlank())) {
            id = base + "-" + i++;
        }

        removeSourceKeys(id);
        String prefix = "source." + id + ".";
        values.setProperty(prefix + "type", clean(type) == null ? "local" : type.trim().toLowerCase(Locale.ROOT));
        values.setProperty(prefix + "name", clean(name) == null ? id : name.trim());
        values.setProperty(prefix + "location", clean(location) == null ? "" : location.trim());
        values.setProperty(prefix + "enabled", Boolean.toString(enabled));
        if (options != null) {
            options.forEach((k, v) -> {
                String key = clean(k);
                String value = clean(v);
                if (key != null && value != null) values.setProperty(prefix + "option." + key, value);
            });
        }
        save();
        return id;
    }

    public synchronized void setSourceEnabled(String id, boolean enabled) {
        if (source(id) == null) return;
        values.setProperty("source." + id + ".enabled", Boolean.toString(enabled));
        save();
    }

    public synchronized void deleteSource(String id) {
        removeSourceKeys(id);
        save();
    }

    public synchronized void reset() {
        values.clear();
        save();
    }

    private void load() {
        try {
            Files.createDirectories(configDir);
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    values.load(in);
                }
            }
            else {
                values.setProperty("app.parallel", Integer.toString(DEFAULT_PARALLEL));
                values.setProperty("app.port", Integer.toString(DEFAULT_PORT));
                values.setProperty("ai.model", DEFAULT_MODEL);
                save();
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("KAI cannot read its private settings at " + file + ": " + e.getMessage(), e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(configDir);
            Path temp = Files.createTempFile(configDir, "settings-", ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                values.store(out, "KAI private settings - managed by the KAI UI");
            }
            try {
                Files.setPosixFilePermissions(temp, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
            catch (UnsupportedOperationException ignored) {
                // Windows: the file stays inside the current user's APPDATA folder.
            }
            try {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("KAI cannot save its private settings at " + file + ": " + e.getMessage(), e);
        }
    }

    private void removeSourceKeys(String id) {
        String prefix = "source." + id + ".";
        new ArrayList<>(values.stringPropertyNames()).stream()
                .filter(k -> k.startsWith(prefix)).forEach(values::remove);
    }

    private static int intValue(String raw, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(raw == null ? "" : raw.trim());
            return value >= min && value <= max ? value : fallback;
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void setOrRemove(String key, String value) {
        if (value == null) values.remove(key);
        else values.setProperty(key, value);
    }

    private static String slug(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static Path expandPath(String raw) {
        String v = raw.trim();
        if (v.startsWith("~/") || v.startsWith("~\\")) {
            v = System.getProperty("user.home") + v.substring(1);
        }
        return Path.of(v).toAbsolutePath().normalize();
    }
}
