# Built APKs

| File | Version | Signing | Size | Built |
|---|---|---|---|---|
| `SIREN-v2.6.0-release.apk` | 2.6.0 (versionCode 4) | `siren-release.jks`, alias `siren` | 4.53 MB | 5 Aug 2026 |

`package com.research.siren` · `compileSdk 37` · `targetSdk 35` · `minSdk 24`

Signing certificate SHA-256:

```
16:EC:CC:66:64:B8:E7:4A:38:B8:75:37:5F:B1:C6:AE:00:D7:73:F4:85:AF:2E:03:32:43:64:C9:10:02:BC:3E
```

A debug APK was published here briefly and has been removed — the release build
supersedes it. Rebuild one with `:app:assembleDebug` if you need a debuggable
copy.

## Installing

Copy it to the phone and open it. Android will ask you to allow installs from
this source; that is expected for an APK that did not come from Play.

**Uninstall any older S.I.R.E.N. build first.** This is signed with a key
generated on 5 Aug 2026, and Android refuses to install over an app whose
signing key differs. The error it gives — "App not installed" — does not
explain why.

## Verified before publishing

Checked against the built artifact, not assumed:

- **Signature** — `apksigner verify` passes, APK Signature Scheme v2, and the
  certificate digest matches the keystore fingerprint above
- **Alarm audio** — present at **139,695 bytes**. Release builds shorten
  resource paths, so `res/raw/siren_alarm.mp3` ships as `res/dQ.mp3`; a
  filename check answers the wrong question here. `isShrinkResources` is
  deliberately `false` for exactly this reason — see `app/build.gradle.kts`.
- **Compose resources** — 28 `ic_sg_*` pictograms and 5 Inter weights, which
  AGP 9's KMP-library plugin does not package on its own
- **Strings, post-R8** — the intensity-level readouts are present; the removed
  dark-mode toggle, the old peak-acceleration readout, and the four false
  "SMS fallback" claims are all absent
- **`lintVitalRelease`** — passes. This is release-only and had failed
  historically on the `androidx.fragment` transitive pull from Firebase; the
  pin to 1.8.9 holds.

R8 minification is on: 15 dex files collapse to 1, and the APK drops from
25.77 MB (debug) to 4.53 MB.

## Not verified

**Nothing here has been run.** No device or emulator was available. These are
static checks — they prove the code compiled, the resources packaged and the
signature is valid. They do not prove the app launches, renders correctly, or
survives a real alert. Install it on a phone and walk Demo Mode through all
three tiers; the Safety Guide screen is the canary for resource packaging.

## Rebuilding

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME="C:\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:assembleRelease
```

A fresh clone needs three files restored first, all gitignored on purpose:
`app/google-services.json`, `keystore.properties` and `siren-release.jks`.
Without the latter two the release build still succeeds but comes out unsigned.
