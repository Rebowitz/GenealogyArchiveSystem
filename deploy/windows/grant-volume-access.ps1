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
# The DENY entry is written through the .NET ACL API with explicit rights. It must not contain
# SYNCHRONIZE or READ_CONTROL: Windows needs SYNCHRONIZE to open any file, even for reading, and an
# icacls generic "W" deny includes it, which would also block the web service's reads.
$webSid = New-Object System.Security.Principal.SecurityIdentifier($web)
$denied = [System.Security.AccessControl.FileSystemRights]'WriteData, AppendData, WriteExtendedAttributes, WriteAttributes, Delete, DeleteSubdirectoriesAndFiles, ChangePermissions, TakeOwnership'
$rule = New-Object System.Security.AccessControl.FileSystemAccessRule($webSid, $denied,
    [System.Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit',
    [System.Security.AccessControl.PropagationFlags]::None, [System.Security.AccessControl.AccessControlType]::Deny)
# Get-Acl/Set-Acl work in both Windows PowerShell 5.1 and PowerShell 7.
$acl = Get-Acl -LiteralPath $VolumeRoot
$acl.AddAccessRule($rule)
Set-Acl -LiteralPath $VolumeRoot -AclObject $acl

$check = (Get-Acl -LiteralPath $VolumeRoot).GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier]) |
    Where-Object { $_.IdentityReference.Value -eq $web -and $_.AccessControlType -eq 'Deny' -and -not $_.IsInherited }
if (-not $check) { throw "The DENY entry for the web service was not recorded on $VolumeRoot" }
foreach ($r in $check) {
    if ($r.FileSystemRights -band ([System.Security.AccessControl.FileSystemRights]::Synchronize -bor [System.Security.AccessControl.FileSystemRights]::ReadPermissions)) {
        throw "The DENY entry for the web service would also block reading ($($r.FileSystemRights))"
    }
}

# Advisory only: broad write access for ordinary users on a volume root is poor practice.
$broad = (Get-Acl -LiteralPath $VolumeRoot).GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier]) |
    Where-Object { $_.AccessControlType -eq 'Allow' -and $_.IdentityReference.Value -in @('S-1-1-0', 'S-1-5-32-545', 'S-1-5-11') -and
        ($_.FileSystemRights -band [System.Security.AccessControl.FileSystemRights]'WriteData, AppendData, Delete, DeleteSubdirectoriesAndFiles') }
if ($broad) {
    Write-Warning "Everyone, Users or Authenticated Users may write in $VolumeRoot (inherited from its parent). Consider removing inheritance so only administrators and the worker service can change preservation originals."
}
Write-Output "Granted $VolumeRoot : worker modify; web read-only (write rights denied)."
