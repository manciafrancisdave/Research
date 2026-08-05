# Built APKs

| File | Version | Signing | Built |
|---|---|---|---|
| `SIREN-v2.6.0-debug.apk` | 2.6.0 (versionCode 4) | **debug key** | 5 Aug 2026 |

`package com.research.siren` · `compileSdk 37` · `targetSdk 35` · `minSdk 24`

## Installing

Copy it to the phone and open it. Android will ask you to allow installs from
this source — that is expected for an APK that did not come from Play.

**Uninstall any older S.I.R.E.N. build first.** This is signed with the debug
key, and Android refuses to install over an app whose signing key differs. The
error it gives — "App not installed" — does not explain why.

## This is a debug build

It is fine for testing and for the defence demonstration, but it is not a
release artifact:

- signed with the auto-generated debug key, not `siren-release.jks`
- debuggable, and not run through R8 shrinking or obfuscation
- larger than a release build would be

A signed release APK needs `keystore.properties` and `siren-release.jks` in the
repository root. Neither is committed — see `.gitignore` — so `assembleRelease`
cannot be reproduced from a fresh clone without restoring them.

## Verified contents

`CLAUDE.md` warns that a successful build proves nothing about whether the
Compose resources were packaged, because AGP 9's KMP-library plugin does not
ship them and `:app` works around it with a copy task. This APK was opened and
checked:

- 28 `ic_sg_*` safety-guide pictograms — present
- 5 `inter_*` font weights — present
- `res/raw/siren_alarm.mp3` (139,695 bytes) — present

The on-device canary is still the Safety Guide screen. If those pictograms
render on a real phone, the packaging workaround is genuinely working.

## Rebuilding

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME="C:\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:assembleDebug
```

A fresh clone also needs `app/google-services.json` restored from the Firebase
console before it will build at all.
