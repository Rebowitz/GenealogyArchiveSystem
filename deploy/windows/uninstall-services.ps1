<#
.SYNOPSIS
  Stops and removes the Genealogy Archive System Windows services. Run elevated.
  Leaves configuration, logs, the database and all Storage Volumes untouched.
#>
param([string]$AppDir = "$env:ProgramFiles\GenealogyArchive")
$ErrorActionPreference = 'Stop'
foreach ($name in 'GenealogyArchive-Web', 'GenealogyArchive-Worker') {
    $exe = Join-Path $AppDir "$name.exe"
    if (Test-Path $exe) {
        & $exe stop | Out-Null
        & $exe uninstall
    }
}
Write-Output "Services removed. Configuration, logs, database and Storage Volumes were not touched."
