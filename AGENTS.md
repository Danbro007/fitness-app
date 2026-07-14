---
name: i fitness Agent Guide
description: Repository instructions for agents working on the local-first Android fitness application.
version: 1.0.0
last_updated: 2026-07-13
maintained_by: shanqijie
---

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **fitness-app** (2690 symbols, 6840 relationships, 236 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/fitness-app/context` | Codebase overview, check index freshness |
| `gitnexus://repo/fitness-app/clusters` | All functional areas |
| `gitnexus://repo/fitness-app/processes` | All execution flows |
| `gitnexus://repo/fitness-app/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

# i fitness Repository Rules

## Product Boundary

- This is a single-project, local-first Android application. Core training, food, profile, plan, and backup flows must work without an account, cloud sync, or an AI provider.
- SQLite is the source of truth for user-owned data. AI output is advisory and must remain a draft until the user explicitly confirms it.
- API keys belong in `AiCredentialStore` (Android Keystore + AES/GCM), never SQLite, logs, fixtures, screenshots, or documentation.
- Do not introduce a server dependency or remote account model without an explicit product decision.

## Source Map

| Area | Source paths | Responsibility |
| --- | --- | --- |
| Android entry | `app/src/main/java/com/shanqijie/fitnessapp/MainActivity.kt` | Edge-to-edge window and Compose root |
| Persistence | `app/src/main/java/com/shanqijie/fitnessapp/data/` | SQLite schema, store, repository, backups, credentials |
| Domain | `app/src/main/java/com/shanqijie/fitnessapp/domain/` | State rules, summaries, exercise presentation translation |
| UI/navigation | `app/src/main/java/com/shanqijie/fitnessapp/ui/` | Routes, screens, reusable components, theme |
| JVM tests | `app/src/test/` | Pure rules, serialization, navigation, AI request construction |
| Device tests | `app/src/androidTest/` | SQLite migrations, repository integration, Compose flows and visual evidence |
| Product/design baseline | `docs/superpowers/specs/2026-07-11-futuristic-neumorphism-ui-development-requirements.md` | Current native UI and behavior requirements |
| Visual source of truth | `.scratch/futuristic-neumorphism-prototype/i-fitness-未来主义新拟态交互原型.html` | Approved 390 x 844 visual layout |

## Implementation Rules

- Preserve the five primary tabs and the route/back behavior defined in `FitnessNavigation.kt`.
- Keep blocking database, file, bitmap, network, manifest parsing, and full-library indexing work off the main thread.
- For the 1,324-action library, retain lazy list/grid rendering and lazy translation. Do not eagerly decode or animate every GIF in a grid.
- Translate only visible presentation strings through `ExerciseChineseNameTranslator`; do not rewrite raw exercise metadata or IDs.
- Database changes require a version bump, additive/explicit migration logic, and an upgrade test from the previous schema.
- Backup changes must remain backward-readable for every version accepted by `validateBackupPayload` and must round-trip user data.
- Validate image dimensions safely before full bitmap allocation; compressed file-size checks alone do not prevent OOM.
- Preserve user changes in a dirty worktree and keep edits scoped to the requested task.

## Media Builds

The default build excludes third-party exercise GIF binaries. A media-enabled personal/non-commercial build requires both explicit Gradle properties:

```bash
./gradlew :app:assembleDebug \
  -PincludeLicensedExerciseMedia=true \
  -PexerciseMediaLicenseReference=docs/compliance/exercisedb-personal-noncommercial-media-record.md
```

Do not weaken or silently bypass the build gate. Keep source, attribution, and hash records synchronized with `MEDIA_RIGHTS.md`, `THIRD_PARTY_NOTICES.md`, and `docs/compliance/`.

## Verification

Use the smallest focused command while iterating, then run the proportional final gate:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

For device-sensitive work, also install and exercise the real APK on the Pixel 8 Pro emulator (`1170x2532`, 480 dpi = `390x844dp`). Validate the real route, logs, SQLite state, and screenshot; a standalone Compose test or HTML prototype is not sufficient proof of native completion.

Before committing:

1. Run `git diff --check`.
2. Run the focused JVM/device tests for affected flows.
3. Run `gitnexus_detect_changes({scope: "all"})` and review unexpected processes.
4. Do not commit `.scratch/`, `.gitnexus/`, build outputs, emulator databases, screenshots, secrets, or downloaded GIFs.
