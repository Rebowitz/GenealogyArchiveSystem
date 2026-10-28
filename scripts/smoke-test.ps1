# Packaged-application smoke test (Windows): drives the built jar exactly as an administrator would -
# check-config, status, migrate (twice), serve with readiness polling, worker start/stop - using a
# Storage Volume root given as a native Windows path. Requires:
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
Expect-Exit 0 @('status')

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
if ($webLog -notmatch '"log.level"') { Fail "structured JSON web log not written" }
if (Select-String -Path "$Work\logs\*.log" -SimpleMatch -Pattern $env:GAS_TEST_DB_PASSWORD -Quiet) { Fail "database password found in logs" }
Write-Output "ok: structured logs written, no secrets in logs"
Write-Output "SMOKE TEST PASSED"
exit 0
