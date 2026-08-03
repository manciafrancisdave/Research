# CLAUDE.md — S.I.R.E.N. Mobile

Mobile companion app for **S.I.R.E.N. (Seismic Integrated Response and Emergency
Notification)** — an IoT earthquake detection system built around an Arduino R3 +
ESP32 + ADXL335 accelerometer (Practical Research 2, City of Bogo Senior High School).

This repo is the **app**, not the firmware. It receives alerts when the hardware
detects a seismic event, lets users confirm "I'm Safe" / "I Need Help", shows a live
safety dashboard for teachers and parents, and keeps a history log for the paper's
evaluation phase.

---

## ⚠️ Current state — read this first

| Module | Status |
|---|---|
| `:app` | ✅ **Complete and working.** Native Android. Debug + signed release APKs build. |
| `:shared` | 🚧 **Work in progress — does not compile yet.** Compose Multiplatform migration, ~40% done. |
| `iosApp/` | ❌ **Not created yet.** |

**Build only the Android module** until the migration finishes:

```powershell
$env:JAVA_HOME="C:\siren_toolchain\jdk-17.0.20+8"
$env:ANDROID_HOME="C:\Users\Administrator\AppData\Local\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd C:\Users\Administrator\Desktop\Project
.\gradlew.bat :app:assembleDebug        # or :app:assembleRelease
```

Do **not** run a bare `.\gradlew.bat build` — it will try to compile `:shared` and fail.
`:app` does not depend on `:shared`, so it is unaffected.

There is currently **duplicated code**: `:app` holds the complete working Android
source, and `:shared/src/commonMain` holds a partially-migrated copy. `:app` is the
source of truth until step 5 below deletes the duplicates.

---

## Why the app was rebuilt

The original project folder (`Desktop\Project`) was lost — only the built APKs
survived in `Desktop\SIREN-APK\`. The source was reconstructed by decompiling
`SIREN-v1.0-debug.apk` (jadx + apktool) and rebuilding against the Kotlin/Compose
prototype (`SIREN Android Prototype (standalone).html`, 14 screens).

Recovered intact and reused: the 28 `ic_sg_*` safety-guide pictograms, the 5 Inter
font weights, the launcher/notification vectors, and all Firebase identifiers.

**The original `siren-release.jks` was NOT recoverable.** A new signing key was
generated. Phones holding the old v1.0 release **must uninstall before installing**
— Android refuses to update an app whose signing key changed.

---

## Tech stack

- **Kotlin + Jetpack Compose** (Material 3), migrating to **Compose Multiplatform**
- **AGP 9.3.1 · Gradle 9.6.1 · Kotlin 2.4.10 · JDK 17** (`C:\siren_toolchain\jdk-17.0.20+8`)
- **compileSdk 37 · minSdk 24 · targetSdk 35**
- **applicationId** `com.research.siren` (must match Firebase) · **namespace** `com.siren.mobile`
- **Firebase** — Firestore, Cloud Messaging, Auth (**Email/Password**)
- **State** — `StateFlow` in a single `SirenRepository` singleton. No DI, no ViewModels;
  screens are pure composables fed from `AppRoot`.
- **Offline** — Firestore's on-device cache queues writes and replays on reconnect.
  Do not disable persistence.

---

## REMAINING WORK

### 1. Finish moving screens into `commonMain` 🚧

11 files under `shared/src/commonMain/.../ui/` are unmigrated copies and still
reference JVM/Android-only APIs. A prepared migration script does the mechanical
substitutions:

```powershell
.\tools\migrate-to-commonmain.ps1     # written but NEVER RUN
```

It rewrites: `java.text.SimpleDateFormat` / `java.util.Date` / `java.util.Locale`
imports out, `androidx.compose.ui.res.painterResource` →
`org.jetbrains.compose.resources.painterResource`, `com.siren.mobile.R` →
`com.siren.mobile.resources.*`, and `R.drawable.` / `R.font.` → `Res.drawable.` /
`Res.font.`.

After running it, these must be fixed **by hand** (the script does not touch them):

| Pattern | Replace with |
|---|---|
| `SimpleDateFormat(...).format(Date(ms))` | `DateFmt.clock/date/dateTime/shortDateTime(ms)` |
| `String.format(Locale.US, "%.2f", g)` | `g.toFixed(2)` / `g.asG()` / `g.asGSpaced()` |
| `System.currentTimeMillis()` | add `nowMillis()` to `PlatformServices` (not yet defined) |
| `LocalContext.current` + `Haptics.x(context)` | `Haptics.x()` — already context-free in shared |
| `androidx.activity.compose.BackHandler` | `androidx.compose.ui.backhandler.BackHandler` (CMP 1.8+) |
| `Intent` / `Uri` for call & SMS | `Platform.services.dial(phone)` / `.sendSms(phone)` |

`SafetyGuideScreen.kt` needs extra care: `GuideItem.iconRes` is typed `Int` and must
become `org.jetbrains.compose.resources.DrawableResource`.

Verify cheaply — this type-checks common code against **both** platforms and takes
~10 s once warm:

```powershell
.\gradlew.bat :shared:compileCommonMainKotlinMetadata
```

### 2. Rewrite `SirenRepository` on the GitLive Firebase KMP SDK 🚧

`:app`'s repository uses the Android-only Firebase SDK. The shared version must use
`dev.gitlive:firebase-auth` / `firebase-firestore` (2.5.0, already declared in
`shared/build.gradle.kts`).

Main API shifts: `ListenerRegistration` + callbacks become `Flow`s
(`document.snapshots`, `query.snapshots`), and `auth.addAuthStateListener` becomes
`Firebase.auth.authStateChanged: Flow<FirebaseUser?>`. Settings JSON moves from
`org.json` to `kotlinx-serialization` (plugin already applied).

Keep the existing public surface so the screens need no changes: `user`, `signedIn`,
`alerts`, `myResponses`, `roster`, `settings`, `online`, `incomingAlert`,
`authLoading`, `authError`, `authResolved`, `events`.

### 3. Implement `AndroidPlatformServices` 🚧

`shared/src/commonMain/.../platform/Platform.kt` defines the `PlatformServices`
interface (vibration, notifications, dial/SMS, settings persistence, FCM topic).
Only the no-op fallback exists. Port the real bodies from `:app`'s `Notifier.kt`
and `Haptics.kt`, then call `Platform.install(...)` in `SirenApp.onCreate`.

### 4. Implement `IosPlatformServices` ❌

iOS equivalents: `UNUserNotificationCenter` (notifications),
`UIImpactFeedbackGenerator` / `AudioServicesPlaySystemSound` (haptics),
`NSUserDefaults` (settings), `UIApplication.sharedApplication.openURL` (`tel:` /
`sms:`), Firebase Messaging (topic subscribe).

**This cannot be compiled on Windows** — see "iOS prerequisites" below.

### 5. Rewire `:app` as a thin host 🚧

Add `implementation(project(":shared"))`, reduce `MainActivity` to
`setContent { App() }`, and **delete** the duplicated `model/`, `ui/`, `data/`
packages from `app/src/main/java/`. Keep in `:app`: `AndroidManifest.xml`,
launcher/splash resources under `res/`, `google-services.json`, `SirenApp.kt`,
`MainActivity.kt`, `SirenMessagingService.kt`.

### 6. Create `iosApp/` ❌

Xcode project consuming the `ComposeApp` framework, a `Podfile` for the Firebase iOS
SDK, `Info.plist` with the notification capability, and a `MainViewController` that
bridges to `App()`. **Must be generated and opened on a Mac.**

---

## iOS prerequisites (hard blockers, not code problems)

1. **A Mac is mandatory.** Xcode does not exist for Windows — no emulator, no
   cross-compiler. This project was developed on Windows 10, so no iOS binary has
   ever been produced or tested. Options: a physical Mac, or a cloud Mac
   (Codemagic, MacStadium, GitHub Actions `macos-latest` runners).
2. **A paid Apple Developer account ($99/year) is required for alerts.** Free
   provisioning sideloads for 7 days but does **not** grant the Push Notifications
   capability, and APNs is mandatory for FCM to reach an iPhone. Without it an iOS
   build installs and runs but never alerts anyone — which defeats the app's purpose.
3. **Firebase needs a second app registered.** The project currently has only an
   Android app (`1:888788240342:android:…`). Add an iOS app in the Firebase console,
   download `GoogleService-Info.plist`, and upload an APNs auth key.
4. **Scope note.** Both earlier drafts of this file listed iOS as *Explicitly Out of
   Scope* ("matches the project's stated scope — no iOS build needed"). If the
   research paper says Android-only, its scope section needs amending too.

On the Mac, first run:

```bash
cd iosApp && pod install
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
open iosApp.xcworkspace
```

---

## Hard-won constraints — do not rediscover these

Each of these cost a debugging cycle:

- **AGP 9 rejects `org.jetbrains.kotlin.android`.** Kotlin support is built in. The
  Compose *compiler* plugin (`org.jetbrains.kotlin.plugin.compose`) is still required
  separately.
- **AGP 9 rejects `com.android.application` + `org.jetbrains.kotlin.multiplatform`.**
  This is why shared code is a *library* (`com.android.kotlin.multiplatform.library`)
  with `:app` as a separate host, rather than one `composeApp` module.
- **Declare KMP/Compose plugin versions only in the root `build.gradle.kts`** with
  `apply false`; submodules apply them by bare id. Repeating a version in a submodule
  fails with *"already on the classpath with an unknown version."*
- **`material-icons-extended` was discontinued for Multiplatform after 1.7.3.** The
  app uses ~40 extended icons, so 1.7.3 is pinned deliberately against CMP 1.11.1.
  It resolves and type-checks on both platforms — verified.
- **`iosX64` is omitted** (Intel-Mac simulator). Some dependencies no longer publish
  that variant; Apple Silicon uses `iosSimulatorArm64`.
- **CMP's `Font()` is `@Composable`**, so typography cannot be a top-level `val` —
  hence `interFamily()` / `sirenTypography()`.
- **`androidx.fragment` must be pinned to 1.8.9.** Firebase drags in 1.1.0
  transitively and `lintVitalRelease` then fails `registerForActivityResult` with
  *InvalidFragmentVersionForActivityResult*. This only breaks **release** builds.
- **Auto-mirrored icons need their own import** —
  `androidx.compose.material.icons.automirrored.filled.ArrowBack`.
- **Android Studio's bundled JBR 25 is too new.** Always build with JDK 17.

---

## Authentication

Firebase **Email/Password**. Anonymous sign-in was used in v1.0 and has been removed
— it lost accounts on reinstall, breaking parent links and history.

- Role is chosen **before** the account exists, then written into the user document
- Role **can** be changed later (Settings → Switch role → `repo.updateRole`)
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

Safety Guide is not in the prototype; it is carried over from the shipped v1.0 APK
and uses the 28 recovered `ic_sg_*` pictograms.

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
`Role.fromName` / `Intensity.fromName` — they fall back safely.

## Conventions

- Colours and spacing live in `ui/theme`; never hard-code hex values in screens
- Inter is the only font family
- Firestore documents are mapped by hand (`getString`, `getDouble`), never
  `toObject()` — keeps R8 rules minimal
- Simulated events are always tagged so they stay separable from real sensor
  readings in the study's data

## Secrets — not in this repo

`.gitignore` excludes `keystore.properties`, `*.jks`, and
`app/google-services.json`. A fresh clone needs all three restored before it can
build a release. Losing `siren-release.jks` means no one can ship an update to an
installed app without a full uninstall/reinstall.

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
