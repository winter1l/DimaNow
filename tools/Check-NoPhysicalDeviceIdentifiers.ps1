[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$approvedAliases = @(
    'test-device-galaxy-api36-a',
    'test-device-galaxy-tab-api36-a'
)
$identifierPatterns = @(
    '(?<![A-Za-z0-9_])(?<identifier>R[0-9][A-Z0-9]{8,})(?![A-Za-z0-9_])',
    '(?i)(?<![A-Za-z0-9])(?<identifier>adb-[A-Za-z0-9._:-]{8,})(?![A-Za-z0-9])',
    '(?i)\bADB\s+serial\s+`?(?<identifier>[^`\s,;)]+)',
    '(?i)\bserial\s+`(?<identifier>[^`]+)`',
    '(?i)\bANDROID_SERIAL\s*=\s*(?<identifier>[^`\s]+)',
    '(?i)\badb\s+-s\s+(?<identifier>[^`\s]+)'
)

$violations = [System.Collections.Generic.List[string]]::new()
$trackedMarkdown = & git -C $RepositoryRoot ls-files -- '*.md'
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to enumerate tracked Markdown files.'
}

foreach ($relativePath in $trackedMarkdown) {
    $path = Join-Path $RepositoryRoot $relativePath
    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadLines($path)) {
        $lineNumber++
        foreach ($pattern in $identifierPatterns) {
            foreach ($match in [regex]::Matches($line, $pattern)) {
                $identifier = $match.Groups['identifier'].Value.Trim('`', '"', "'")
                $isApproved = $identifier -in $approvedAliases -or $identifier -match '^emulator-[0-9]+$'
                $isPhysical =
                    $identifier -cmatch '^R[0-9][A-Z0-9]{8,}$' -or
                    $identifier -match '^adb-[A-Za-z0-9._:-]{8,}$' -or
                    $identifier -match '^(?:[0-9]{1,3}\.){3}[0-9]{1,3}:[0-9]{2,5}$'
                if ($isPhysical -and -not $isApproved) {
                    $violations.Add("${relativePath}:$lineNumber")
                }
            }
        }
    }
}

if ($violations.Count -gt 0) {
    $violations | Sort-Object -Unique | ForEach-Object {
        [Console]::Error.WriteLine("Physical ADB identifier found at $_. Replace it with an approved test-device alias.")
    }
    exit 1
}

Write-Host 'No tracked Markdown file contains a physical ADB identifier.'
