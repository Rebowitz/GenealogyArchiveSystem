<#
.SYNOPSIS
  Installs or updates the Genealogy Archive System as Windows services (web + worker) using WinSW v2.

.DESCRIPTION
  Run from an elevated PowerShell. WinSW is a small open-source service wrapper; download
  WinSW-x64.exe (v2.12.0) from https://github.com/winsw/winsw/releases once and pass its path.
  No Internet access is needed at runtime.

  Creates:
    -AppDir   (default C:\Program Files\GenealogyArchive)  application jar and service wrappers
    -DataDir  (default C:\ProgramData\GenealogyArchive)    installation.yml, logs, secrets\
  The secrets folder is restricted to SYSTEM and Administrators. Never overwrites installation.yml.
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

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Run this script from an elevated (Administrator) PowerShell."
}
if (-not $JavaExe) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $JavaExe = "$env:JAVA_HOME\bin\java.exe" }
    else { $JavaExe = (Get-Command java.exe -ErrorAction Stop).Source }
}
foreach ($p in $JarPath, $WinSWPath, $JavaExe) { if (-not (Test-Path $p)) { throw "Not found: $p" } }

New-Item -ItemType Directory -Force -Path $AppDir, $DataDir, "$DataDir\logs", "$DataDir\logs\service", "$DataDir\secrets" | Out-Null
Copy-Item -Force $JarPath "$AppDir\genealogy-archive.jar"

# Secrets folder: SYSTEM and Administrators only (services run as LocalSystem by default).
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
    # Literal (non-regex) substitution: paths contain backslashes.
    $content = Get-Content (Join-Path $PSScriptRoot "$name.xml") -Raw
    $content = $content.Replace('@JAVA@', $JavaExe).Replace('@APP_DIR@', $AppDir).Replace('@DATA_DIR@', $DataDir)
    Set-Content -Path $xml -Value $content -Encoding UTF8
    & $exe install
    if ($LASTEXITCODE -ne 0) { throw "WinSW install failed for $name ($LASTEXITCODE)" }
}

Write-Output @"

Installed. Next steps:
  1. Edit $DataDir\installation.yml (database, storageRoots, logging.directory: $DataDir\logs).
  2. Put the database password (one line) in $DataDir\secrets\db-password
     and set database.passwordFile to that path.
  3. Validate and migrate:
       & '$JavaExe' -jar '$AppDir\genealogy-archive.jar' check-config --config='$DataDir\installation.yml'
       & '$JavaExe' -jar '$AppDir\genealogy-archive.jar' migrate --config='$DataDir\installation.yml'
  4. Start:
       Start-Service GenealogyArchiveWeb, GenealogyArchiveWorker
"@
