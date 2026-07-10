---
name: i fitness Third-Party Notices
description: Attribution, license boundaries, and distribution requirements for third-party data, media, and libraries used by i fitness.
version: 1.0.0
last_updated: 2026-07-11
maintained_by: shanqijie
---

# Third-Party Notices

This file records the known third-party material boundaries for this repository. It is not legal advice.

## Exercise dataset

The exercise names, categories, body parts, equipment fields, targets, muscle groups, dataset structure, and instruction text originate from [`hasaneyldrm/exercises-dataset`](https://github.com/hasaneyldrm/exercises-dataset).

The upstream code, tooling, dataset structure, and instruction text are provided under the following MIT license:

```text
MIT License

Copyright (c) 2026 Hasan Emir Yıldırım

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation and data files (the "Software"),
to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Exercise media exception

The MIT license above does **not** cover exercise images or animation GIFs. The upstream project identifies that media as:

> © Gym visual — https://gymvisual.com/

This repository does not grant a license to that media. The GIF binaries are excluded from Git. Anyone who downloads or uses those assets must review [Gym Visual's terms](https://gymvisual.com/content/3-terms-and-conditions-of-use), obtain any required license directly from the rights holder, retain the required attribution, and comply with the permitted resolution and use scope.

The helper downloader requires the operator to explicitly state that they already have the necessary media rights. That flag is a safety acknowledgement, not a license grant.

## Android and Kotlin libraries

The runtime uses AndroidX, Jetpack Compose, Kotlin/Kotlinx, and Coil libraries. These projects are generally distributed under Apache License 2.0; their own copyright notices and license files remain authoritative. JUnit 4 is used only for tests and is distributed under the Eclipse Public License 1.0.

Primary references:

- [Android Open Source Project licenses](https://source.android.com/docs/setup/about/licenses)
- [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)
- [Coil license](https://github.com/coil-kt/coil/blob/main/LICENSE.txt)
- [JUnit 4 license](https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt)

## Product and content boundaries

- `i fitness` is a working product name, not a registered trademark claim. Perform a trademark clearance before a public or commercial release.
- The launcher artwork must remain project-owned or properly licensed; verify its source record before public distribution.
- AI-generated workout and nutrition output is assistive information, not medical diagnosis or treatment.
- When a user invokes a configured AI provider, the submitted prompt and any selected food image may be transmitted to that provider under its terms and privacy policy.
- Before making the repository or APK public, complete a privacy policy, a dependency-notice review, media licensing, and jurisdiction-specific legal review.
