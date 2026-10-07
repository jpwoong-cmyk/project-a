# Build status — KAI UI Full-Stack V1

## Implemented

- First-run browser setup; no end-user `kai.properties` editing.
- Persistent per-user application settings.
- Runtime-editable OpenAI-compatible endpoint, API key and model.
- Live AI connection test endpoint.
- Workspace navigation: Impact Analysis, Knowledge Sources, History, Settings.
- Generic knowledge-source UI and connector catalogue.
- Local-folder adapter retained as the working end-to-end adapter.
- Unimplemented enterprise connectors are represented honestly as disabled connector drafts.
- Dynamic source list used by the existing Orchestrator.
- Existing Scanner → Editor → Reviewer workflow preserved.
- Existing human review, stale check, report, backup, write and rollback flow preserved.
- Go packaging updated so the executable no longer requires or ships `kai.properties`.
- Saved port is read before Spring starts; changing the port in Settings applies on the next launch.

## Validation performed here

- Java source was passed through `javac --release 21 -proc:none`; after fixing syntax issues, remaining errors are expected missing external Spring/Spring AI/Tika/Thymeleaf dependencies because Maven cannot be downloaded in this environment.
- The runtime construction pattern for Spring AI 2.0.1 (`OpenAiChatModel.builder()` + `OpenAiChatOptions` + `ChatClient.builder`) was checked against the official Spring AI 2.0.1 documentation.
- Packaging shell and Go launcher were updated consistently with the no-properties-file runtime.

## Not fully executed here

This environment has JDK 21, while the supplied project enforces JDK 25, and it cannot fetch Maven from Maven Central. Therefore a complete `./mvnw package` and live browser run could not be performed here.

Run on a normal KAI build machine with JDK 25:

```bash
./mvnw package
```

Then launch the jar or use `packaging/build.sh` to create the desktop packages.
