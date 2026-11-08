# Packaged-application smoke test (Windows): drives the built jar exactly as an administrator would -
# check-config, status, migrate (twice), serve with readiness polling, worker start/stop - using a
# Storage Volume root given as a native Windows path. Phase 2: registers and initializes the volume,
# uploads a file through the browser upload API, lets the worker archive it, and downloads it again
# byte for byte. Requires:
#   target\genealogy-archive.jar      (.\mvnw.cmd package or verify)
#   GAS_SMOKE_DB_URL                  JDBC URL of an EMPTY database
#   GAS_TEST_DB_USER, GAS_TEST_DB_PASSWORD
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$Jar = (Resolve-Path 'target\genealogy-archive.jar').Path
$Port = if ($env:GAS_SMOKE_PORT) { $env:GAS_SMOKE_PORT } else { 18080 }
foreach ($v in 'GAS_SMOKE_DB_URL', 'GAS_TEST_DB_USER', 'GAS_TEST_DB_PASSWORD') {
    if (-not [Environment]::GetEnvironmentVariable($v)) { throw "Missing environment variable $v" }
}

$Work = Join-Path ([IO.Path]::GetTempPath()) ("gas-smoke-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path "$Work\logs", "$Work\volume-primary" | Out-Null
@"
database:
  url: '$($env:GAS_SMOKE_DB_URL)'
  username: '$($env:GAS_TEST_DB_USER)'
server:
  bindAddress: 127.0.0.1
  port: $Port
logging:
  format: json
  directory: '$Work\logs'
storageRoots:
  '0192f0c4-1111-7000-8000-000000000001': '$Work\volume-primary'
"@ | Set-Content -Path "$Work\installation.yml" -Encoding utf8

$env:GAS_DB_PASSWORD = $env:GAS_TEST_DB_PASSWORD
$Config = "--config=$Work\installation.yml"

function Fail([string]$Message) { Write-Output "::error::smoke test: $Message"; exit 1 }
function Expect-Exit([int]$Want, [string[]]$CommandArgs) {
    & java -jar $Jar @CommandArgs $Config
    $got = $LASTEXITCODE
    if ($got -ne $Want) { Fail "'$($CommandArgs -join ' ')' exited $got, expected $Want" }
    Write-Output "ok: '$($CommandArgs -join ' ')' exited $got"
}

Expect-Exit 0 @('check-config')
Expect-Exit 3 @('status')      # not yet migrated: visibly not ready
Expect-Exit 0 @('migrate')
Expect-Exit 0 @('migrate')     # repeat is a no-op
Expect-Exit 3 @('status')      # Phase 1: migrated but the archive is not initialized yet
Expect-Exit 0 @('init-archive', '--name=Smoke Test Archive')
Expect-Exit 5 @('init-archive', '--name=Second Archive')   # an Archive ID is created only once
$AdminPw = 'smoke-' + (-join ((1..24) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) }))
$env:GAS_NEW_ADMIN_PASSWORD = $AdminPw
Expect-Exit 0 @('create-admin', '--username=smoke-admin')
Remove-Item Env:GAS_NEW_ADMIN_PASSWORD
Expect-Exit 0 @('status')

# ---- Phase 2: Storage Volume registration and initialization (P2-C, P2-D) ----------------------
$registered = & java -jar $Jar register-volume '--name=Primary' '--purpose=primary' $Config
if ($LASTEXITCODE -ne 0) { Fail "register-volume exited $LASTEXITCODE" }
$VolumeId = ($registered | Select-String -Pattern 'Volume ID\s*:\s*([0-9a-f-]{36})').Matches[0].Groups[1].Value
if (-not $VolumeId) { Fail "register-volume printed no Volume ID" }
New-Item -ItemType Directory -Path "$Work\staging", "$Work\cache" | Out-Null
@"
database:
  url: '$($env:GAS_SMOKE_DB_URL)'
  username: '$($env:GAS_TEST_DB_USER)'
server:
  bindAddress: 127.0.0.1
  port: $Port
logging:
  format: json
  directory: '$Work\logs'
storageRoots:
  '$VolumeId': '$Work\volume-primary'
files:
  stagingDirectory: '$Work\staging'
  cacheDirectory: '$Work\cache'
"@ | Set-Content -Path "$Work\installation.yml" -Encoding utf8
Expect-Exit 0 @('check-config')
Expect-Exit 0 @('init-volume', "--volume-id=$VolumeId")
Expect-Exit 5 @('init-volume', "--volume-id=$VolumeId")   # a marker is never written twice
if (-not (Test-Path "$Work\volume-primary\.genealogy-archive-volume")) { Fail "volume marker not written" }
Expect-Exit 0 @('set-ingestion-volume', "--volume-id=$VolumeId")

$web = Start-Process -FilePath 'java' -ArgumentList @('-jar', "`"$Jar`"", 'serve', "`"$Config`"") -PassThru -NoNewWindow
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/actuator/health/readiness" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch { }
    if ($web.HasExited) { Fail "web process exited during startup" }
    Start-Sleep -Seconds 1
}
if (-not $ready) { Stop-Process -Id $web.Id -Force; Fail "web application not ready" }
$page = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/" -UseBasicParsing
if ($page.Content -notmatch 'Archive instance is ready') { Stop-Process -Id $web.Id -Force; Fail "home page does not report ready" }
# Phase 1: sign in through the real form (CSRF token from the page) and read an archive page.
$login = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/login" -UseBasicParsing -SessionVariable browser
$tokenMatch = [regex]::Match($login.Content, 'name="_csrf" value="([^"]+)"')
if (-not $tokenMatch.Success) { Stop-Process -Id $web.Id -Force; Fail "no CSRF token on the sign-in page" }
$signIn = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/login" -Method Post -WebSession $browser -UseBasicParsing `
    -Body @{ _csrf = $tokenMatch.Groups[1].Value; username = 'smoke-admin'; password = $AdminPw } `
    -MaximumRedirection 0 -SkipHttpErrorCheck -ErrorAction SilentlyContinue
$location = [string]$signIn.Headers.Location
if (-not $location -or $location -match 'error') { Stop-Process -Id $web.Id -Force; Fail "sign-in failed (redirect: $location)" }
$archivePage = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/archive" -WebSession $browser -UseBasicParsing
if ($archivePage.Content -notmatch 'Smoke Test Archive') { Stop-Process -Id $web.Id -Force; Fail "signed-in archive page not shown" }
$anonymous = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/archive" -UseBasicParsing -MaximumRedirection 0 -SkipHttpErrorCheck -ErrorAction SilentlyContinue
if ($anonymous.StatusCode -ne 302) { Stop-Process -Id $web.Id -Force; Fail "archive page reachable without sign-in" }
Write-Output "ok: local sign-in works and protects archive pages"

# ---- Phase 2: browser upload -> worker -> verified, protected original -> download --------------
$worker = Start-Process -FilePath 'java' -ArgumentList @('-jar', "`"$Jar`"", 'worker', "`"$Config`"") -PassThru -NoNewWindow
try {
    $scan = "$Work\scan.bin"
    $bytes = New-Object byte[] 3145731
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    [IO.File]::WriteAllBytes($scan, $bytes)
    $uploadPage = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/files/upload" -WebSession $browser -UseBasicParsing
    if ($uploadPage.Content -notmatch 'Uploads are stored on volume') { Fail "upload page does not report readiness" }
    $token = [regex]::Match($uploadPage.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
    $batchResponse = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/files/batches" -Method Post -WebSession $browser -UseBasicParsing `
        -Body @{ _csrf = $token; note = 'smoke test' } -MaximumRedirection 0 -SkipHttpErrorCheck -ErrorAction SilentlyContinue
    $batch = [regex]::Match([string]$batchResponse.Headers.Location, '/files/batches/([0-9a-f-]{36})').Groups[1].Value
    if (-not $batch) { Fail "no upload batch created (redirect: $($batchResponse.Headers.Location))" }
    $put = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/files/batches/$batch/items/smoke-1" -Method Put -WebSession $browser `
        -UseBasicParsing -InFile $scan -ContentType 'application/octet-stream' -SkipHttpErrorCheck `
        -Headers @{ 'X-CSRF-TOKEN' = $token; 'X-File-Name' = 'smoke%20scan%201.tif'; 'X-File-Last-Modified' = '1577934245000' }
    $op = [regex]::Match($put.Content, '"operationId":"([0-9a-f-]{36})"').Groups[1].Value
    if (-not $op) { Fail "upload not accepted: HTTP $($put.StatusCode) $($put.Content)" }
    $fileId = $null
    $st = ''
    for ($i = 0; $i -lt 60; $i++) {
        $st = (Invoke-WebRequest -Uri "http://127.0.0.1:$Port/files/operations/$op/status" -WebSession $browser -UseBasicParsing).Content
        if ($st -match '"archived":true') { $fileId = [regex]::Match($st, '"fileId":"([0-9a-f-]{36})"').Groups[1].Value; break }
        Start-Sleep -Seconds 1
    }
    if (-not $fileId) { Fail "upload was not archived by the worker (last status: $st)" }
    Invoke-WebRequest -Uri "http://127.0.0.1:$Port/files/$fileId/download" -WebSession $browser -UseBasicParsing -OutFile "$Work\downloaded.bin"
    if ((Get-FileHash $scan).Hash -ne (Get-FileHash "$Work\downloaded.bin").Hash) { Fail "downloaded original differs from the uploaded bytes" }
    $stored = Get-ChildItem "$Work\volume-primary\originals" -Recurse -File | Select-Object -First 1
    if (-not $stored -or -not $stored.IsReadOnly) { Fail "stored original is not read-only" }
    if ($stored.Name -match 'smoke') { Fail "stored filename derived from the uploaded name" }
    for ($i = 0; $i -lt 20 -and (Get-ChildItem "$Work\staging" -Force); $i++) { Start-Sleep -Seconds 1 }
    if (Get-ChildItem "$Work\staging" -Force) { Fail "staging not cleaned after archiving" }
} finally {
    Stop-Process -Id $worker.Id -Force -ErrorAction SilentlyContinue
    $worker.WaitForExit()
}
Write-Output "ok: browser upload archived by the worker, protected, downloaded byte for byte"
Stop-Process -Id $web.Id -Force   # graceful stop on Windows is exercised by the WinSW service test
$web.WaitForExit()
Write-Output "ok: web application started, ready, stopped"

$worker = Start-Process -FilePath 'java' -ArgumentList @('-jar', "`"$Jar`"", 'worker', "`"$Config`"") -PassThru -NoNewWindow
Start-Sleep -Seconds 10
if ($worker.HasExited) { Fail "worker exited unexpectedly (exit code $($worker.ExitCode))" }
Stop-Process -Id $worker.Id -Force
$worker.WaitForExit()
Write-Output "ok: worker started and stopped"

$webLog = Get-Content "$Work\logs\genealogy-archive-web.log" -Raw
if ($webLog -notmatch '"ecs":\{"version"') { Fail "structured JSON web log not written" }
if (Select-String -Path "$Work\logs\*.log" -SimpleMatch -Pattern $env:GAS_TEST_DB_PASSWORD -Quiet) { Fail "database password found in logs" }
if (Select-String -Path "$Work\logs\*.log" -SimpleMatch -Pattern $AdminPw -Quiet) { Fail "administrator password found in logs" }
Write-Output "ok: structured logs written, no secrets in logs"
Write-Output "SMOKE TEST PASSED"
exit 0
