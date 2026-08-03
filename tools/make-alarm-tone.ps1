# Synthesises the S.I.R.E.N. emergency alert tone.
#
# NDRRMC-STYLE, NOT THE OFFICIAL TONE. Deliberately its own sound: this app is a
# supplementary local tool and must not be mistaken for an official government alert
# during a real earthquake.
#
# Two alternating tones with a harsh square-dominant timbre, no fade-in, sized so each
# segment is a whole number of cycles — that makes the loop seamless with no click at
# the wrap point.

param(
    [string]$OutPath = "C:\Users\Administrator\Desktop\Project\app\src\main\res\raw\siren_alarm.wav"
)

$ErrorActionPreference = 'Stop'

$sampleRate = 44100
$amplitude  = 0.82
$segments   = @(960.0, 720.0, 960.0, 720.0)   # Hz, alternating high/low
$segSeconds = 0.45

$samples = New-Object System.Collections.Generic.List[int16]

foreach ($freq in $segments) {
    # Whole number of cycles => segment ends at phase 0 => seamless join.
    $cycles = [math]::Round($freq * $segSeconds)
    $count  = [int][math]::Round($cycles * $sampleRate / $freq)

    for ($i = 0; $i -lt $count; $i++) {
        $phase = 2.0 * [math]::PI * $freq * $i / $sampleRate
        $sine  = [math]::Sin($phase)
        # Square dominates so the tone cuts through ambient noise; the sine term
        # takes the hardest edge off the aliasing.
        $square = if ($sine -ge 0) { 1.0 } else { -1.0 }
        $value = (0.72 * $square) + (0.28 * $sine)

        # 4ms ramp at each segment edge only, to avoid a step between tones.
        $ramp = 1.0
        $rampLen = [int]($sampleRate * 0.004)
        if ($i -lt $rampLen) { $ramp = $i / $rampLen }
        elseif ($i -ge ($count - $rampLen)) { $ramp = ($count - $i) / $rampLen }

        $s = [int][math]::Round($value * $ramp * $amplitude * 32767)
        if ($s -gt 32767) { $s = 32767 }
        if ($s -lt -32768) { $s = -32768 }
        $samples.Add([int16]$s)
    }
}

$dataBytes = $samples.Count * 2
$dir = Split-Path $OutPath -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

$fs = [System.IO.File]::Create($OutPath)
$bw = New-Object System.IO.BinaryWriter($fs)
try {
    # ---- RIFF header ----
    $bw.Write([char[]]'RIFF')
    $bw.Write([int](36 + $dataBytes))
    $bw.Write([char[]]'WAVE')
    # ---- fmt chunk ----
    $bw.Write([char[]]'fmt ')
    $bw.Write([int]16)                       # PCM chunk size
    $bw.Write([int16]1)                      # format = PCM
    $bw.Write([int16]1)                      # channels = mono
    $bw.Write([int]$sampleRate)
    $bw.Write([int]($sampleRate * 2))        # byte rate
    $bw.Write([int16]2)                      # block align
    $bw.Write([int16]16)                     # bits per sample
    # ---- data chunk ----
    $bw.Write([char[]]'data')
    $bw.Write([int]$dataBytes)
    foreach ($s in $samples) { $bw.Write($s) }
} finally {
    $bw.Dispose()
    $fs.Dispose()
}

$len = $samples.Count / [double]$sampleRate
Write-Output ("wrote {0}" -f $OutPath)
Write-Output ("  samples : {0}" -f $samples.Count)
Write-Output ("  duration: {0:N3}s (loops seamlessly)" -f $len)
Write-Output ("  size    : {0:N0} bytes" -f (Get-Item $OutPath).Length)
