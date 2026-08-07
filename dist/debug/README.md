# Debug APKs

Unminified, debuggable builds. Use these while testing on a phone — the
stack traces are readable and `adb logcat` is useful.

```powershell
$env:JAVA_HOME="C:\siren_toolchain\jdk-17.0.20+8"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:assembleDebug
```

The artifact lands in `app/build/outputs/apk/debug/app-debug.apk`. Copy it here
named `SIREN-v<version>-debug.apk`.

| File | Version | Size | Built |
|---|---|---|---|
| `SIREN-v2.7.0-debug.apk` | 2.7.0 (versionCode 5) | 25.7 MB | 8 Aug 2026 |

Verified against the built artifact, not assumed:

- **Alarm audio** — `res/raw/siren_alarm.mp3` present at **139,695 bytes**
- **Compose resources** — 28 `ic_sg_*` pictograms and 5 Inter weights under
  `assets/composeResources/com.siren.mobile.resources/`, which AGP 9's
  KMP-library plugin does not package on its own
- 15 `classes*.dex` — R8 is off, as expected for debug

**Not run.** No device or emulator was available. These are static checks: they
prove it compiled, packaged and carries its resources. They do not prove it
launches or survives an alert.

## Why keep them separate from release

R8 is off here, so a debug APK is roughly **25 MB against the release build's
4.5 MB** and behaves differently in the two places this app most needs
watching:

- **Resource shrinking and path shortening do not happen.** `res/raw/siren_alarm.mp3`
  keeps its name, so "is the alarm audio present?" is easy to answer here and
  misleading in release. A silent alarm has shipped before precisely this way.
- **`lintVitalRelease` never runs.** Failures that only appear in release —
  historically the `androidx.fragment` version Firebase pulls in — are invisible
  from a debug build.

A debug build passing therefore says nothing about the release build. Test the
one you intend to hand out.

## Debug builds share the release application id

`applicationIdSuffix` is empty, so debug and release are both
`com.research.siren` and **cannot be installed side by side** — the second one
replaces the first, and only if the signing keys match. They do not, so
uninstall before switching between them.

## Worth walking through on a debug build

1. Demo Mode at all three levels — colour, vibration, full-screen behaviour
2. Lock the phone, trigger Red, confirm the screen wakes and the alert shows
3. Deny full-screen alerts in Android settings, trigger Red again, confirm the
   notification's I'm safe / I need help / Stop alarm buttons still work
4. Respond on one account and verify it appears on a teacher or parent account
5. Airplane mode → respond → reconnect → confirm the response syncs
6. Open the Safety Guide — it is the canary for Compose-resource packaging
