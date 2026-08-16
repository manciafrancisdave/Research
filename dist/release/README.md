# Release APKs

Signed, minified builds. This is what gets installed on a phone for a drill,
a demonstration or the defence.

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:assembleRelease
```

The artifact lands in `app/build/outputs/apk/release/app-release.apk`. Copy it
here named `SIREN-v<version>-release.apk` so the version is readable without
unpacking it.

| File | Version | Signing | Size | Built |
|---|---|---|---|---|
| `SIREN-v2.9.1-release.apk` | 2.9.1 (versionCode 8) | `siren-release.jks`, alias `siren` | 4.60 MB | 17 Aug 2026 |

2.9.0 added the **emergency SMS**: a student tapping "I need help" texts their approved
guardians automatically, before the Firestore write, so it works with no connection. A
mobile number is required at sign-up, because that feature is worthless without one.

**2.9.1 is the version to use. 2.9.0 should not be installed.** Three adversarial review
passes over 2.8.0 and 2.9.0 found thirteen defects that a passing build could not have
caught, every one of them a silent failure on the emergency path:

- `detachAll()` wiped the push-painted alert on every cold start — the alert appeared over
  the keyguard and then vanished, with the alarm still looping against a dark screen
- `sendTextMessage` was called with a null `sentIntent`, so the app counted binder calls, not
  sends, and reported "2 guardians texted" when nothing had left the phone
- SEND_SMS could only ever be *checked*, never *requested*, on the notification-answer path
  that stock Android 14 makes the default — zero texts, reported as "you declined"
- `sendHelpSms` returned silently when the profile snapshot had not arrived, so the cold-start
  lock-screen tap sent nothing and said nothing
- `parentPhone` was never back-filled, so every pre-existing link texted nobody
- a Back press closed the alert and left the alarm looping behind a blank keyguard
- Demo Mode texted guardians an unmarked alert indistinguishable from a real earthquake

Same signing key as 2.8.0, so this upgrades in place.

**Testing it costs real money and reaches real people.** Use a second SIM you control.
Demo Mode sends for real — the dispatch does not distinguish a simulated event from a
sensor one.

**The signing key changed again on 16 Aug 2026.** The key that signed 2.6.0 was
not recoverable — it existed only on the machine that built it, and that machine
was not available. A new 4096-bit RSA key was generated, valid 30 years. This is
the second key loss in this project's history; **back up `siren-release.jks` and
its password off this machine now**, because a third loss has the same cost:
every installed copy has to be uninstalled by hand before it can be updated.

Signing certificate SHA-256 for 2.8.0:

```
EF:2E:14:D5:A2:C4:4D:19:72:58:CD:A7:8D:50:18:57:63:C7:ED:60:FD:77:6A:5E:EE:CC:CC:8B:1F:C8:8D:FC
```

For reference, 2.6.0 and earlier were signed with a key that is now gone:

```
16:EC:CC:66:64:B8:E7:4A:38:B8:75:37:5F:B1:C6:AE:00:D7:73:F4:85:AF:2E:03:32:43:64:C9:10:02:BC:3E
```

The 2.8.0 fingerprint is what has to be registered in the Firebase console before
**phone sign-up** can send an SMS. As of 16 Aug 2026 the console's SHA certificate
fingerprints list was **empty** — no fingerprint had ever been registered, which is
why phone sign-up has never worked. See CLAUDE.md → Authentication.

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

**Uninstall any older SIREN build first.** The current key was generated on
16 Aug 2026 and Android refuses to install over an app signed with a different
one. The error it gives — "App not installed" — does not explain why. This
applies to *every* build that came before 2.8.0, debug and release alike.

From 2.8.0 onward, updates install over the top normally — provided
`siren-release.jks` survives. That is now the single point of failure for
in-place updates.
