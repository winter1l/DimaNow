[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
# Inspect tracked working-tree contents, including staged additions. Never echo matching text.
$identifierPatterns = @(
    '(?<![A-Za-z0-9_])R[0-9][A-Z0-9]{8,}(?![A-Za-z0-9_])',
    '(?i)(?<![A-Za-z0-9])adb-[A-Za-z0-9._:-]{8,}(?![A-Za-z0-9])'
)
$ipv4Pattern = '(?<![\d.])(?<ip>(?:\d{1,3}\.){3}\d{1,3})(?::(?<port>\d{1,5}))?(?![\d.])'
$adbEndpointPattern = '(?i)(?:\bADB\s+serial\s+|\bserial\s+`|\bANDROID_SERIAL\s*=\s*|\badb\s+(?:-P\s+\d+\s+)?-s\s+)[`"'']*(?<ip>(?:\d{1,3}\.){3}\d{1,3}):\d{1,5}'

function Test-PrivateAddress([string]$Address) {
    $parsed = $null
    if (-not [System.Net.IPAddress]::TryParse($Address, [ref]$parsed)) { return $false }
    $octets = $parsed.GetAddressBytes()
    return $octets.Length -eq 4 -and (
        $octets[0] -eq 10 -or
        ($octets[0] -eq 172 -and $octets[1] -ge 16 -and $octets[1] -le 31) -or
        ($octets[0] -eq 192 -and $octets[1] -eq 168) -or
        ($octets[0] -eq 100 -and $octets[1] -ge 64 -and $octets[1] -le 127)
    )
}

function Get-ForbiddenPathReason([string]$Path) {
    if ($Path -match '(^|/)(?:\.codex-remote-attachments|\.local)(/|$)') { return 'private attachment or local connection data' }
    $name = ($Path -split '/')[-1]
    if ($name -match '^codex-clipboard-.*\.png$' -or $name -match '\.(?:cookies|har)$') { return 'private capture or session data' }
    if ($name -eq 'local.properties') { return 'local machine configuration' }
    if ($name -match '^\.env(?:\..+)?$' -and $name -notmatch '\.(?:example|sample|template)$') { return 'local environment file' }
    if ($name -match '\.(?:jks|keystore|p12|pfx|key)$' -or $name -match '^id_(?:rsa|dsa|ecdsa|ed25519)$') { return 'private key or signing store' }
    # Deliberate non-secret fixtures may contain these formats; generated outputs may not.
    $isFixture = $Path -match '(^|/)(?:fixtures|testdata)(/|$)|/src/(?:test|androidTest)/resources/'
    if (-not $isFixture -and $name -match '\.(?:apk|aab|db|sqlite|sqlite3|log)(?:-(?:wal|shm))?$') { return 'local data or generated output' }
    return $null
}

function Get-SafePath([string]$Path) {
    $safe = [regex]::Replace($Path, $ipv4Pattern, '[address]')
    foreach ($pattern in $identifierPatterns) { $safe = [regex]::Replace($safe, $pattern, '[device]') }
    return $safe
}

$violations = [System.Collections.Generic.List[string]]::new()
$trackedOutput = & git -C $RepositoryRoot -c core.quotepath=false ls-files -z
if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate tracked files.' }
$trackedFiles = ($trackedOutput -join "`n") -split "`0" | Where-Object { $_ }
foreach ($relativePath in $trackedFiles) {
    $safePath = Get-SafePath $relativePath
    $reason = Get-ForbiddenPathReason $relativePath
    if ($reason) { $violations.Add("${safePath}: tracked $reason") }
    $path = Join-Path $RepositoryRoot $relativePath
    # A tracked deletion is safe to stage; submodule directories have no text to inspect.
    if (-not [System.IO.File]::Exists($path)) { continue }
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $isUtf16 = $bytes.Length -ge 2 -and (($bytes[0] -eq 255 -and $bytes[1] -eq 254) -or ($bytes[0] -eq 254 -and $bytes[1] -eq 255))
    if (-not $isUtf16 -and $bytes -contains 0) { continue }
    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadLines($path)) {
        $lineNumber++
        $hasIdentifier = $false
        foreach ($pattern in $identifierPatterns) {
            if ([regex]::IsMatch($line, $pattern)) { $hasIdentifier = $true; break }
        }
        foreach ($match in [regex]::Matches($line, $ipv4Pattern)) {
            # This existing SSRF regression deliberately exercises the CGNAT boundary.
            $isReservedAddressFixture = $relativePath -eq 'data-pipeline/src/test/kotlin/com/example/dimanow/pipeline/MealRemoteHttpClientTest.kt' -and
                $match.Groups['ip'].Value -eq (@(100, 64, 0, 1) -join '.') -and -not $match.Groups['port'].Success
            if (-not $isReservedAddressFixture -and (Test-PrivateAddress $match.Groups['ip'].Value)) { $hasIdentifier = $true; break }
        }
        foreach ($match in [regex]::Matches($line, $adbEndpointPattern)) {
            $address = $match.Groups['ip'].Value
            if ($address -notmatch '^(?:127\.|0\.0\.0\.0$)') { $hasIdentifier = $true; break }
        }
        if ($hasIdentifier) { $violations.Add("${safePath}:${lineNumber}: physical device identifier or private address") }
    }
}

if ($violations.Count -gt 0) {
    $violations | Sort-Object -Unique | ForEach-Object { [Console]::Error.WriteLine("Tracking privacy violation at $_. Use a test-device alias or remove the private file from tracking.") }
    exit 1
}
Write-Host 'No tracked private attachments, local artifacts, or physical device identifiers found.'
