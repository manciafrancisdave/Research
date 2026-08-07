# Release APKs

Signed, minified builds. This is what gets installed on a phone for a drill,
a demonstration or the defence.

```powershell
$env:JAVA_HOME="C:\siren_toolchain\jdk-17.0.20+8"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:assembleRelease
```

The artifact lands in `app/build/outputs/apk/release/app-release.apk`. Copy it
here named `SIREN-v<version>-release.apk` so the version is readable without
unpacking it.

| File | Version | Signing | Size | Built |
|---|---|---|---|---|
| `SIREN-v2.6.0-release.apk` | 2.6.0 (versionCode 4) | `siren-release.jks`, alias `siren` | 4.53 MB | 5 Aug 2026 |

Signing certificate SHA-256 for 2.6.0:

```
16:EC:CC:66:64:B8:E7:4A:38:B8:75:37:5F:B1:C6:AE:00:D7:73:F4:85:AF:2E:03:32:43:64:C9:10:02:BC:3E
```

That fingerprint is also what has to be registered in the Firebase console
before **phone sign-up** can send an SMS — see CLAUDE.md → Authentication.

## Before publishing one here

Check the built artifact, not the build log. A passing build proves none of this:

- **Signature** — `apksigner verify` passes and the certificate digest matches
  the keystore fingerprint
- **Alarm audio** — release builds shorten resource paths, so
  `res/raw/siren_alarm.mp3` ships as something like `res/dQ.mp3`. Check by
  **byte size** (~139,695 bytes), never by filename. `isShrinkResources` is
  deliberately `false` because shrinking once deleted this file silently and
  shipped a completely mute alarm.
- **Compose resources** — 28 `ic_sg_*` pictograms and 5 Inter weights. AGP 9's
  KMP-library plugin does not package these on its own; `:app` copies them in.
- **`lintVitalRelease`** — release-only, and has failed historically on the
  `androidx.fragment` version Firebase pulls in transitively.

## Installing

Copy it to the phone and open it. Android will ask you to allow installs from
this source; that is expected for an APK that did not come from Play.

**Uninstall any older S.I.R.E.N. build first.** The current key was generated on
5 Aug 2026 and Android refuses to install over an app signed with a different
one. The error it gives — "App not installed" — does not explain why.
