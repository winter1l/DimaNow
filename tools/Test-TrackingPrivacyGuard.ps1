[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$checker = Join-Path $PSScriptRoot 'Check-NoPhysicalDeviceIdentifiers.ps1'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$shell = (Get-Process -Id $PID).Path
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('dimanow-tracking-guard-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $testRoot
$testCount = 0

function Write-Tracked([string]$RelativePath, [string]$Content) {
    $destination = Join-Path $testRoot $RelativePath
    $null = New-Item -ItemType Directory -Force -Path (Split-Path $destination)
    [IO.File]::WriteAllText($destination, $Content)
    & git -C $testRoot add -f -- $RelativePath 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to add disposable test fixture.' }
}
function Assert-Check([bool]$ShouldPass, [string]$Label, [string[]]$MustNotPrint = @()) {
    $output = (& $shell -NoProfile -File $checker -RepositoryRoot $testRoot 2>&1 | Out-String)
    $passed = $LASTEXITCODE -eq 0
    if ($passed -ne $ShouldPass) { throw "Guard regression failed: $Label (unexpected status; captured output withheld)." }
    foreach ($privateValue in $MustNotPrint) {
        if ($output.Contains($privateValue)) { throw "Guard regression failed: $Label leaked a synthetic identifier." }
    }
    $script:testCount++
    Write-Host "PASS $Label"
}
function Remove-Tracked([string]$RelativePath) {
    & git -C $testRoot rm -f -- $RelativePath 1>$null 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to reset disposable fixture.' }
}
try {
    & git -C $testRoot init --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create disposable repository.' }
    & git -C $testRoot config core.excludesFile (Join-Path $testRoot '.git/empty-global-ignore')
    Copy-Item -LiteralPath (Join-Path $repository '.gitignore') -Destination (Join-Path $testRoot '.gitignore')
    Write-Tracked 'README.md' 'test-device-galaxy-api37-a emulator-5554 adb -s 127.0.0.1:5555 and 0.0.0.0; docs use 192.0.2.1.'
    Write-Tracked '.env.example' 'TOKEN=replace-me'
    Write-Tracked 'app/src/test/resources/fixtures/sample.db' 'Synthetic fixture'
    Assert-Check $true 'safe aliases, loopback, documentation addresses and deliberate fixtures'

    $privateAddress = @(100, 87, 45, 23) -join '.'
    $endpoint = $privateAddress + ':5555'
    Write-Tracked 'notes.md' "Standalone: $endpoint"
    Assert-Check $false 'standalone endpoint in Markdown' @($privateAddress, $endpoint)
    Remove-Tracked 'notes.md'
    Write-Tracked 'settings.json' ('{"host":"' + $privateAddress + '"}')
    Assert-Check $false 'standalone address outside Markdown' @($privateAddress)
    Remove-Tracked 'settings.json'
    foreach ($parts in @(@(10, 23, 45, 67), @(172, 22, 33, 44), @(192, 168, 42, 43))) {
        $address = $parts -join '.'
        Write-Tracked 'notes.txt' $address
        Assert-Check $false 'private LAN address' @($address)
        Remove-Tracked 'notes.txt'
    }
    $serial = 'R' + '3' + 'ABCDEF1234'
    Write-Tracked 'capture.txt' $serial
    Assert-Check $false 'physical serial masked' @($serial)
    Remove-Tracked 'capture.txt'
    $mdns = 'adb-' + 'ABCDEF123456' + '-debug'
    Write-Tracked 'capture.txt' $mdns
    Assert-Check $false 'wireless discovery identifier masked' @($mdns)
    Remove-Tracked 'capture.txt'

    foreach ($privatePath in @('.codex-remote-attachments/session/photo.jpg', '.local/device-connections.json', 'nested/.env.production', 'local.properties', 'signing.jks', 'profile.db', 'build.apk', 'device.log', 'browser.cookies', 'traffic.har', 'codex-clipboard-synthetic.png')) {
        Write-Tracked $privatePath 'PRIVATE_SENTINEL_MUST_NOT_BE_PRINTED'
        Assert-Check $false 'force-added private artifact rejected' @('PRIVATE_SENTINEL_MUST_NOT_BE_PRINTED')
        Remove-Tracked $privatePath
    }
    Write-Tracked 'unchanged.md' 'Initially safe'
    [IO.File]::WriteAllText((Join-Path $testRoot 'unchanged.md'), $endpoint)
    Assert-Check $false 'unstaged content of tracked file inspected' @($endpoint)
    Remove-Tracked 'unchanged.md'
    [IO.File]::WriteAllText((Join-Path $testRoot 'untracked.txt'), $endpoint)
    Assert-Check $true 'untracked local content is not published or scanned'

    foreach ($ignoredPath in @('.codex-remote-attachments/session/photo.jpg', '.local/device-connections.json', 'nested/.env.production', 'local.properties', 'signing.jks', 'profile.db', 'build.apk', 'device.log', 'artifacts/phone.png')) {
        & git -C $testRoot check-ignore -q --no-index -- $ignoredPath
        if ($LASTEXITCODE -ne 0) { throw 'Repository ignore rules missed a private artifact test case.' }
        $testCount++
    }
    & git -C $testRoot check-ignore -q --no-index -- '.env.example'
    if ($LASTEXITCODE -ne 1) { throw 'Expected the environment example to be trackable without a Git error.' }
    $testCount++
    Write-Host "PASS repository ignore policy. Total assertions: $testCount"
}
finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($tempParent, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path $resolved -Leaf) -notmatch '^dimanow-tracking-guard-[a-f0-9]{32}$') {
        throw 'Refusing cleanup outside the disposable test directory.'
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
# GitHub Actions propagates the expected check-ignore non-match unless success is explicit.
exit 0
