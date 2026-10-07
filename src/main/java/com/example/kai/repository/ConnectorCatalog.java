package com.example.kai.repository;

import java.util.List;

import org.springframework.stereotype.Component;

/** UI-facing connector catalogue. Adapters can be added later without changing KAI's agent core. */
@Component
public class ConnectorCatalog {

    public record Connector(String type, String name, String description, String locationLabel,
            String locationPlaceholder, boolean installed) {
    }

    private final AdapterRegistry adapters;

    public ConnectorCatalog(AdapterRegistry adapters) {
        this.adapters = adapters;
    }

    public List<Connector> all() {
        return List.of(
                connector("local", "Local / synced folder", "Folders on this computer, including synced OneDrive or Box folders.",
                        "Folder path", "C:\\Knowledge\\Runbooks"),
                connector("sharepoint", "SharePoint", "Microsoft 365 SharePoint site or document library.",
                        "Site or library URL", "https://company.sharepoint.com/sites/Knowledge"),
                connector("onedrive", "OneDrive", "OneDrive or OneDrive for Business folder.",
                        "Drive or folder URL", "https://.../my?id=..."),
                connector("s3", "Amazon S3", "S3 bucket and optional prefix.",
                        "Bucket", "company-knowledge"),
                connector("googledrive", "Google Drive", "Google Drive folder or Shared Drive.",
                        "Folder URL", "https://drive.google.com/drive/folders/..."),
                connector("custom", "Custom connector", "Reserved for an organisation-specific adapter.",
                        "Endpoint / root", "https://knowledge.example.com/api"));
    }

    public Connector get(String type) {
        return all().stream().filter(c -> c.type().equals(type)).findFirst().orElse(
                new Connector(type, type, "Custom knowledge connector.", "Location", "", adapters.supports(type)));
    }

    private Connector connector(String type, String name, String description, String label, String placeholder) {
        return new Connector(type, name, description, label, placeholder, adapters.supports(type));
    }
}
