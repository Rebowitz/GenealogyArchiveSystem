# CI only: installs the real Windows services with deploy\windows\install-services.ps1 (WinSW v2),
# runs the web application and worker as Windows services, checks readiness, stops them through
# the Service Control Manager and verifies a graceful stop from the application logs. Uninstalls.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..\..')

foreach ($v in 'JAVA_HOME', 'GAS_PG_BIN', 'GAS_PG_PORT', 'GAS_TEST_DB_USER', 'GAS_TEST_DB_PASSWORD') {
    if (-not [Environment]::GetEnvironmentVariable($v)) { throw "Missing environment variable $v" }
}
$Port = 18082
$AppDir = Join-Path $env:RUNNER_TEMP 'GenealogyArchiveApp'
$DataDir = Join-Path $env:RUNNER_TEMP 'GenealogyArchiveData'
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
Write-Output "ok: Windows worker service started and stopped gracefully"

& .\deploy\windows\uninstall-services.ps1 -AppDir $AppDir
Write-Output "WINDOWS SERVICE TEST PASSED"
exit 0
