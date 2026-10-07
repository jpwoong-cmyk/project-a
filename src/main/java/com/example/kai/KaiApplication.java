package com.example.kai;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.example.kai.config.UserConfigService;

@SpringBootApplication
public class KaiApplication {

    public static void main(String[] args) {
        List<String> appArgs = new ArrayList<>(Arrays.asList(args));
        if (appArgs.stream().noneMatch(a -> a.startsWith("--server.port="))) {
            appArgs.add("--server.port=" + UserConfigService.bootstrapPort());
        }
        if (appArgs.stream().noneMatch(a -> a.startsWith("--server.address="))) {
            appArgs.add("--server.address=127.0.0.1");
        }

        ConfigurableApplicationContext ctx;
        try {
            ctx = SpringApplication.run(KaiApplication.class, appArgs.toArray(String[]::new));
        }
        catch (Exception e) {
            System.err.println("KAI could not start. The reason is shown above.");
            return;
        }

        int port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port", "8080"));
        UserConfigService config = ctx.getBean(UserConfigService.class);
        String path = config.setupComplete() ? "/impact" : "/setup";
        String url = "http://localhost:" + port + path;

        System.out.println();
        System.out.println("KAI is running at " + url);
        System.out.println("Configuration is managed in the KAI interface. No properties file editing is required.");
        System.out.println("Keep this window open while you use KAI. Close it to stop KAI.");
        System.out.println();

        openBrowser(url);
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        }
        catch (Exception ignored) {
            // The URL is printed in the console if the OS refuses automatic browser opening.
        }
    }
}
