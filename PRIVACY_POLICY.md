---
name: i fitness Privacy Policy
version: 1.0.0
last_updated: 2026-07-11
---

# Privacy Policy

This policy applies to the i fitness Android application distributed from this repository. It describes the current local-first MVP; it is not a substitute for jurisdiction-specific legal review before an app-store release.

## Data stored on the device

The app stores workout plans, workout logs, food records, venue and equipment choices, local backup files, and AI drafts in its local SQLite database. API keys are stored using Android Keystore-backed encryption rather than SQLite. The app has no account system, analytics SDK, advertising SDK, default cloud sync, or server-side fitness-data store.

## Network use and AI providers

The app requests network access only when the user configures and invokes an AI provider. The configured provider receives the prompt text and, if the user selects one, the food image needed for that request. The provider's own terms and privacy policy apply to that processing. Do not submit sensitive health information unless you accept that provider's terms.

## Sharing and retention

The app does not sell, rent, or share local workout or food data with the project maintainer. Data remains on the device until the user edits it, clears app storage, uninstalls the app, or exports/deletes a backup. A user-selected AI provider may retain submitted content under its own policy.

## User controls

Users can avoid AI requests, remove a configured API key, delete records, clear Android app storage, uninstall the app, and choose whether to export or import a local JSON backup. Do not export backups to a third party unless you accept that recipient's privacy practices.

## Security and health boundary

Android Keystore-backed encryption is used for API credentials, but no software can guarantee absolute security. Workout, nutrition, and AI output are informational only and are not medical diagnosis, treatment, or emergency guidance.

## Changes and contact

Material changes will be recorded in this repository. For questions or deletion requests related to the distributed source, open an issue at <https://github.com/Danbro007/fitness-app/issues>.
