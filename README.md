# KAI UI Full-Stack V1

KAI is a local Spring Boot application that performs knowledge change-impact analysis with three LLM agents:

- **Scanner Agent** — decides whether a document is affected.
- **Editor Agent** — proposes exact passage edits for supported writable documents.
- **Reviewer Agent** — checks the proposal against the requested change.

A plain-Java `Orchestrator` controls the workflow. The agents never choose repositories or write files directly.

## What changed in this branch

This branch removes the end-user requirement to edit `kai.properties`.

On first launch KAI opens a browser setup flow for:

1. AI endpoint, API key and model.
2. Knowledge source connection.
3. Backup location and processing concurrency.
4. Final review and workspace launch.

After setup, KAI has four workspace screens:

- **Impact Analysis** — run Scanner → Editor → Reviewer, review diffs and Finalize selected changes.
- **Knowledge Sources** — add, enable, disable and remove connected sources.
- **History** — open final reports from completed write-back runs.
- **Settings** — update AI, backup, parallelism and local port settings.

Settings are written by the application to the current user's private application-data folder:

- Windows: `%APPDATA%\\KAI\\settings.properties`
- macOS: `~/Library/Application Support/KAI/settings.properties`
- Linux: `$XDG_CONFIG_HOME/kai/settings.properties` or `~/.config/kai/settings.properties`

The file is an implementation detail. Users are not expected to edit it.

## Knowledge-source architecture

```text
User knowledge source
       |
       v
KnowledgeSourceAdapter
       |
       v
AdapterRegistry
       |
       v
Orchestrator
  |       |       |
Scanner  Editor  Reviewer
       |
       v
Human review
       |
       v
ChangeWriter
(stale check -> report -> backup -> write -> rollback)
```

The UI includes connector shells for Local folders, SharePoint, OneDrive, Amazon S3, Google Drive and Custom connectors. **Local / synced folder is the implemented provider in this branch.** Unimplemented providers can be saved as disabled connector drafts without pretending they are connected.

## AI configuration

Spring AI is configured dynamically from the UI. The application no longer depends on a boot-time `spring.ai.openai.*` properties file. `AiClientFactory` creates an `OpenAiChatModel` from the current saved endpoint/key/model when an agent call is made, so AI changes take effect without restarting KAI.

Existing `ICA_CODEX_KEY` and `ICA_BASE_URL_V1` environment variables are still accepted as migration fallbacks until values are saved through the UI.

## Run from source

Requirements:

- JDK 25
- internet or your organisation's Maven mirror for the first dependency download

Windows:

```bat
mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

KAI starts on the saved local port (8080 by default) and attempts to open the browser automatically. First launch goes to:

```text
http://localhost:8080/setup
```

There is no `kai.properties` setup step.

## Package as Windows/macOS executables

The existing Go launcher remains the distribution mechanism, but it no longer passes or ships `kai.properties`.

1. Build the Spring Boot jar:

```bash
./mvnw package
```

2. Prepare `packaging/build/`:

```text
packaging/build/
├── kai-0.0.1-SNAPSHOT.jar
├── winjre/        # Java 25 runtime, bin/java.exe
├── macjre/        # Java 25 runtime, bin/java
└── README.md
```

3. On macOS with Go installed:

```bash
packaging/build.sh packaging/build
```

Output:

```text
packaging/dist/
├── windows/kai.exe
├── macos/kai
├── kai-windows.zip
└── kai-macos.zip
```

If you create a trimmed runtime with `jlink`, include `java.desktop` because KAI uses the desktop API to open the browser automatically.

## Security note

The API key is no longer stored beside the executable or inside the source tree. In this V1 it is stored in the current user's private KAI application-data settings file and masked in the UI. For production enterprise rollout, the next hardening step should move the secret to Windows Credential Manager / macOS Keychain or an organisation-managed secret provider.

## Main new classes

```text
config/UserConfigService.java     UI-owned persistent settings
ai/AiClientFactory.java           dynamic Spring AI client/model creation
ai/AiConnectionService.java       live Test connection action
repository/ConnectorCatalog.java  connector metadata for the UI
ui/HomeController.java            first-run routing
ui/SetupController.java           setup wizard + test APIs
ui/SourcesController.java         knowledge-source management
ui/SettingsController.java        AI / processing settings
ui/HistoryController.java         final report history
chat/ChatController.java          Impact Analysis workflow
```
