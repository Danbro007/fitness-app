---
name: i fitness Exercise Media Rights Gate
version: 1.0.0
last_updated: 2026-07-11
---

# Exercise Media Rights Gate

Gym Visual GIFs and thumbnails are not covered by the upstream exercise-data MIT license. No direct Gym Visual purchase invoice or written permission has been recorded for this project.

For the user's explicitly personal, local, non-commercial build only, the two currently displayed 180p GIF files have been verified against the ExerciseDB V1 Free Dataset manifest and its official non-commercial usage terms. The scoped record is retained at `docs/compliance/exercisedb-personal-noncommercial-media-record.md`. It does not authorize public distribution, commercial use, resale, redistribution, or a future change of purpose.

Accordingly, the default Android build excludes `app/src/main/assets/exercise-media/gifs/**` and the UI does not display those files. The public repository also excludes GIF binaries and UI screenshots containing the media.

To make a licensed internal build, both properties are required:

```text
./gradlew :app:assembleRelease \
  -PincludeLicensedExerciseMedia=true \
  -PexerciseMediaLicenseReference=<invoice-or-written-permission-reference>
```

The reference is an accountability gate, not automatic rights verification. Before enabling it, retain a rights-holder grant that covers the intended territory, platforms, term, commercial use, redistribution, offline packaging, and required attribution. Do not publish an APK with these media files until that record is approved.
