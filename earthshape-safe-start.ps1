param(
    [Parameter(Mandatory = $true)]
    [string]$CommandLine,
    [string]$ModsDirectory = "mods",
    [string]$DisabledDirectory = "mods-disabled-by-earthshape",
    [int]$MaximumCrashRetries = 5
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $DisabledDirectory | Out-Null

function Get-LatestDiagnostic([datetime]$StartedAtUtc) {
    $crash = Get-ChildItem "crash-reports\crash-*.txt" -ErrorAction SilentlyContinue |
        Where-Object LastWriteTimeUtc -ge $StartedAtUtc |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($crash) { return $crash.FullName }
    if (Test-Path "logs\latest.log") {
        $latestLog = Get-Item "logs\latest.log"
        if ($latestLog.LastWriteTimeUtc -ge $StartedAtUtc) { return $latestLog.FullName }
    }
    return $null
}

function Get-FailedModId([string]$Text) {
    $matches = [regex]::Matches($Text, 'Failed to create mod instance\. ModID: ([A-Za-z0-9_.-]+)|(?m)^\s*Mod ID:\s*([A-Za-z0-9_.-]+)')
    if ($matches.Count -eq 0) { return "" }
    $match = $matches[$matches.Count - 1]
    if ($match.Groups[1].Success) { return $match.Groups[1].Value }
    return $match.Groups[2].Value
}

function Find-FailedJar([string]$Text, [string]$ModId) {
    if (-not [string]::IsNullOrWhiteSpace($ModId)) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        foreach ($jar in Get-ChildItem -LiteralPath $ModsDirectory -Filter '*.jar' -File -ErrorAction SilentlyContinue) {
            try {
                $zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
                try {
                    $entry = $zip.GetEntry('META-INF/neoforge.mods.toml')
                    if (-not $entry) { continue }
                    $reader = [IO.StreamReader]::new($entry.Open())
                    try { $manifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
                    if ($manifest -match ('modId\s*=\s*["'']' + [regex]::Escape($ModId) + '["'']')) {
                        return $jar.FullName
                    }
                } finally { $zip.Dispose() }
            } catch { }
        }
    }

    $directMatches = [regex]::Matches($Text, '(?m)^\s*Mod file:\s*(.*\.jar)\s*$')
    if ($directMatches.Count -gt 0) {
        $name = [IO.Path]::GetFileName($directMatches[$directMatches.Count - 1].Groups[1].Value.Trim())
        $candidate = Join-Path $ModsDirectory $name
        if (Test-Path -LiteralPath $candidate) { return (Resolve-Path -LiteralPath $candidate).Path }
    }
    return $null
}

for ($attempt = 0; $attempt -le $MaximumCrashRetries; $attempt++) {
    $startedAtUtc = [datetime]::UtcNow
    & cmd.exe /d /s /c $CommandLine
    $status = $LASTEXITCODE
    if ($status -eq 0) { exit 0 }
    if ($attempt -eq $MaximumCrashRetries) { exit $status }

    $diagnostic = Get-LatestDiagnostic $startedAtUtc
    if (-not $diagnostic) {
        Write-Error '[EarthShape safe-start] No crash report or latest.log was found; not disabling an unknown mod.'
        exit $status
    }
    $text = [IO.File]::ReadAllText($diagnostic)
    $modId = Get-FailedModId $text
    if ($modId -in @('minecraft', 'neoforge', 'earthshape')) {
        Write-Error "[EarthShape safe-start] Refusing to isolate protected mod '$modId'."
        exit $status
    }
    $failedJar = Find-FailedJar $text $modId
    if (-not $failedJar) {
        Write-Error "[EarthShape safe-start] Could not identify the failed mod JAR from $diagnostic."
        exit $status
    }

    $destination = Join-Path $DisabledDirectory ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [IO.Path]::GetFileName($failedJar))
    Move-Item -LiteralPath $failedJar -Destination $destination
    Write-Warning "[EarthShape safe-start] Isolated failed mod '$modId': $failedJar -> $destination"
    Write-Warning '[EarthShape safe-start] Restarting server without that JAR.'
}
