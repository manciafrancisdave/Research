# Debug APKs

Unminified, debuggable builds. Use these while testing on a phone — the
stack traces are readable and `adb logcat` is useful.

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:assembleDebug
```

The artifact lands in `app/build/outputs/apk/debug/app-debug.apk`. Copy it here
named `SIREN-v<version>-debug.apk`.

| File | Version | Size | Built |
|---|---|---|---|
| `SIREN-v2.8.0-debug.apk` | 2.8.0 (versionCode 6) | 25.7 MB | 16 Aug 2026 |

**2.8.0 is the first build where the alert reaches the screen of a locked phone.**
Everything before it could sound the alarm and wake the display, then show the
splash — the alert UI sat behind the sign-in gates, which are all closed during the
cold start a full-screen intent produces. It also raises the alarm stream volume,
which used to leave a muted phone completely silent with no error.

`versionCode` moved 5 → 6, so unlike the previous pair a phone **will** treat this
as an update by version.

**Signed with this machine's debug key, so you must uninstall before installing.**
`debug.keystore` is generated per machine and never committed, so this build cannot
install over 2.7.0 — Android says "App not installed" and does not explain why. From
2.8.0 onward, builds from this machine update in place.

Debug SHA-256, which has to be registered in Firebase for phone sign-up to work on a
debug build:

```
84:45:C3:F4:A6:C4:F4:D8:23:72:FC:84:D6:84:30:BF:52:4B:2B:71:2B:9A:F8:1C:DD:AC:C0:60:22:44:60:11
```

Earlier notes claimed `28:6D:5C:E9:...` was already registered. It was not — the
console's fingerprint list was empty on 16 Aug 2026, which is why phone sign-up has
never sent an SMS. Only phone sign-up depends on this; email sign-in, alerts, Firestore
and the alarm all work regardless. Read any APK's own fingerprint back with
`apksigner verify --print-certs <apk>`; the keystore does not have to be present.

Verified against the built artifact, not assumed:

- **Launcher label reads `SIREN`** — `aapt2 dump badging` reports
  `application-label:'SIREN'`, and `string/app_name` resolves to `SIREN`
- **Alarm audio** — `res/raw/siren_alarm.mp3` present at **139,695 bytes**
- **Compose resources** — 28 `ic_sg_*` pictograms and 5 Inter weights under
  `assets/composeResources/`, which AGP 9's KMP-library plugin does not package on
  its own
- 15 `classes*.dex` — R8 is off, as expected for debug
- **All three alert permissions reached the merged manifest**
  (`USE_FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`). They are declared in `app/`, but the alarm
  service relying on them lives in `:shared`, so "it is in the source" and "it is in
  the APK" are separate claims

**Not run.** No device or emulator was available. These are static checks: they
prove it compiled, packaged and carries its resources. They do not prove it
launches or survives an alert — and for 2.8.0 specifically, they prove nothing at
all about whether the alert now reaches a locked screen. That needs step 2 below.

## Why keep them separate from release

R8 is off here, so a debug APK is roughly **25 MB against the release build's
4.5 MB** and behaves differently in the two places this app most needs
watching:

- **Resource shrinking and path shortening do not happen.** `res/raw/siren_alarm.mp3`
  keeps its name, so "is the alarm audio present?" is easy to answer here and
  misleading in release. A silent alarm has shipped before precisely this way.
  In release the same file is renamed — `res/dQ.mp3` as of 2.8.0 — so listing paths
  finds **no `res/raw` entries** and looks like the bug even when nothing is wrong.
  Resolve it through the resource table instead:
  `aapt2 dump resources <apk> | Select-String -Context 0,1 "raw/siren_alarm"`.
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
3. **All four actions are on the alert itself** — Yes I'm safe, I need help,
   the silence toggle, Dismiss. None of them should need a second screen
4. **The three fallback layers, tested in order.** Each one only matters when the
   one above it is refused, so testing with everything granted proves nothing:
   - Both grants denied → launch the app, confirm it *asks*; trigger Red,
     confirm the notification's I'm safe / I need help / Stop alarm still work
   - Full-screen alerts denied, pop-up windows allowed → lock the phone, trigger
     Red, confirm the alert comes up anyway (this is `raiseAlertScreen`)
   - Full-screen alerts allowed → confirm the alert appears **once**, not twice
5. Trigger Yellow on a locked phone — it now takes the screen too, which it did
   not before
6. **Turn the alarm volume to zero, then trigger Red.** New in 2.8.0: the service
   lifts the alarm stream to a floor and restores it afterwards. Before this the
   phone was silent with no error of any kind. Confirm the volume goes back down
   once the alert is answered
7. **Airplane mode, then trigger Red from another account.** The alert now renders
   from the push payload rather than a Firestore read, so it must appear even with
   no connection. This is the path that used to leave the alarm sounding behind a
   spinner
8. **Force-stop the app, then trigger Red on a locked phone.** This is the true
   cold start — the case every earlier build got wrong
9. Respond on one account and verify it appears on a teacher or parent account
10. Airplane mode → respond → reconnect → confirm the response syncs
11. Open the Safety Guide — it is the canary for Compose-resource packaging
