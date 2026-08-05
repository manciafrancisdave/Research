# CLAUDE.md — S.I.R.E.N. Mobile

Mobile companion app for **S.I.R.E.N. (Seismic Integrated Response and Emergency
Notification)** — an IoT earthquake detection system built around an ESP32 +
ADXL335 accelerometer (Practical Research 2, City of Bogo Senior High School).

This repo is the **app**, not the firmware. It receives alerts when the hardware
detects a seismic event, lets users confirm "I'm Safe" / "I Need Help", shows a live
safety dashboard for teachers and parents, and keeps a history log for the paper's
evaluation phase.

---

## Current state

| Module | Status |
|---|---|
| `:shared` | ✅ Compose Multiplatform library. All UI, models and the data layer. Compiles for Android **and** iOS. |
| `:app` | ✅ Thin Android host (3 files). Debug + signed release APKs build. |
| `iosApp/` | ⚠️ Swift sources + Podfile written, **never compiled**. Needs a Mac — see below. |

```powershell
$env:JAVA_HOME="C:\siren_toolchain\jdk-17.0.20+8"
$env:ANDROID_HOME="C:\Users\Administrator\AppData\Local\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd C:\Users\Administrator\Desktop\Project

.\gradlew.bat :app:assembleDebug                    # Android debug APK
.\gradlew.bat :app:assembleRelease                  # signed release APK
.\gradlew.bat :shared:compileCommonMainKotlinMetadata   # type-checks shared code for BOTH platforms (~10s)
```

That last command is the fastest way to verify shared code still compiles for iOS
without a Mac. Use it constantly.

---

## Architecture

```
shared/src/commonMain/     everything cross-platform
  ui/App.kt                auth gate + back stack + role-based bottom nav
  ui/screens/              16 screens
  ui/theme/                colours, Inter typography, spacing
  ui/components/           shared widgets, Haptics facade
  data/SirenRepository     auth, Firestore, settings  (GitLive Firebase KMP)
  model/Models.kt          enums + data classes
  platform/Platform.kt     PlatformServices interface + installer
  util/                    DateFmt (expect), Format helpers
  composeResources/        28 ic_sg_* pictograms, 5 Inter weights, vectors

shared/src/androidMain/    AndroidPlatformServices, DateFmt actual, BackHandler actual
shared/src/iosMain/        IosPlatformServices, DateFmt actual, MainViewController
app/                       SirenApp, MainActivity, SirenMessagingService + launcher res
iosApp/                    Swift host (unbuilt)
```

Platform differences go through **`PlatformServices`** (an interface, installed at
start-up), not expect/actual — vibration, notifications, dial/SMS, settings storage,
FCM topic, wall-clock. Only `DateFmt` and `PlatformBackHandler` use expect/actual,
because they need per-platform *compile-time* bindings.

`App()` is the single entry point: `MainActivity.setContent { App() }` on Android,
`MainViewController()` on iOS.

## Tech stack

- **Kotlin 2.4.10 · Compose Multiplatform 1.11.1 · AGP 9.3.1 · Gradle 9.6.1 · JDK 17**
- **compileSdk 37 · minSdk 24 · targetSdk 35 · iOS 15+**
- **applicationId** `com.research.siren` (must match Firebase) · **namespace** `com.siren.mobile`
- **Firebase** via **GitLive KMP SDK 2.5.0** (auth + firestore); Cloud Messaging stays
  platform-specific because GitLive does not wrap it
- **State** — `StateFlow` on a single `SirenRepository` object. No DI, no ViewModels;
  screens are pure composables fed from `App()`
- **Offline** — Firestore's on-device cache queues writes and replays on reconnect

---

## Hard-won constraints — do not rediscover these

Each of these cost a debugging cycle:

- **AGP 9 rejects `org.jetbrains.kotlin.android`.** Kotlin is built in. The Compose
  *compiler* plugin is still applied separately.
- **AGP 9 rejects `com.android.application` + `org.jetbrains.kotlin.multiplatform`.**
  That is why shared code is a library (`com.android.kotlin.multiplatform.library`)
  with `:app` as a separate host, instead of one `composeApp` module.
- **Declare KMP/Compose plugin versions only in the root `build.gradle.kts`** with
  `apply false`; submodules apply them by bare id. Repeating a version in a submodule
  fails with *"already on the classpath with an unknown version."*
- **AGP 9's KMP-library plugin does NOT package Compose resources.** `shared.aar`
  ships with **zero** asset entries and only iOS gets assembled resources. `:app`
  works around this with `CopyComposeResourcesTask` + `variant.sources.assets
  .addGeneratedSourceDirectory`, which puts them at
  `assets/composeResources/siren.shared.generated.resources/`. **Without that the app
  compiles but every icon and font fails at runtime.** Verify after changing
  resources:
  ```powershell
  # expect 28 icons + 5 fonts
  ```
- **AGP 9 forbids `Provider`s in the SourceSet API** (`assets.srcDir(provider)`), and
  `addGeneratedSourceDirectory` requires a task exposing a `DirectoryProperty` —
  a plain `Copy` task will not do.
- **`material-icons-extended` was discontinued for Multiplatform after 1.7.3.** The
  app uses ~40 extended icons, so 1.7.3 is pinned deliberately against CMP 1.11.1.
  It resolves and type-checks on both platforms.
- **`iosX64` is omitted** (Intel-Mac simulator) — some dependencies no longer publish
  that variant. Apple Silicon uses `iosSimulatorArm64`.
- **CMP has no `androidx.compose.ui.backhandler`** in 1.11.1 — hence
  `PlatformBackHandler` expect/actual.
- **CMP's `Font()` is `@Composable`**, so typography cannot be a top-level `val` —
  hence `interFamily()` / `sirenTypography()`.
- **`androidx.fragment` must be pinned to 1.8.9.** Firebase drags in 1.1.0
  transitively and `lintVitalRelease` then fails `registerForActivityResult`. This
  only breaks **release** builds.
- **Auto-mirrored icons need their own import** —
  `androidx.compose.material.icons.automirrored.filled.ArrowBack`.
- **No `String.format`/`SimpleDateFormat` in common code.** Use `Double.toFixed/asG/
  asGSpaced` and `DateFmt`.
- **Resource shrinking silently deleted the alarm audio from the release build.**
  `isShrinkResources = true` could not see `R.raw.siren_alarm` as reachable, because the
  only reference is passed into `AndroidPlatformServices` and stashed in a static on the
  alarm service. Debug played fine; release shipped with **zero** `res/raw` entries and
  a completely silent alarm. `app/src/main/res/raw/keep.xml` pins it. After touching
  shrinking, always confirm the *release* APK still contains `res/raw/siren_alarm.mp3` —
  a passing build proves nothing here.
- **Android 14+ restricts `USE_FULL_SCREEN_INTENT`** to calling/alarm apps. If it is
  denied, the full-screen alert silently never appears — leaving a user with a looping
  alarm and no visible way to stop it. The alarm notification's **I'm safe / I need
  help / Stop alarm** actions are the required fallback; test with it denied.
- **Notification channel settings are immutable after creation.** Changing sound or
  vibration on `siren_alerts` does nothing on existing installs — bump the channel id.
  The alarm service uses its own **silent** channel (`siren_alarm_playback`) precisely
  so the notification does not play a second sound over MediaPlayer.
- **Android Studio's bundled JBR 25 is too new.** Always build with JDK 17.

---

## REMAINING WORK

### 1. Build and test on iOS ❌

Everything under `iosApp/` and `shared/src/iosMain/` was written on Windows and has
**never been compiled**. Kotlin/Native cannot build Apple targets off macOS.
`iosApp/README.md` has the full step-by-step. In short:

1. A Mac with Xcode 15+ and CocoaPods
2. **Paid Apple Developer account ($99/yr)** — free provisioning does *not* grant the
   Push Notifications capability, and without APNs an iOS build installs and runs but
   **never alerts anyone**, which defeats the app's purpose
3. Register an iOS app in Firebase (`com.research.siren`), add
   `GoogleService-Info.plist`, upload an APNs key
4. Create `iosApp.xcodeproj` in Xcode (a `.pbxproj` cannot be hand-authored reliably)
   and add the two Swift files
5. `pod install`, then run

Expect `IosPlatformServices` to need fixes on first compile — it is unverified.

### 2. Wire the FCM topic subscription on iOS ⚠️

`IosPlatformServices.subscribeToAlertsTopic()` is a no-op; the Swift `AppDelegate`
subscribes instead. Consolidate once the FirebaseMessaging pod is linked.

### 3. Request the critical-alerts entitlement ⚠️

Red alerts should bypass silent mode. Apple grants that entitlement only on request;
until then `defaultCriticalSound()` degrades to a normal sound.

### 4. Optional cleanups

- `app/src/main/res/drawable/ic_phone_outline.xml` and `ic_brand_tile.xml` may now be
  unused — check before deleting
- `Platform.services.clearNotifications()` and `vibrateTap()` are implemented but not
  called anywhere yet

---

## Emergency alarm

**The Red alarm stops only when the user taps.** Not on a timer, not when the
notification is swiped, not when the app is backgrounded, not on silent/DND, and not
when audio focus is lost to a call. The three exits are **I'm Safe**, **I Need Help**
and **Stop alarm** — and "Stop alarm" silences the sound while deliberately leaving the
safety confirmation outstanding.

| Level | Sound | Vibration | Service |
|---|---|---|---|
| Green — Intensity I–IV | single chime, respects ringer | one pulse | none |
| Yellow — Intensity V–VI | repeats, stops after 30 s | repeating | foreground |
| **Red — Intensity VII+** | **loops until dismissed, bypasses silent** | **continuous** | foreground |

Android runs it from `SirenAlarmService` (a foreground service) — audio driven from a
composable dies the moment the app is backgrounded, which is exactly when the alarm
matters. It uses `USAGE_ALARM` audio attributes (that is what bypasses the ringer and
DND), a `PARTIAL_WAKE_LOCK`, and `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` whose loss
listener intentionally does nothing above Green.

The tone is `app/src/main/res/raw/siren_alarm.mp3` — the **NDRRMC alert audio supplied
by the project owner**, used at their explicit direction.

Worth knowing if this is ever distributed beyond the school: an identical official tone
can lead people to take this supplementary app for an official PHIVOLCS warning, and
reproducing official emergency signals outside genuine alerts is restricted in a number
of jurisdictions. A distinct synthesised alternative (alternating 960/720 Hz,
square-dominant, whole-cycle segments so it loops seamlessly) can be regenerated at any
time with `tools/make-alarm-tone.ps1` and dropped in as `siren_alarm.wav`.

Because MP3 carries encoder padding, its loop point is not perfectly gapless. A
2-second watchdog in the service restarts playback if it ever stalls, so the alarm
cannot fall silent while an event is unanswered.

Simulated (Demo Mode) events always render a `DEMO — NOT A REAL EVENT` badge on the
full-screen alert. A simulation that is indistinguishable from a real earthquake would
be a serious failure during a defence.

## Authentication

Firebase **Email/Password**. Anonymous sign-in was used in v1.0 and has been removed —
it lost accounts on reinstall, breaking parent links and history.

- Role is chosen **before** the account exists, then written into the user document
- Role is **fixed at sign-up and cannot be changed in the app.** Settings has no
  "Switch role" entry. `SirenRepository.updateRole` still exists for administrative
  correction from outside the UI, but nothing calls it — do not re-expose it without
  asking. (Note this reverses the prototype, which offered role switching.)
- The **ESP32 has its own account** and writes alert documents directly; the app only listens

## Emergency contacts

Three official City of Bogo responders are seeded for every user on first run
(`DefaultEmergencyContacts` in `model/Models.kt`): Bogo Police Station (primary),
Emergency Response Unit, and Bogo Fire Department.

Seeding is guarded by a `seededDefaults` flag in the stored settings JSON, which does
two jobs: installs that predate the defaults pick them up once on upgrade, and a
contact the user deliberately deletes does **not** reappear on the next launch. If one
is removed, a "Restore official numbers" button appears on the Emergency Contacts
screen — and only when something is actually missing.

## User roles

- **Student** — receives alerts, submits own status, gets a 6-char `shortCode`
- **Teacher / School Admin** — roster for their `classId`, live roll-call, close events
- **Parent / Guardian** — status of students in `linkedStudentIds`

## Screens (16)

Splash · Login · Role Selection · Sign-up · Parent Linking · Student Dashboard ·
Teacher Dashboard · Parent Dashboard · Earthquake Alert · Safety Confirmation ·
Live Safety Dashboard · Alert History · Demo Mode · Emergency Contacts · Settings ·
Safety Guide

Safety Guide is not in the prototype; it is carried over from the shipped v1.0 APK and
uses the 28 recovered `ic_sg_*` pictograms.

## Intensity thresholds

Must stay in lockstep with the firmware — `Intensity.fromMagnitude` here,
`BAND_YELLOW_G` / `BAND_RED_G` in `siren_esp32.ino`. Change one and the hardware and
the phone disagree about what colour an earthquake is.

| Band | Shown to users | g range | Level | Behaviour |
|---|---|---|---|---|
| Green | Intensity I–IV · Light shaking | 0.000 – 0.010 g | 1 | Notification only, single vibration |
| Yellow | Intensity V–VI · Moderate shaking | 0.010 – 0.120 g | 2 | Full-screen alert, repeating vibration |
| Red | Intensity VII+ · Destructive shaking | ≥ 0.120 g | 3 | Alarm sound, continuous vibration, full-screen intent |

**Users never see the g figure.** Every readout, notification and list row shows
`Intensity.levelText` ("Intensity V–VI") instead — a student reading "0.12 g" mid-
earthquake learns nothing, and intensity levels are the language drills already use.
`magnitudeG` is still stored on every alert for the study's results, and is still shown
on the Demo screen, which is a developer surface.

## Theme

**Light-only, deliberately.** `SirenTheme` ignores the system dark setting; there is no
dark scheme and no toggle in Settings. A dark scheme used to ship, and several screens
— the login screen worst of all — dropped to unreadable contrast under it. Do not
reintroduce one without contrast-checking every screen against WCAG AA.

`SettingsDoc` no longer has a `darkMode` field, but installs that predate this still
have the key in their stored JSON. `ignoreUnknownKeys = true` on the repository's `Json`
is what stops those settings failing to parse and wiping the user's saved emergency
contacts. Do not tighten it.

## Data model (Firestore)

```
users/{userId}
  name, email, role ("student"|"teacher"|"parent")
  classId, schoolId
  shortCode          # students only — the parent linking code
  linkedStudentIds[] # parents only

users/{userId}/responses/{alertId}
  alertId, status, respondedAt      # mirror, avoids a collection-group index

alerts/{alertId}
  intensity ("green"|"yellow"|"red"), magnitudeG, detectedAt
  source ("esp32"|"simulated"), nodeId, closed

alerts/{alertId}/responses/{userId}
  userId, name, status ("safe"|"needs_help"|"no_response"), respondedAt
```

Enums serialise lower-case via their `wire` property. Always read through
`Role.fromName` / `Intensity.fromName` — they fall back safely. Documents map through
`@Serializable` DTOs at the bottom of `SirenRepository.kt`.

Parents watch children with one document flow each, `combine`d — deliberately avoiding
a `whereIn` on document ids and the index that implies.

## Conventions

- Colours and spacing live in `ui/theme`; never hard-code hex values in screens
- Inter is the only font family
- Simulated events are always tagged so they stay separable from real sensor readings

## Secrets — not in this repo

`.gitignore` excludes `keystore.properties`, `*.jks`, and `app/google-services.json`.
A fresh clone needs all three restored before it can build a release. The original
`siren-release.jks` was lost with the old project folder and could not be recovered,
so the current key is a **new** one — devices holding the old v1.0 release must
uninstall before installing.

## Out of scope

- ESP32 firmware (separate hardware repo)
- QR-code scanning for parent linking (needs a camera dependency; code entry only)
- Structural damage assessment, evacuation routing, search-and-rescue
- Replacing official PHIVOLCS warnings — supplementary local tool only

## Testing priorities

1. Demo Mode: all three levels — colour, vibration, full-screen behaviour
2. Full loop: respond on one account, verify on a teacher/parent account
3. Offline: airplane mode → respond → reconnect → confirm sync
4. Push to topic `alerts` from the Firebase console
5. **Safety Guide icons and Inter fonts actually render** — the canary for the
   Compose-resources packaging workaround above
