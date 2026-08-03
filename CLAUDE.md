# CLAUDE.md — S.I.R.E.N. Mobile

Mobile companion app for **S.I.R.E.N. (Seismic Integrated Response and Emergency
Notification)** — an IoT earthquake detection system built around an Arduino R3 +
ESP32 + ADXL335 accelerometer (Practical Research 2, City of Bogo Senior High School).

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

## Authentication

Firebase **Email/Password**. Anonymous sign-in was used in v1.0 and has been removed —
it lost accounts on reinstall, breaking parent links and history.

- Role is chosen **before** the account exists, then written into the user document
- Role **can** be changed later (Settings → Switch role)
- The **ESP32 has its own account** and writes alert documents directly; the app only listens

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

Must stay in lockstep with the firmware (`Intensity.fromMagnitude`):

| Band | Range | Level | Behaviour |
|---|---|---|---|
| Green | ≤ 0.30 g | 1 | Notification only, single vibration |
| Yellow | 0.31 – 0.60 g | 2 | Full-screen alert, repeating vibration |
| Red | ≥ 0.61 g | 3 | Alarm sound, continuous vibration, full-screen intent |

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

- Arduino/ESP32 firmware (separate hardware repo)
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
