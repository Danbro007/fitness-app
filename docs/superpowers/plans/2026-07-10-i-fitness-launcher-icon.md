# i fitness Launcher Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder launcher glyph with the user-selected i fitness smart-training logo on Android 8 through Android 16.

**Architecture:** Keep the selected central mark as a dedicated raster foreground image, separated from an Android adaptive-icon background. The application supports Android 8 and later (`minSdk = 26`), so one adaptive-icon resource family covers every supported device; no Compose UI or business state changes.

**Tech Stack:** Android resource linking, Android adaptive icons, PNG launcher assets, Gradle Android application plugin.

## Global Constraints

- Preserve the selected central concept: black `i`, green progress ring, green heart-rate line, and orange progress accent.
- Do not include the `i fitness` wordmark inside the launcher icon.
- Set the launcher label to `i fitness`; do not change training, SQLite, GIF, or AI behavior.
- Provide one `mipmap-anydpi-v26` adaptive-icon resource family, matching the project's Android 8 minimum version.
- Verify the assembled APK declares the new `@mipmap/ic_launcher` application icon.

---

### Task 1: Prepare Launcher Icon Assets

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable-nodpi/ic_launcher_art.png`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

**Interfaces:**
- Consumes: selected central logo from `/Users/shanqijie/.codex/generated_images/019f4562-8f25-7703-9698-66639a8632f4/ig_072b1757f36ea8f8016a4fb9174e6481a1b07512796a94322f.png`.
- Produces: `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`, each usable as an application icon.

- [x] **Step 1: Extract the selected mark without the wordmark**

Crop the central panel to a 512-pixel square containing only the rounded-square logo. Keep the white card, black outline, green segmented progress ring, orange accent, and heartbeat line visible.

```zsh
sips --cropToHeightWidth 512 512 --cropOffset 92 722 \
  /Users/shanqijie/.codex/generated_images/019f4562-8f25-7703-9698-66639a8632f4/ig_072b1757f36ea8f8016a4fb9174e6481a1b07512796a94322f.png \
  --out app/src/main/res/drawable-nodpi/ic_launcher_art.png
```

- [x] **Step 2: Provide a stable adaptive-icon background**

Create `ic_launcher_background.xml` with an opaque white fill so Android's icon mask cannot expose transparent pixels.

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#FBFBFC" />
</shape>
```

- [x] **Step 3: Add the adaptive foreground and icon descriptors**

Use `ic_launcher_foreground.xml` to display `@drawable/ic_launcher_art` inside the adaptive foreground safe zone. Create `ic_launcher.xml` and `ic_launcher_round.xml` in `mipmap-anydpi-v26`, each referencing the background and foreground drawables.

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [x] **Step 4: Verify resource linkage**

Run: `./gradlew :app:processDebugResources`

Expected: `BUILD SUCCESSFUL`; no duplicate or missing-resource errors.

### Task 2: Point the Application Manifest at the New Icon

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:7-15`
- Modify: `app/src/main/res/values/strings.xml:2`
- Test: assembled APK manifest inspection

**Interfaces:**
- Consumes: `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` from Task 1.
- Produces: Android application metadata that uses the new icon for regular and round launchers.

- [x] **Step 1: Update application icon references**

Replace the current drawable references and update the launcher label resource while preserving all other application attributes.

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

```xml
<string name="app_name">i fitness</string>
```

- [x] **Step 2: Assemble the debug APK**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [x] **Step 3: Verify installed-launcher metadata**

Run: `aapt dump badging app/build/outputs/apk/debug/app-debug.apk | rg "application-icon|application-label"`

Expected: output includes `application-label:'i fitness'` and icon entries resolved from the `mipmap/ic_launcher` family.

### Task 3: Visual Device Validation

**Files:**
- No source changes.
- Evidence: `.scratch/run-evidence/i-fitness-launcher-icon.png`

**Interfaces:**
- Consumes: debug APK from Task 2.
- Produces: screenshot evidence that the launcher mask has not clipped the visual mark or exposed the wordmark.

- [x] **Step 1: Install the APK on the existing emulator**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`.

- [x] **Step 2: Inspect the launcher icon at native size**

Open the app drawer, confirm the dark `i`, green progress ring, orange accent, and heartbeat line are visible, and capture one screenshot.

- [x] **Step 3: Run the regression test suite**

Run: `./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`; no existing unit-test regressions.
