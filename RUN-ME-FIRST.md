# Run KAI UI Full-Stack V1

## Windows source run

1. Install JDK 25 and confirm:

```bat
java -version
```

2. Open Command Prompt inside this project folder.

3. Run:

```bat
mvnw.cmd spring-boot:run
```

4. KAI should open your browser automatically. If it does not, open:

```text
http://localhost:8080
```

5. First launch opens the Setup wizard. Enter the AI endpoint, API key and model, then connect a local/synced knowledge folder and choose the backup directory.

No `kai.properties` editing is required.

## Build the jar

```bat
mvnw.cmd package
```

The jar will be under `target/`.

## Package the desktop executables

The existing Go launcher is still used. See `README.md` and `packaging/README.md` for the Windows/macOS packaging layout.

## Current connector status

Local/synced folders work with the KAI adapter layer. SharePoint, OneDrive, Amazon S3, Google Drive and Custom are visible as connector templates but their provider-specific API adapters are not implemented yet.
