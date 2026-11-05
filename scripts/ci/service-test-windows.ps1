# CI only: installs the real Windows services with deploy\windows\install-services.ps1 (WinSW v2),
# runs the web application and worker as Windows services, checks readiness, stops them through
# the Service Control Manager and verifies a graceful stop from the application logs. Phase 2: checks
# that each service runs as its own virtual service account, grants the volume with
# grant-volume-access.ps1, proves from inside the web service that it cannot write archival originals,
# uploads a file through the browser API, waits for the worker service to archive it and downloads
# it byte for byte. Uninstalls.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..\..')

foreach ($v in 'JAVA_HOME', 'GAS_PG_BIN', 'GAS_PG_PORT', 'GAS_TEST_DB_USER', 'GAS_TEST_DB_PASSWORD') {
    if (-not [Environment]::GetEnvironmentVariable($v)) { throw "Missing environment variable $v" }
}
$Port = 18082
$AppDir = Join-Path $env:RUNNER_TEMP 'Genealogy & Family App'
$DataDir = Join-Path $env:RUNNER_TEMP 'Genealogy & Family Data'
$Java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$WinSW = Join-Path $env:RUNNER_TEMP 'WinSW-x64.exe'

function Fail([string]$Message) {
    Write-Output "::error::Windows service test: $Message"
    Get-ChildItem "$DataDir\logs" -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Output "----- $($_.FullName)"; Get-Content $_.FullName -Tail 40
    }
    exit 1
}

Invoke-WebRequest -Uri 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe' -OutFile $WinSW -UseBasicParsing

$env:PGPASSWORD = $env:GAS_TEST_DB_PASSWORD
& "$env:GAS_PG_BIN\psql.exe" -h localhost -p $env:GAS_PG_PORT -U $env:GAS_TEST_DB_USER -d postgres -v ON_ERROR_STOP=1 `
    -c "CREATE DATABASE gas_service ENCODING 'UTF8' TEMPLATE template0"
if ($LASTEXITCODE -ne 0) { Fail "could not create service test database" }

& .\deploy\windows\install-services.ps1 -JarPath target\genealogy-archive.jar -WinSWPath $WinSW `
    -AppDir $AppDir -DataDir $DataDir -JavaExe $Java

$Volume = Join-Path $env:RUNNER_TEMP 'GenealogyArchiveVolumes\primary'
New-Item -ItemType Directory -Force -Path $Volume | Out-Null
Set-Content -Path "$DataDir\secrets\db-password" -Value $env:GAS_TEST_DB_PASSWORD -NoNewline -Encoding utf8
@"
database:
  url: 'jdbc:postgresql://localhost:$($env:GAS_PG_PORT)/gas_service'
  username: '$($env:GAS_TEST_DB_USER)'
  passwordFile: '$DataDir\secrets\db-password'
server:
  bindAddress: 127.0.0.1
  port: $Port
logging:
  format: json
  directory: '$DataDir\logs'
storageRoots:
  '0192f0c4-1111-7000-8000-000000000001': '$Volume'
"@ | Set-Content -Path "$DataDir\installation.yml" -Encoding utf8

& $Java -jar "$AppDir\genealogy-archive.jar" check-config "--config=$DataDir\installation.yml"
if ($LASTEXITCODE -ne 0) { Fail "check-config" }
& $Java -jar "$AppDir\genealogy-archive.jar" migrate "--config=$DataDir\installation.yml"
if ($LASTEXITCODE -ne 0) { Fail "migrate" }
# Phase 1: the archive is created explicitly (never as a migration side effect); the worker and
# readiness require it. The administrator password is passed through the environment, never argv.
& $Java -jar "$AppDir\genealogy-archive.jar" init-archive "--name=Service Test Archive" "--config=$DataDir\installation.yml"
if ($LASTEXITCODE -ne 0) { Fail "init-archive" }
$SvcAdminPw = 'svc-' + (-join ((1..24) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) }))
$env:GAS_NEW_ADMIN_PASSWORD = $SvcAdminPw
& $Java -jar "$AppDir\genealogy-archive.jar" create-admin "--username=service-admin" "--config=$DataDir\installation.yml"
$createAdminExit = $LASTEXITCODE
Remove-Item Env:GAS_NEW_ADMIN_PASSWORD
if ($createAdminExit -ne 0) { Fail "create-admin" }

# Phase 2 (ADR-0010): virtual service accounts; the volume is granted by SID.
foreach ($id in 'GenealogyArchiveWeb', 'GenealogyArchiveWorker') {
    $startName = (Get-CimInstance Win32_Service -Filter "Name='$id'").StartName
    if ($startName -ne "NT SERVICE\$id") { Fail "$id runs as '$startName', expected NT SERVICE\$id" }
}
Write-Output "ok: each service runs as its own virtual service account"
$registered = & $Java -jar "$AppDir\genealogy-archive.jar" register-volume '--name=Primary' '--purpose=primary' "--config=$DataDir\installation.yml"
if ($LASTEXITCODE -ne 0) { Fail "register-volume" }
$VolumeId = ($registered | Select-String -Pattern 'Volume ID\s*:\s*([0-9a-f-]{36})').Matches[0].Groups[1].Value
(Get-Content "$DataDir\installation.yml" -Raw).Replace('0192f0c4-1111-7000-8000-000000000001', $VolumeId) + @"
files:
  stagingDirectory: '$DataDir\staging'
  cacheDirectory: '$DataDir\cache'
"@ | Set-Content -Path "$DataDir\installation.yml" -Encoding utf8
& .\deploy\windows\grant-volume-access.ps1 -VolumeRoot $Volume
& $Java -jar "$AppDir\genealogy-archive.jar" init-volume "--volume-id=$VolumeId" "--config=$DataDir\installation.yml"
if ($LASTEXITCODE -ne 0) { Fail "init-volume" }
& $Java -jar "$AppDir\genealogy-archive.jar" set-ingestion-volume "--volume-id=$VolumeId" "--config=$DataDir\installation.yml"
if ($LASTEXITCODE -ne 0) { Fail "set-ingestion-volume" }

Start-Service GenealogyArchiveWeb
$ready = $false
for ($i = 0; $i -lt 120; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/actuator/health/readiness" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch { }
    if ((Get-Service GenealogyArchiveWeb).Status -eq 'Stopped') { Fail "web service stopped during startup" }
    Start-Sleep -Seconds 1
}
if (-not $ready) { Fail "web service not ready" }
Stop-Service GenealogyArchiveWeb
if ((Get-Service GenealogyArchiveWeb).Status -ne 'Stopped') { Fail "web service did not stop" }
Start-Sleep -Seconds 2
if (-not (Select-String -Path "$DataDir\logs\genealogy-archive-web.log" -Pattern 'graceful shutdown' -Quiet)) {
    Fail "web service stop was not graceful (no graceful shutdown logged)"
}
Write-Output "ok: Windows web service started, ready, stopped gracefully"

Start-Service GenealogyArchiveWorker
Start-Sleep -Seconds 10
if ((Get-Service GenealogyArchiveWorker).Status -ne 'Running') { Fail "worker service not running" }
Stop-Service GenealogyArchiveWorker
Start-Sleep -Seconds 2
if (-not (Select-String -Path "$DataDir\logs\genealogy-archive-worker.log" -Pattern 'Worker stopped' -Quiet)) {
    Fail "worker stop was not graceful"
}
if (-not (Select-String -Path "$DataDir\logs\genealogy-archive-worker.log" -Pattern 'started for archive' -Quiet)) {
    Fail "worker did not start for the archive"
}
if (Select-String -Path "$DataDir\logs\*.log" -SimpleMatch -Pattern $SvcAdminPw -Quiet) { Fail "administrator password found in service logs" }
Write-Output "ok: Windows worker service started and stopped gracefully"

# ---- Phase 2 under the real services -------------------------------------------------------------
Start-Service GenealogyArchiveWorker, GenealogyArchiveWeb
$ready = $false
for ($i = 0; $i -lt 120; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/actuator/health/readiness" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
}
if (-not $ready) { Fail "web service not ready for Phase 2 checks" }
$base = "http://127.0.0.1:$Port"
$login = Invoke-WebRequest -Uri "$base/login" -UseBasicParsing -SessionVariable browser
$t = [regex]::Match($login.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
$signIn = Invoke-WebRequest -Uri "$base/login" -Method Post -WebSession $browser -UseBasicParsing `
    -Body @{ _csrf = $t; username = 'service-admin'; password = $SvcAdminPw } -MaximumRedirection 0 -SkipHttpErrorCheck -ErrorAction SilentlyContinue
if (-not $signIn.Headers.Location -or [string]$signIn.Headers.Location -match 'error') { Fail "sign-in failed" }
$ops = (Invoke-WebRequest -Uri "$base/operations" -WebSession $browser -UseBasicParsing).Content
if ($ops -notmatch 'Web service write access to archival originals: denied') {
    [regex]::Matches($ops, '<li>[^<]*</li>') | Select-Object -First 5 | ForEach-Object { Write-Output $_.Value }
    Fail "web service could write (or could not confirm) archival originals"
}
Write-Output "ok: web service account is denied write access to the archival volume (requirement 5)"
$page = Invoke-WebRequest -Uri "$base/files/upload" -WebSession $browser -UseBasicParsing
if ($page.Content -notmatch 'Uploads are stored on volume') {
    $why = [regex]::Match($page.Content, '<section class="status problem">\s*<p>([^<]*)</p>').Groups[1].Value
    & icacls "$DataDir\staging"
    Fail "uploads are not ready under the service accounts: $why"
}
$t = [regex]::Match($page.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
$b = Invoke-WebRequest -Uri "$base/files/batches" -Method Post -WebSession $browser -UseBasicParsing -Body @{ _csrf = $t } `
    -MaximumRedirection 0 -SkipHttpErrorCheck -ErrorAction SilentlyContinue
$batch = [regex]::Match([string]$b.Headers.Location, '/files/batches/([0-9a-f-]{36})').Groups[1].Value
if (-not $batch) { Fail "no upload batch (HTTP $($b.StatusCode), redirect: $($b.Headers.Location))" }
$scan = Join-Path $env:RUNNER_TEMP 'gas-svc-scan.bin'
$bytes = New-Object byte[] 2097155
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[IO.File]::WriteAllBytes($scan, $bytes)
$put = Invoke-WebRequest -Uri "$base/files/batches/$batch/items/svc-1" -Method Put -WebSession $browser -UseBasicParsing `
    -InFile $scan -ContentType 'application/octet-stream' -SkipHttpErrorCheck -Headers @{ 'X-CSRF-TOKEN' = $t; 'X-File-Name' = 'service%20scan.tif' }
$op = [regex]::Match($put.Content, '"operationId":"([0-9a-f-]{36})"').Groups[1].Value
if (-not $op) { Fail "upload not accepted: HTTP $($put.StatusCode) $($put.Content)" }
$fileId = $null
$st = ''
for ($i = 0; $i -lt 60; $i++) {
    $st = (Invoke-WebRequest -Uri "$base/files/operations/$op/status" -WebSession $browser -UseBasicParsing).Content
    if ($st -match '"archived":true') { $fileId = [regex]::Match($st, '"fileId":"([0-9a-f-]{36})"').Groups[1].Value; break }
    Start-Sleep -Seconds 1
}
if (-not $fileId) { Fail "upload not archived by the worker service (last status: $st)" }
$down = Join-Path $env:RUNNER_TEMP 'gas-svc-down.bin'
Invoke-WebRequest -Uri "$base/files/$fileId/download" -WebSession $browser -UseBasicParsing -OutFile $down
if ((Get-FileHash $scan).Hash -ne (Get-FileHash $down).Hash) { Fail "downloaded original differs" }
$stored = Get-ChildItem "$Volume\originals" -Recurse -File | Select-Object -First 1
if (-not $stored.IsReadOnly) { Fail "stored original is not read-only" }
Write-Output "ok: browser upload archived by the worker service, protected and downloaded byte for byte"
Stop-Service GenealogyArchiveWeb, GenealogyArchiveWorker

& .\deploy\windows\uninstall-services.ps1 -AppDir $AppDir
Write-Output "WINDOWS SERVICE TEST PASSED"
exit 0
