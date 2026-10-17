<#
.SYNOPSIS
    Re-signs the Gradle optimized APK for release with the DIMA Now release key.

.DESCRIPTION
    Gradle keeps signing the "optimized" build type with the local debug key so CI and local
    development work without the release key. This script takes that APK and re-signs it with
    the release key alone (APK Signature Scheme v2 and v3; v1 and v4 disabled).

    D-099 moved the app to the new package io.github.winter1l.dimanow, which has never been
    debug-signed, so no proof-of-rotation lineage or debug key is involved. The lineage file in
    the signing folder (debug -> release, used by the old com.example.dimanow package) is read
    only to confirm the release certificate digest.
    Secrets are read from .local/release-signing/release-signing.properties and passed to
    apksigner through a process environment variable; they are never printed.

.EXAMPLE
    cmd /c gradlew-local.bat :app:assembleOptimized
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\Sign-Release.ps1
#>
[CmdletBinding()]
param(
    [string]$RepositoryRoot,
    [string]$InputApk,
    [string]$SigningDirectory,
    [string]$OutputDirectory,
    [string]$BuildToolsVersion
)

$ErrorActionPreference = 'Stop'
$passwordVariable = 'DIMANOW_RELEASE_SIGNING_PASS'

# $PSScriptRoot is not available in param defaults on Windows PowerShell 5.1.
if (-not $RepositoryRoot) { $RepositoryRoot = (Resolve-Path (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '..')).Path }

if (-not $InputApk) { $InputApk = Join-Path $RepositoryRoot 'app\build\outputs\apk\optimized\app-optimized.apk' }
if (-not $SigningDirectory) { $SigningDirectory = Join-Path $RepositoryRoot '.local\release-signing' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $RepositoryRoot 'dist' }

function Fail([string]$Message) {
    Write-Host "Sign-Release: $Message" -ForegroundColor Red
    exit 1
}

function Read-Properties([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*[#!]' -or $line -notmatch '=') { continue }
        $key, $value = $line -split '=', 2
        $values[$key.Trim()] = $value.Trim()
    }
    return $values
}

function Get-SdkDirectory {
    $localProperties = Join-Path $RepositoryRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkDir = (Read-Properties $localProperties)['sdk.dir']
        if ($sdkDir) {
            $sdkDir = $sdkDir -replace '\\:', ':' -replace '\\\\', '\'
            if (Test-Path -LiteralPath $sdkDir) { return $sdkDir }
        }
    }
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate }
    }
    Fail 'Android SDK not found (local.properties sdk.dir, ANDROID_HOME, ANDROID_SDK_ROOT or %LOCALAPPDATA%\Android\Sdk).'
}

function Get-BuildToolsDirectory([string]$SdkDirectory) {
    $root = Join-Path $SdkDirectory 'build-tools'
    if ($BuildToolsVersion) {
        $directory = Join-Path $root $BuildToolsVersion
        if (-not (Test-Path -LiteralPath (Join-Path $directory 'apksigner.bat'))) { Fail "build-tools $BuildToolsVersion has no apksigner." }
        return $directory
    }
    $latest = Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
        Where-Object { (Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat')) -and $_.Name -match '^\d+(\.\d+)*$' } |
        Sort-Object { [version]($_.Name + ('.0' * (3 - ($_.Name.Split('.').Count - 1)))) } -Descending |
        Select-Object -First 1
    if (-not $latest) { Fail "No build-tools with apksigner under $root." }
    return $latest.FullName
}

function Invoke-Tool([string]$Executable, [string[]]$Arguments, [string]$What) {
    # Native stderr must not become a terminating error under Windows PowerShell 5.1.
    $ErrorActionPreference = 'Continue'
    $output = & $Executable @Arguments 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) {
        # apksigner/aapt2 never echo passwords; still keep failure output short.
        $output | Select-Object -Last 15 | ForEach-Object { Write-Host "  $_" }
        Fail "$What failed (exit $LASTEXITCODE)."
    }
    return $output
}

# --- Preconditions -------------------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $SigningDirectory -PathType Container)) {
    Fail "Release signing folder is missing: $SigningDirectory`n  It holds the private release key and is never committed. Restore it from the offline backup (see the README.txt that was stored with it)."
}
$propertiesFile = Join-Path $SigningDirectory 'release-signing.properties'
if (-not (Test-Path -LiteralPath $propertiesFile)) { Fail "Missing $propertiesFile." }
$properties = Read-Properties $propertiesFile
foreach ($key in 'storeFile', 'keyAlias', 'storePassword') {
    if (-not $properties[$key]) { Fail "release-signing.properties has no '$key'." }
}
$releaseKeystore = Join-Path $SigningDirectory $properties['storeFile']
$lineageName = $properties['lineageFile']
if (-not $lineageName) { $lineageName = 'lineage' }
$lineageFile = Join-Path $SigningDirectory $lineageName
foreach ($required in $releaseKeystore, $lineageFile) {
    if (-not (Test-Path -LiteralPath $required)) { Fail "Missing $required." }
}
if (-not (Test-Path -LiteralPath $InputApk)) { Fail "Input APK not found: $InputApk`n  Build it first: gradlew-local.bat :app:assembleOptimized" }

$bundledJdk = Get-ChildItem -LiteralPath (Join-Path $RepositoryRoot '.tooling') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($bundledJdk) {
    $env:JAVA_HOME = $bundledJdk.FullName
    $env:Path = (Join-Path $bundledJdk.FullName 'bin') + ';' + $env:Path
}

$buildTools = Get-BuildToolsDirectory (Get-SdkDirectory)
$apksigner = Join-Path $buildTools 'apksigner.bat'
$aapt2 = Join-Path $buildTools 'aapt2.exe'
Write-Host "build-tools: $(Split-Path $buildTools -Leaf)"

# --- Version -------------------------------------------------------------------------------------
$badging = Invoke-Tool $aapt2 @('dump', 'badging', $InputApk) 'aapt2 dump badging'
$packageLine = $badging | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
if ($packageLine -notmatch "versionName='([0-9]+(?:\.[0-9]+)*)'") { Fail 'Could not read versionName from the APK.' }
$versionName = $Matches[1]
$null = $packageLine -match "versionCode='(\d+)'"
Write-Host "input: $(Split-Path $InputApk -Leaf) versionName=$versionName versionCode=$($Matches[1])"

# --- Sign ----------------------------------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$outputApk = Join-Path $OutputDirectory "DIMA-Now-v$versionName-optimized.apk"
$temporaryApk = "$outputApk.signing"
Remove-Item -LiteralPath $temporaryApk -ErrorAction SilentlyContinue

$keyPasswordSource = "env:$passwordVariable"
try {
    Set-Item -Path "Env:$passwordVariable" -Value $properties['storePassword']
    if ($properties['keyPassword'] -and $properties['keyPassword'] -ne $properties['storePassword']) {
        Set-Item -Path "Env:${passwordVariable}_KEY" -Value $properties['keyPassword']
        $keyPasswordSource = "env:${passwordVariable}_KEY"
    }
    $signArguments = @(
        'sign',
        '--in', $InputApk,
        '--out', $temporaryApk,
        '--v1-signing-enabled', 'false',
        '--v2-signing-enabled', 'true',
        '--v3-signing-enabled', 'true',
        '--v4-signing-enabled', 'false',
        '--ks', $releaseKeystore, '--ks-key-alias', $properties['keyAlias'], '--ks-pass', "env:$passwordVariable", '--key-pass', $keyPasswordSource
    )
    $null = Invoke-Tool $apksigner $signArguments 'apksigner sign'
} finally {
    Remove-Item -Path "Env:$passwordVariable" -ErrorAction SilentlyContinue
    Remove-Item -Path "Env:${passwordVariable}_KEY" -ErrorAction SilentlyContinue
}

# --- Verify --------------------------------------------------------------------------------------
$verify = Invoke-Tool $apksigner @('verify', '--print-certs', '-v', '--min-sdk-version', '24', $temporaryApk) 'apksigner verify'
$schemes = @{}
foreach ($line in $verify) {
    if ($line -match '^Verified using (v\d(?:\.\d)?) scheme .*: (true|false)$') { $schemes[$Matches[1]] = $Matches[2] }
}
$signerDigests = @($verify | Where-Object { $_ -match '^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$' } | ForEach-Object { ($_ -split ': ')[-1] } | Select-Object -Unique)
$releaseDigest = @((Invoke-Tool $apksigner @('lineage', '--in', $lineageFile, '--print-certs') 'apksigner lineage (file)') |
    Where-Object { $_ -match 'Signer #\d+ in lineage certificate SHA-256 digest: ([0-9a-f]{64})' } | ForEach-Object { ($_ -split ': ')[-1] }) | Select-Object -Last 1

Write-Host ''
Write-Host "Signature schemes: v1=$($schemes['v1']) v2=$($schemes['v2']) v3=$($schemes['v3'])"
Write-Host 'Signer certificate SHA-256:'
$signerDigests | ForEach-Object { Write-Host "  $_" }

$problems = @()
if ($schemes['v2'] -ne 'true') { $problems += 'v2 signature not verified' }
if ($schemes['v3'] -ne 'true') { $problems += 'v3 signature not verified' }
if (-not $releaseDigest) { $problems += 'release certificate digest unavailable from the lineage file' }
elseif (($signerDigests -join ',') -ne $releaseDigest) { $problems += 'APK is not signed by the release certificate alone' }
if ($problems.Count -gt 0) {
    Remove-Item -LiteralPath $temporaryApk -ErrorAction SilentlyContinue
    Fail ('Verification failed: ' + ($problems -join '; '))
}

Move-Item -LiteralPath $temporaryApk -Destination $outputApk -Force
$hash = (Get-FileHash -LiteralPath $outputApk -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host ''
Write-Host "Output: $outputApk"
Write-Host "Size:   $((Get-Item -LiteralPath $outputApk).Length) bytes"
Write-Host "SHA-256: $hash"
Write-Host "Upload this file unchanged as the release asset (the in-app updater expects this name)."
