# KAI

## Start KAI

### Windows
Double-click `kai.exe`.

### macOS
Double-click `kai`. If Gatekeeper blocks the unsigned internal build, use System Settings → Privacy & Security → Open Anyway.

KAI starts a local web application and normally opens your browser automatically. If it does not, use the URL printed in the console window, normally:

```text
http://localhost:8080
```

Keep the console window open while using KAI. Closing it stops KAI.

## First launch

The first launch opens KAI Setup. Configure the AI connection, choose a knowledge source and choose a backup location in the browser. You do not edit a `kai.properties` file.

KAI stores its private configuration under your own operating-system profile. Settings can be changed later from the KAI Settings screen.

## Knowledge sources

This build supports local folders and synced folders end to end. SharePoint, OneDrive, Amazon S3, Google Drive and Custom connector shells are visible in the UI for the adapter architecture, but require their provider adapter implementation before they can be enabled for scanning.

## Safety

KAI never writes a proposed change immediately. You review proposals first. On Finalize it checks for stale source content, saves an HTML report, backs up exact originals, writes selected changes, and rolls all selected files back if a write fails.
