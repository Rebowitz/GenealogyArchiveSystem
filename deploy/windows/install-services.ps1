<#
.SYNOPSIS
  Installs or updates the Genealogy Archive System as Windows services (web + worker) using WinSW v2.

.DESCRIPTION
  Run from an elevated PowerShell. WinSW is a small open-source service wrapper; download
  WinSW-x64.exe (v2.12.0) from https://github.com/winsw/winsw/releases once and pass its path.
  No Internet access is needed at runtime.

  Creates:
    -AppDir   (default C:\Program Files\GenealogyArchive)  application jar and service wrappers
    -DataDir  (default C:\ProgramData\GenealogyArchive)    installation.yml, logs, secrets\,
                                                           staging\, cache\, tmp\web, tmp\worker
  Each service runs under its own virtual service account (NT SERVICE\GenealogyArchiveWeb and
  NT SERVICE\GenealogyArchiveWorker; ADR-0010). Permissions are granted to those accounts by SID:
    secrets\  SYSTEM, Administrators: full; both services: read           (inheritance removed)
    staging\  SYSTEM, Administrators: full; both services: modify         (inheritance removed)
    cache\    worker: modify; web: read
    logs\     both services: modify;  tmp\<service>: that service only
  Archival Storage Volume roots are granted separately with grant-volume-access.ps1 (worker: modify;
  web: read with an explicit DENY of every write right). Never overwrites installation.yml.
  Services are installed but not started: configure, migrate, then start.

.EXAMPLE
  .\deploy\windows\install-services.ps1 -JarPath .\target\genealogy-archive.jar -WinSWPath C:\Downloads\WinSW-x64.exe
#>
param(
    [Parameter(Mandatory = $true)][string]$JarPath,
    [Parameter(Mandatory = $true)][string]$WinSWPath,
    [string]$AppDir = "$env:ProgramFiles\GenealogyArchive",
    [string]$DataDir = "$env:ProgramData\GenealogyArchive",
    [string]$JavaExe = ""
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'service-config.ps1')

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Run this script from an elevated (Administrator) PowerShell."
}
if (-not $JavaExe) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $JavaExe = "$env:JAVA_HOME\bin\java.exe" }
    else { $JavaExe = (Get-Command java.exe -ErrorAction Stop).Source }
}
foreach ($p in $JarPath, $WinSWPath, $JavaExe) { if (-not (Test-Path $p)) { throw "Not found: $p" } }
Assert-WinSWIntegrity -Path $WinSWPath

New-Item -ItemType Directory -Force -Path $AppDir, $DataDir, "$DataDir\logs", "$DataDir\logs\service", "$DataDir\secrets",
    "$DataDir\staging", "$DataDir\cache", "$DataDir\tmp\web", "$DataDir\tmp\worker" | Out-Null
Copy-Item -Force $JarPath "$AppDir\genealogy-archive.jar"

# Secrets folder: SYSTEM and Administrators only until the service accounts exist (below).
& icacls "$DataDir\secrets" /inheritance:r /grant:r "*S-1-5-18:(OI)(CI)F" "*S-1-5-32-544:(OI)(CI)F" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not restrict permissions on $DataDir\secrets" }

if (-not (Test-Path "$DataDir\installation.yml")) {
    Copy-Item (Join-Path $PSScriptRoot '..\..\config\installation.example.yml') "$DataDir\installation.yml"
    Write-Output "Created $DataDir\installation.yml from the example - edit it before starting services."
}

foreach ($name in 'GenealogyArchive-Web', 'GenealogyArchive-Worker') {
    $exe = Join-Path $AppDir "$name.exe"
    $xml = Join-Path $AppDir "$name.xml"
    if (Test-Path $exe) { & $exe stop 2>$null | Out-Null; & $exe uninstall 2>$null | Out-Null }
    Copy-Item -Force $WinSWPath $exe
    $template = Get-Content -LiteralPath (Join-Path $PSScriptRoot "$name.xml") -Raw
    $content = ConvertTo-WinSWXml -Template $template -JavaExe $JavaExe -AppDir $AppDir -DataDir $DataDir
    Set-Content -LiteralPath $xml -Value $content -Encoding UTF8
    & $exe install
    if ($LASTEXITCODE -ne 0) { throw "WinSW install failed for $name ($LASTEXITCODE)" }
    $serviceId = Get-ServiceId -Name $name
    Set-VirtualServiceAccount -ServiceId $serviceId
}

# Grant the installation-local directories to the virtual accounts, by SID.
$web = Get-ServiceSid -ServiceId 'GenealogyArchiveWeb'
$worker = Get-ServiceSid -ServiceId 'GenealogyArchiveWorker'
Invoke-Icacls $AppDir /grant "*${web}:(OI)(CI)RX" "*${worker}:(OI)(CI)RX"
Invoke-Icacls $DataDir /grant "*${web}:RX" "*${worker}:RX"
Invoke-Icacls "$DataDir\installation.yml" /grant "*${web}:R" "*${worker}:R"
Invoke-Icacls "$DataDir\logs" /grant "*${web}:(OI)(CI)M" "*${worker}:(OI)(CI)M"
Invoke-Icacls "$DataDir\secrets" /grant "*${web}:(OI)(CI)R" "*${worker}:(OI)(CI)R"
Invoke-Icacls "$DataDir\staging" /inheritance:r /grant:r "*S-1-5-18:(OI)(CI)F" "*S-1-5-32-544:(OI)(CI)F" `
    "*${web}:(OI)(CI)M" "*${worker}:(OI)(CI)M"
Invoke-Icacls "$DataDir\cache" /grant "*${worker}:(OI)(CI)M" "*${web}:(OI)(CI)RX"
Invoke-Icacls "$DataDir\tmp\web" /inheritance:r /grant:r "*S-1-5-18:(OI)(CI)F" "*S-1-5-32-544:(OI)(CI)F" "*${web}:(OI)(CI)M"
Invoke-Icacls "$DataDir\tmp\worker" /inheritance:r /grant:r "*S-1-5-18:(OI)(CI)F" "*S-1-5-32-544:(OI)(CI)F" "*${worker}:(OI)(CI)M"

Write-Output @"

Installed. Next steps:
  1. Edit $DataDir\installation.yml (database, storageRoots, logging.directory: $DataDir\logs,
     files.stagingDirectory: $DataDir\staging, files.cacheDirectory: $DataDir\cache).
  2. Put the database password (one line) in $DataDir\secrets\db-password
     and set database.passwordFile to that path.
  3. Validate and migrate:
       & '$JavaExe' -jar '$AppDir\genealogy-archive.jar' check-config --config='$DataDir\installation.yml'
       & '$JavaExe' -jar '$AppDir\genealogy-archive.jar' migrate --config='$DataDir\installation.yml'
  4. For each archival Storage Volume root (after register-volume):
       .\deploy\windows\grant-volume-access.ps1 -VolumeRoot 'D:\Archive\Primary'
     then init-volume and set-ingestion-volume.
  5. Start:
       Start-Service GenealogyArchiveWeb, GenealogyArchiveWorker
"@
