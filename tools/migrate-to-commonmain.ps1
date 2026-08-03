# Mechanical half of "Remaining work — step 1" in CLAUDE.md.
#
# Rewrites the JVM/Android-only APIs in the screens that were copied into
# shared/src/commonMain so they compile as Compose Multiplatform common code.
#
# This script does NOT finish the job. After running it you must still fix by hand:
#   * SimpleDateFormat(...).format(Date(ms))  -> DateFmt.clock/date/dateTime/shortDateTime(ms)
#   * String.format(Locale.US, "%.2f", g)     -> g.toFixed(2) / g.asG() / g.asGSpaced()
#   * System.currentTimeMillis()              -> nowMillis() on PlatformServices (not yet defined)
#   * LocalContext.current + Haptics.x(ctx)   -> Haptics.x()
#   * androidx.activity.compose.BackHandler   -> androidx.compose.ui.backhandler.BackHandler
#   * Intent/Uri call + SMS                   -> Platform.services.dial() / .sendSms()
#   * SafetyGuideScreen GuideItem.iconRes:Int -> org.jetbrains.compose.resources.DrawableResource
#
# Verify with:  .\gradlew.bat :shared:compileCommonMainKotlinMetadata

$ErrorActionPreference = 'Stop'

$root = Join-Path (Split-Path $PSScriptRoot -Parent) 'shared\src\commonMain\kotlin\com\siren\mobile'
if (-not (Test-Path $root)) { throw "Shared source root not found: $root" }

$files = @(Get-ChildItem "$root\ui\screens" -Filter *.kt)
$appKt = Join-Path $root 'ui\App.kt'
if (Test-Path $appKt) { $files += Get-Item $appKt }

$enc = New-Object System.Text.UTF8Encoding($false)

foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName)
    $orig = $t

    # JVM-only date/locale imports are replaced by the shared DateFmt helper.
    $t = $t -replace '(?m)^import java\.text\.SimpleDateFormat\r?\n', ''
    $t = $t -replace '(?m)^import java\.util\.Date\r?\n', ''
    $t = $t -replace '(?m)^import java\.util\.Locale\r?\n', ''

    # Compose Multiplatform resource loading.
    $t = $t -replace 'import androidx\.compose\.ui\.res\.painterResource', 'import org.jetbrains.compose.resources.painterResource'
    $t = $t -replace '(?m)^import com\.siren\.mobile\.R\r?\n', "import com.siren.mobile.resources.*`n"
    $t = $t -replace '\bR\.drawable\.', 'Res.drawable.'
    $t = $t -replace '\bR\.font\.', 'Res.font.'

    if ($t -ne $orig) {
        [System.IO.File]::WriteAllText($f.FullName, $t, $enc)
        Write-Output ("patched: {0}" -f $f.Name)
    }
}

Write-Output ''
Write-Output '--- still needs manual fixing ---'
Select-String -Path "$root\ui\screens\*.kt", "$root\ui\App.kt" `
    -Pattern 'SimpleDateFormat|String\.format|LocalContext|System\.currentTimeMillis|Locale\.|BackHandler|android\.content|Uri\.|\bIntent\b' |
    ForEach-Object { "{0}:{1}: {2}" -f $_.Filename, $_.LineNumber, $_.Line.Trim() }
