# KAI — UI Full-Stack Plan

## Product rule

**KAI owns the analysis workflow. The user owns the knowledge base.**

A connected source can be a local/synced folder today and SharePoint, OneDrive, S3, Google Drive or another enterprise repository through additional adapters later.

## Runtime flow

```text
Double-click KAI
     |
     v
Spring Boot starts on localhost
     |
     +--> first run -> Setup UI
     |       |-- AI endpoint/key/model
     |       |-- knowledge source
     |       `-- backup/processing
     |
     `--> configured -> Impact Analysis workspace
                         |
                         v
                    Orchestrator
                     /   |    \
                Scanner Editor Reviewer
                         |
                         v
                    Human review
                         |
                         v
                    ChangeWriter
         stale check -> report -> backup -> write
                         |
                  rollback on failure
```

## UI areas

- Impact Analysis: change request, live agent log, findings, diffs, human approval, Finalize.
- Knowledge Sources: add/remove/enable/disable sources via connector templates.
- History: saved final reports from backup runs.
- Settings: AI connection, backup location, concurrency, local port.

## Configuration model

No `kai.properties` is required for end users.

The UI writes KAI's private settings into the current OS user's application-data folder. `UserConfigService` converts enabled source records into the existing `KaiProperties.Target` shape at runtime so the Orchestrator and ChangeWriter stay source-agnostic.

## Connector state

- Local / synced folder: implemented.
- SharePoint: UI shell + adapter seam, provider code pending.
- OneDrive: UI shell + adapter seam, provider code pending.
- Amazon S3: UI shell + adapter seam, provider code pending.
- Google Drive: UI shell + adapter seam, provider code pending.
- Custom: UI shell + adapter seam, provider code pending.

Unimplemented connectors are saved disabled and never presented as working connections.

## Next engineering stages

1. Build and run this branch on JDK 25; fix any dependency/API drift found by the real Maven build.
2. Add a production secret store (Windows Credential Manager / macOS Keychain).
3. Implement SharePoint/OneDrive adapter via Microsoft Graph, starting read-only.
4. Add S3 adapter with IAM/SSO-friendly credential handling.
5. Add Google Drive adapter.
6. Add provider-aware write-back only after read-only impact analysis is stable.
7. Sign Windows binary and notarize the macOS build for normal enterprise distribution.
