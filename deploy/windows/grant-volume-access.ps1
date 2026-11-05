<#
.SYNOPSIS
  Grants an archival Storage Volume root to the Genealogy Archive services (Phase 2, ADR-0010).

.DESCRIPTION
  Run from an elevated PowerShell after install-services.ps1. For the given volume root:
    worker  (NT SERVICE\GenealogyArchiveWorker): Modify, inherited by everything below
    web     (NT SERVICE\GenealogyArchiveWeb):    Read & execute, plus an explicit DENY of every
            write right (write data, append, write attributes/extended attributes, delete, delete
            child, change permissions, take ownership). A DENY entry overrides any ALLOW the web
            service might receive through group membership, so the web service can never change
            preservation originals (authorization requirement 5).
  Existing entries are not removed. Re-running is safe.

  For a workgroup NAS that cannot authorize a virtual account, use the documented dedicated
  low-privilege local account instead (README, "Windows service accounts"); that variant is
  documented only and not covered by CI (owner decision B4).

.EXAMPLE
  .\deploy\windows\grant-volume-access.ps1 -VolumeRoot 'D:\Genealogy Archive\Primary'
#>
param(
    [Parameter(Mandatory = $true)][string]$VolumeRoot
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'service-config.ps1')

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Run this script from an elevated (Administrator) PowerShell."
}
if (-not (Test-Path -LiteralPath $VolumeRoot -PathType Container)) { throw "Not a directory: $VolumeRoot" }
$item = Get-Item -LiteralPath $VolumeRoot -Force
if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing a volume root that is a link or junction: $VolumeRoot" }

$web = Get-ServiceSid -ServiceId 'GenealogyArchiveWeb'
$worker = Get-ServiceSid -ServiceId 'GenealogyArchiveWorker'
Invoke-Icacls $VolumeRoot /grant "*${worker}:(OI)(CI)M"
Invoke-Icacls $VolumeRoot /grant "*${web}:(OI)(CI)RX"
Invoke-Icacls $VolumeRoot /deny "*${web}:(OI)(CI)(WD,AD,WEA,WA,D,DC,WDAC,WO)"
Write-Output "Granted $VolumeRoot : worker modify; web read-only (write rights denied)."
